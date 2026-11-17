package com.juge.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.juge.app.ui.AppDialog
import com.juge.app.ui.AppDialogHeader
import com.juge.app.ui.AppDialogSurface
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppSecondaryButton
import com.juge.app.ui.AppText
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.overlayCard
import com.juge.app.ui.theme.selectBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object CropImageHelper {

    private const val MAX_IMAGE_DIMENSION = 2048
    // 按目标尺寸解码时留的余量：用户可能把图放大后再框选，刚好按目标尺寸解会糊
    private const val DECODE_HEADROOM = 2
    // 再小的目标也不低于这个边长，否则放大后锯齿明显
    private const val MIN_DECODE_DIMENSION = 480
    private const val DEFAULT_CROP_WIDTH = 840
    private const val DEFAULT_CROP_HEIGHT = 270
    private const val MAX_CROP_OUTPUT_DIMENSION = 4096

    // 编辑期产生的背景图先落在临时目录，只有用户真正保存并同步到桌面时才提交为正式资源，
    // 避免「裁完就取消/被付费墙拦下」留下永远没人删的孤儿文件。
    private const val TMP_DIR = "bg_tmp"
    private const val OFFICIAL_DIR = "bg_images"
    private const val PX_PER_DP = 3.36f
    private const val TMP_MAX_AGE_MS = 24 * 60 * 60 * 1000L

    /**
     * 根据组件实际尺寸（dp）计算裁剪输出分辨率。
     * 输出宽高比与组件真实宽高比一致，保证「裁剪框里看到什么，桌面上就显示什么」；
     * 像素密度约 3.36 px/dp，避免组件放大后发虚。
     */
    fun cropTargetForWidget(widthDp: Int, heightDp: Int): Pair<Int, Int> {
        val wDp = if (widthDp > 0) widthDp else 250
        val hDp = if (heightDp > 0) heightDp else 110
        val w = (wDp * PX_PER_DP).toInt().coerceIn(1, MAX_CROP_OUTPUT_DIMENSION)
        val h = (hDp * PX_PER_DP).toInt().coerceIn(1, MAX_CROP_OUTPUT_DIMENSION)
        return w to h
    }

    /**
     * 把编辑期产生的临时背景图提交为正式资源（bg_tmp → bg_images）。
     * 已是正式文件或文件不存在时原样返回，可重复调用。
     */
    fun commitBackground(context: Context, path: String?): String? {
        if (path.isNullOrBlank()) return path
        val src = File(path)
        if (!src.exists()) return path
        val officialDir = File(context.filesDir, OFFICIAL_DIR)
        val tmpDir = File(context.filesDir, TMP_DIR)
        if (src.parentFile?.absolutePath != tmpDir.absolutePath) return path
        if (!officialDir.exists()) officialDir.mkdirs()
        val dst = File(officialDir, src.name)
        return try {
            if (src.renameTo(dst)) dst.absolutePath else path
        } catch (e: Exception) {
            Timber.w(e, "commit background failed")
            path
        }
    }

    /** 清理超过 [maxAgeMs] 仍未提交的临时背景图（编辑中途退出、被付费墙拦下等）。 */
    fun cleanupTempBackgrounds(context: Context, maxAgeMs: Long = TMP_MAX_AGE_MS) {
        try {
            val dir = File(context.filesDir, TMP_DIR)
            if (!dir.exists()) return
            val cutoff = System.currentTimeMillis() - maxAgeMs
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cutoff) f.delete()
            }
        } catch (e: Exception) {
            Timber.w(e, "cleanup temp backgrounds failed")
        }
    }

    /**
     * 背景[文件][path]是否仍被其他配置/组件引用。删除一张「旧背景」前必须先问它：
     * 两条配置行、甚至组件级样式缓存都可以指向同一张图（同一背景先后应用到多个组件），
     * 无引用检查的删除会让其中一个组件的背景整块消失（渲染端对缺失文件只会画空底）。
     *
     * @param excludingConfigId 排除自己对应的配置行
     */
    fun isBackgroundFileStillReferenced(context: Context, path: String, excludingConfigId: Long?): Boolean {
        if (path.isBlank()) return false
        if (path.contains("/$TMP_DIR/")) {
            // 临时文件不存在跨行共用，也从未提交为正式资源，不参与引用判断
            return false
        }
        try {
            val dbHelper = com.juge.app.data.DbHelper.getInstance(context)
            val referencedByConfig = dbHelper.getAllWidgetConfigs().any { config ->
                config.id != excludingConfigId &&
                    com.juge.app.data.WidgetStyle.fromJsonString(config.styleJson).backgroundImagePath == path
            }
            if (referencedByConfig) return true
            val prefs = context.getSharedPreferences(
                com.juge.app.ReminderWidgetProvider.PREFS_WIDGET_BINDINGS,
                Context.MODE_PRIVATE
            )
            return prefs.all.any { (key, value) ->
                key.startsWith("widget_style_") && value is String &&
                    org.json.JSONObject(value).optString("backgroundImagePath") == path
            }
        } catch (e: Exception) {
            // 检查失败按「仍被引用」处理：宁可留下一个无主图片文件，也不回头删用户的内容
            Timber.w(e, "isBackgroundFileStillReferenced failed")
            return true
        }
    }

    /**
     * 从 Uri 加载图片，支持防 OOM 的 downsample 与 EXIF 方向矫正。
     *
     * [targetWidth] / [targetHeight] 是**最终产物**的尺寸（由 [cropTargetForWidget] 算出，
     * 通常只有 840×270 这个量级）。传了它们之后，解码上限会跟着目标走，而不是一律
     * 解到 [MAX_IMAGE_DIMENSION]：
     *
     * 一张 4000×3000 的照片，早先固定按 2048 解 = 2048×1536 ≈ **12MB**；
     * 而 EXIF 旋转还要再分配一张同尺寸位图，峰值 **24MB**——这一切只为了产出
     * 一张 840×270（0.9MB）的图，中间多花了 26 倍内存。低端机上这一下就是整机卡顿
     * 甚至直接被系统回收。
     *
     * 现在留 2 倍余量（用户可能把图放大后再框选，完全按目标尺寸解会糊），
     * 同一张照片解到 1000×750 ≈ 3MB，峰值从 24MB 降到 6MB，产物逐像素不变。
     * 不传目标尺寸时行为与从前完全一致（上限 [MAX_IMAGE_DIMENSION]），
     * 供「只想看一眼原图」的调用方使用。
     */
    fun loadBitmapFromUri(
        context: Context,
        uri: Uri,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            val boundsInput = context.contentResolver.openInputStream(uri) ?: return null
            boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val maxDimension = decodeLimitFor(targetWidth, targetHeight)
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            applyExifRotation(context, uri, decoded)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bitmap from URI")
            null
        }
    }

    /**
     * 解码上限：给了目标尺寸就按「目标长边的 2 倍」解，并夹在
     * [MIN_DECODE_DIMENSION] 与 [MAX_IMAGE_DIMENSION] 之间。没给就沿用旧的固定上限。
     */
    private fun decodeLimitFor(targetWidth: Int, targetHeight: Int): Int {
        val longestTarget = maxOf(targetWidth, targetHeight)
        if (longestTarget <= 0) return MAX_IMAGE_DIMENSION
        return (longestTarget * DECODE_HEADROOM).coerceIn(MIN_DECODE_DIMENSION, MAX_IMAGE_DIMENSION)
    }

    // 相册图片可能携带 EXIF 旋转/翻转信息，直接解码会得到横躺/镜像的图。
    // 8 种 orientation 全部处理：只认 90/180/270 的话，FLIP/TRANSPOSE 类照片
    // （自拍、部分相机与截图）会以左右镜像的姿态落盘，与相册正立画面相反。
    //
    // 关于「为什么仍分配第二张位图」：审查报告建议把 EXIF 变换合进
    // cropAndSaveBitmap 的最后一次 drawBitmap。那需要把 ImageCropDialog 预览端
    // （用户手势作用于**旋转后**的坐标系）的裁剪矩阵与 EXIF 矩阵复合，
    // 并同步改写 cropMatrix 的宽高与缩放枢轴——等于改动裁剪构图语义，
    // 必须真机对 8 种 orientation 逐张比对才能放行，不宜在收尾阶段顺手做。
    // 现在的上游改动已经把源图按目标尺寸解码（decodeLimitFor），
    // 旋转位图随之从 12MB 级降到 3MB 级，且变换成功后立刻 recycle 源图，
    // 双份驻留只发生在一瞬间；异常路径也会保留原图继续走裁剪。
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val transform: android.graphics.Matrix? = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = androidx.exifinterface.media.ExifInterface(input)
                buildExifMatrix(
                    exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read EXIF orientation")
            null
        }
        if (transform == null || transform.isIdentity) return bitmap
        return try {
            val transformed = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, transform, true
            )
            if (transformed != bitmap) bitmap.recycle()
            transformed
        } catch (e: Exception) {
            Timber.w(e, "Failed to apply EXIF rotation")
            bitmap
        }
    }

    /** EXIF orientation → 位图变换矩阵；None/Normal 返回 identity */
    private fun buildExifMatrix(orientation: Int): android.graphics.Matrix {
        val matrix = android.graphics.Matrix()
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                matrix.postScale(-1f, 1f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 ->
                matrix.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                // FlipV = Rotate180 + FlipH
                matrix.postScale(-1f, 1f)
                matrix.postRotate(180f)
            }
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                // 转置 = 先镜像后旋转 90°；顺序决定镜像轴，勿调换
                matrix.postScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 ->
                matrix.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                // 转置的另一方向：镜像 + 旋转 270°（即逆时针 90°）
                matrix.postScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 ->
                matrix.postRotate(270f)
            else -> Unit // 0/1/未识别：identity
        }
        return matrix
    }

    // 后台按矩阵裁剪并保存图片
    fun cropAndSaveBitmap(
        context: Context,
        bitmap: Bitmap,
        viewWidthPx: Float,
        viewHeightPx: Float,
        scale: Float,
        offset: Offset,
        targetWidth: Int = DEFAULT_CROP_WIDTH,
        targetHeight: Int = DEFAULT_CROP_HEIGHT
    ): String? {
        // 异常路径也要能回收：见 catch 里的说明
        var pendingOutput: Bitmap? = null
        return try {
            val matrix = cropMatrix(
                bitmapWidth = bitmap.width.toFloat(),
                bitmapHeight = bitmap.height.toFloat(),
                viewWidthPx = viewWidthPx,
                viewHeightPx = viewHeightPx,
                scale = scale,
                offset = offset,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
            )

            val croppedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            pendingOutput = croppedBitmap
            val canvas = android.graphics.Canvas(croppedBitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, matrix, paint)

            // 先落到临时目录，用户真正保存并同步到桌面时才提交为正式资源
            val dir = File(context.filesDir, TMP_DIR)
            if (!dir.exists()) dir.mkdirs()
            // 带透明通道的图片必须存 PNG，JPEG 会把透明区域压成黑色
            val hasAlpha = bitmap.hasAlpha()
            val ext = if (hasAlpha) "png" else "jpg"
            val file = File(dir, "bg_${System.currentTimeMillis()}.$ext")
            FileOutputStream(file).use { fos ->
                val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                croppedBitmap.compress(format, 90, fos)
                fos.flush()
            }
            croppedBitmap.recycle()
            pendingOutput = null

            file.absolutePath
        } catch (e: Exception) {
            // 压缩/写盘失败时那张输出位图同样要还回去：正常路径在上面回收了，
            // 异常路径以前直接跳过——0.9MB 的量不大，但它和上面的解码峰值发生在
            // 同一个用户动作里（选图 → 裁剪 → 保存），叠加起来正好撞上堆上限。
            // croppedBitmap 声明在 try 内，这里用 nullable 引用统一收口。
            runCatching { pendingOutput?.recycle() }
            Timber.e(e, "Failed to crop and save bitmap")
            null
        }
    }

    /**
     * 裁剪保存矩阵：把源图坐标映射到输出位图坐标。
     *
     * ⚠️ 与 [ImageCropDialog] 里 Canvas 预览的绘制顺序必须严格一致——
     * 用户在预览框里看到的构图，就是这张矩阵输出的构图。历史上保存端用 `post*`
     * 链接矩阵，点位顺序与预览相反（pan 被 [初始缩放×R] 再乘一遍、缩放枢轴也换了
     * 坐标系），结果是预览取到的区域与保存产物对不上。
     *
     * 点位顺序（源图上任意一点 p 的旅程，与预览逐层对应）：
     * S(I) → +init → −c → ×zoom → +c → +pan → ×R
     * 其中 I=[初始铺满缩放], init=[初始居中偏移], c=[视窗中心], zoom/pan=[手势值],
     * R=[视窗坐标 → 输出物理像素] 的比例。
     * `pre*` 系列按书写顺序作用于点（与 Compose Canvas 的绘制顺序同序），照抄即可。
     */
    internal fun cropMatrix(
        bitmapWidth: Float,
        bitmapHeight: Float,
        viewWidthPx: Float,
        viewHeightPx: Float,
        scale: Float,
        offset: Offset,
        targetWidth: Int,
        targetHeight: Int,
    ): android.graphics.Matrix {
        val viewAspectRatio = viewWidthPx / viewHeightPx
        val imageAspectRatio = bitmapWidth / bitmapHeight
        val initScale = if (imageAspectRatio > viewAspectRatio) {
            viewHeightPx / bitmapHeight
        } else {
            viewWidthPx / bitmapWidth
        }
        val initX = (viewWidthPx - bitmapWidth * initScale) / 2f
        val initY = (viewHeightPx - bitmapHeight * initScale) / 2f
        val cx = viewWidthPx / 2f
        val cy = viewHeightPx / 2f
        val rX = targetWidth.toFloat() / viewWidthPx
        val rY = targetHeight.toFloat() / viewHeightPx

        return android.graphics.Matrix().apply {
            preScale(initScale, initScale)   // 1. 初始铺满
            preTranslate(initX, initY)       // 2. 初始居中
            preTranslate(-cx, -cy)
            preScale(scale, scale)           // 3. 手势缩放（绕视窗中心）
            preTranslate(cx, cy)
            preTranslate(offset.x, offset.y) // 4. 手势平移（屏幕像素，不被缩放）
            preScale(rX, rY)                 // 5. 视窗 → 输出物理尺寸
        }
    }

    /**
     * 把裁切预览的平移限制在图片覆盖取景框的范围内。
     *
     * 初始缩放就是 cover，且最小缩放为 1，所以按缩放后的图片尺寸与取景框尺寸
     * 反推每个方向最多可移动多少；不夹紧时用户可以把图片拖出画面，保存出一大片空白。
     */
    internal fun clampCropOffset(
        offset: Offset,
        bitmapWidth: Float,
        bitmapHeight: Float,
        viewWidthPx: Float,
        viewHeightPx: Float,
        scale: Float,
    ): Offset {
        if (bitmapWidth <= 0f || bitmapHeight <= 0f || viewWidthPx <= 0f || viewHeightPx <= 0f) {
            return Offset.Zero
        }
        val viewAspectRatio = viewWidthPx / viewHeightPx
        val imageAspectRatio = bitmapWidth / bitmapHeight
        val initScale = if (imageAspectRatio > viewAspectRatio) {
            viewHeightPx / bitmapHeight
        } else {
            viewWidthPx / bitmapWidth
        }
        val maxX = ((bitmapWidth * initScale * scale - viewWidthPx).coerceAtLeast(0f)) / 2f
        val maxY = ((bitmapHeight * initScale * scale - viewHeightPx).coerceAtLeast(0f)) / 2f
        return Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY),
        )
    }

    @Composable
    fun ImageCropDialog(
        show: Boolean,
        uri: Uri?,
        onDismiss: () -> Unit,
        onCropSuccess: (String) -> Unit,
        // 裁剪输出的物理分辨率（像素）。宽高比应等于组件真实宽高比，用 CropImageHelper.cropTargetForWidget 计算
        targetWidth: Int = DEFAULT_CROP_WIDTH,
        targetHeight: Int = DEFAULT_CROP_HEIGHT
    ) {
        // 调用方 dismiss 即置 null；退出动画还在播，留住最后一次非空的 uri 继续渲染
        var lastUri by remember { mutableStateOf(uri) }
        if (uri != null) lastUri = uri
        val boundUri = lastUri ?: return
        val context = LocalContext.current
        var bitmap by remember(boundUri) { mutableStateOf<Bitmap?>(null) }
        var isLoading by remember(uri) { mutableStateOf(true) }

        LaunchedEffect(boundUri) {
            isLoading = true
            // 大图解码（两次 openInputStream + downsample）放 IO 线程，避免主线程 ANR
            // 把目标尺寸传下去：解码上限跟着裁剪产物走，而不是固定 2048。
            // 见 loadBitmapFromUri 的注释——同一张照片峰值内存从 24MB 降到 6MB。
            bitmap = withContext(Dispatchers.IO) {
                loadBitmapFromUri(context, boundUri, targetWidth, targetHeight)
            }
            isLoading = false
            if (bitmap == null) {
                Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }

        if (bitmap != null) {
            var scale by remember(boundUri, targetWidth, targetHeight) { mutableFloatStateOf(1f) }
            var offset by remember(boundUri, targetWidth, targetHeight) { mutableStateOf(Offset.Zero) }
            // 保存期间置忙：压缩+写盘在 IO 线程执行，防止重复点击与主线程卡顿
            var isSaving by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            val density = LocalDensity.current
            // 裁剪视窗的宽高比与组件真实宽高比一致，用户框选的区域就是桌面上显示的区域
            val viewAspect = targetWidth.toFloat() / targetHeight.toFloat()

            AppDialog(
                visible = show,
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                AppDialogSurface {
                    // 与取色 / 分类管理等高内容子页面同一颗头部（标题 + 右侧 X）
                    AppDialogHeader(title = "选择图片背景区域", onClose = onDismiss)
                    AppText(
                        text = "单指拖动图片平移，双指捏合进行缩放",
                        role = AppTextRole.caption,
                    )

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        // 以弹窗实际可用宽度反推取景框大小，窄屏和 4×2 长条图都不会横向溢出。
                        val innerMaxWidth = (maxWidth - 32.dp).coerceAtLeast(1.dp)
                        val cropWidthDp = minOf(innerMaxWidth, 320.dp, 220.dp * viewAspect)
                        val cropHeightDp = cropWidthDp / viewAspect
                        val viewWidthPx = with(density) { cropWidthDp.toPx() }
                        val viewHeightPx = with(density) { cropHeightDp.toPx() }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // 取景区用深色底承托图片；标题、说明与操作按钮仍留在弹窗底板，
                            // 三个区域从上到下各占一行，不再互相叠压。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShape.button)
                                    .background(overlayCard)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                // 裁剪视窗框
                                Box(
                                    modifier = Modifier
                                        .size(width = cropWidthDp, height = cropHeightDp)
                                        .border(1.5.dp, Color.White)
                                        .clipToBounds(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val paint = remember { Paint() }
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("crop_preview")
                                            .semantics {
                                                contentDescription = "图片裁切预览"
                                            }
                                            .pointerInput(boundUri, viewWidthPx, viewHeightPx) {
                                                detectTransformGestures { _, pan, zoom, _ ->
                                                    val newScale = (scale * zoom).coerceIn(1f, 8f)
                                                    scale = newScale
                                                    offset = clampCropOffset(
                                                        offset = offset + pan,
                                                        bitmapWidth = bitmap!!.width.toFloat(),
                                                        bitmapHeight = bitmap!!.height.toFloat(),
                                                        viewWidthPx = viewWidthPx,
                                                        viewHeightPx = viewHeightPx,
                                                        scale = newScale,
                                                    )
                                                }
                                            }
                                    ) {
                                        val viewWidth = size.width
                                        val viewHeight = size.height

                                        val viewAspectRatio = viewWidth / viewHeight
                                        val imageAspectRatio = bitmap!!.width.toFloat() / bitmap!!.height
                                        val initScale = if (imageAspectRatio > viewAspectRatio) {
                                            viewHeight / bitmap!!.height
                                        } else {
                                            viewWidth / bitmap!!.width
                                        }
                                        val initX = (viewWidth - bitmap!!.width * initScale) / 2f
                                        val initY = (viewHeight - bitmap!!.height * initScale) / 2f

                                        drawIntoCanvas { canvas ->
                                            canvas.save()
                                            // 应用手势平移和缩放
                                            // ⚠️ 这里的操作数序定义了「用户看到的构图」，
                                            // 保存端 [cropMatrix] 与之逐层对应（pre* 链），
                                            // 两端任何一处改动都要同步另一端
                                            canvas.translate(offset.x, offset.y)
                                            canvas.translate(viewWidth / 2f, viewHeight / 2f)
                                            canvas.scale(scale, scale)
                                            canvas.translate(-viewWidth / 2f, -viewHeight / 2f)
                                            // 应用初始铺满和居中
                                            canvas.translate(initX, initY)
                                            canvas.scale(initScale, initScale)

                                            canvas.drawImage(bitmap!!.asImageBitmap(), Offset.Zero, paint)
                                            canvas.restore()
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppSecondaryButton(
                                    text = "取消",
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    enabled = !isSaving,
                                )
                                AppPrimaryButton(
                                    text = "保存并使用",
                                    onClick = {
                                        if (isSaving) return@AppPrimaryButton
                                        isSaving = true
                                        val currentBitmap = bitmap!!
                                        scope.launch {
                                            val savedPath = withContext(Dispatchers.IO) {
                                                cropAndSaveBitmap(
                                                    context = context,
                                                    bitmap = currentBitmap,
                                                    viewWidthPx = viewWidthPx,
                                                    viewHeightPx = viewHeightPx,
                                                    scale = scale,
                                                    offset = offset,
                                                    targetWidth = targetWidth,
                                                    targetHeight = targetHeight
                                                )
                                            }
                                            isSaving = false
                                            if (savedPath != null) {
                                                onCropSuccess(savedPath)
                                            } else {
                                                Toast.makeText(context, "保存裁剪失败", Toast.LENGTH_SHORT).show()
                                            }
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isSaving,
                                    loading = isSaving,
                                )
                            }
                        }
                    }
                }
            }
        } else if (isLoading) {
            AppDialog(
                visible = true,
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                AppDialogSurface {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = selectBlue)
                    }
                }
            }
        }
    }
}
