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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppType
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
            // 组件级样式缓存（widget_bindings_prefs 里的 widget_style_<id>）也持有引用；
            // 直接扫全部字符串值，key 名字变化也不怕
            val prefs = context.getSharedPreferences(
                com.juge.app.ReminderWidgetProvider.PREFS_WIDGET_BINDINGS,
                Context.MODE_PRIVATE
            )
            return prefs.all.values.any { it is String && it.contains(path) }
        } catch (e: Exception) {
            // 检查失败按「仍被引用」处理：宁可留下一个无主图片文件，也不回头删用户的内容
            Timber.w(e, "isBackgroundFileStillReferenced failed")
            return true
        }
    }

    // 从 Uri 加载图片，支持防 OOM 的 downsample 与 EXIF 方向矫正
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION || bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION) {
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

    // 相册图片可能携带 EXIF 旋转/翻转信息，直接解码会得到横躺/镜像的图。
    // 8 种 orientation 全部处理：只认 90/180/270 的话，FLIP/TRANSPOSE 类照片
    // （自拍、部分相机与截图）会以左右镜像的姿态落盘，与相册正立画面相反。
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

            file.absolutePath
        } catch (e: Exception) {
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

    @Composable
    fun ImageCropDialog(
        uri: Uri,
        onDismiss: () -> Unit,
        onCropSuccess: (String) -> Unit,
        // 裁剪输出的物理分辨率（像素）。宽高比应等于组件真实宽高比，用 CropImageHelper.cropTargetForWidget 计算
        targetWidth: Int = DEFAULT_CROP_WIDTH,
        targetHeight: Int = DEFAULT_CROP_HEIGHT
    ) {
        val context = LocalContext.current
        var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
        var isLoading by remember(uri) { mutableStateOf(true) }

        LaunchedEffect(uri) {
            isLoading = true
            // 大图解码（两次 openInputStream + downsample）放 IO 线程，避免主线程 ANR
            bitmap = withContext(Dispatchers.IO) { loadBitmapFromUri(context, uri) }
            isLoading = false
            if (bitmap == null) {
                Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }

        if (bitmap != null) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            // 保存期间置忙：压缩+写盘在 IO 线程执行，防止重复点击与主线程卡顿
            var isSaving by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            val density = LocalDensity.current
            // 裁剪视窗的宽高比与组件真实宽高比一致，用户框选的区域就是桌面上显示的区域
            val viewAspect = targetWidth.toFloat() / targetHeight.toFloat()
            val maxBoxW = 300f
            val maxBoxH = 220f
            val boxW = minOf(maxBoxW, maxBoxH * viewAspect)
            val boxH = boxW / viewAspect
            val cropWidthDp = boxW.dp
            val cropHeightDp = boxH.dp
            val viewWidthPx = with(density) { cropWidthDp.toPx() }
            val viewHeightPx = with(density) { cropHeightDp.toPx() }

            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "选择图片背景区域",
                            style = AppType.dialogTitle,
                            color = Color.White
                        )

                        Text(
                            text = "单指拖动图片平移，双指捏合进行缩放",
                            style = AppType.caption,
                            color = Color.Gray
                        )

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
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(0.5f, 8f)
                                            offset = offset + pan
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

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("取消", style = AppType.button, color = Color.White)
                            }

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
                                enabled = !isSaving,
                                loading = isSaving,
                                modifier = Modifier.weight(1f),
                                containerColor = selectBlue
                            )
                        }
                    }
                }
            }
        } else if (isLoading) {
            Dialog(onDismissRequest = onDismiss) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(overlayCard, AppShape.button),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = selectBlue)
                }
            }
        }
    }
}