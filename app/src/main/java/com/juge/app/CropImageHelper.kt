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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    // 相册图片可能携带 EXIF 旋转信息，直接解码会得到横躺/倒置的图
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = androidx.exifinterface.media.ExifInterface(input)
                when (exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            Timber.w(e, "Failed to read EXIF orientation")
            0f
        }
        if (degrees == 0f) return bitmap
        return try {
            val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Exception) {
            Timber.w(e, "Failed to apply EXIF rotation")
            bitmap
        }
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
            val viewAspectRatio = viewWidthPx / viewHeightPx
            val imageAspectRatio = bitmap.width.toFloat() / bitmap.height
            val initScale = if (imageAspectRatio > viewAspectRatio) {
                viewHeightPx / bitmap.height
            } else {
                viewWidthPx / bitmap.width
            }
            val initX = (viewWidthPx - bitmap.width * initScale) / 2f
            val initY = (viewHeightPx - bitmap.height * initScale) / 2f

            val R = targetWidth.toFloat() / viewWidthPx
            val matrix = android.graphics.Matrix()
            // 1. 初始平移和缩放
            matrix.postScale(initScale, initScale)
            matrix.postTranslate(initX, initY)
            // 2. 手势缩放与平移
            matrix.postScale(scale, scale, viewWidthPx / 2f, viewHeightPx / 2f)
            matrix.postTranslate(offset.x, offset.y)
            // 3. 映射到目标物理尺寸
            matrix.postScale(R, R)

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
            bitmap = loadBitmapFromUri(context, uri)
            isLoading = false
            if (bitmap == null) {
                Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }

        if (bitmap != null) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "单指拖动图片平移，双指捏合进行缩放",
                            fontSize = 12.sp,
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
                                modifier = Modifier.weight(1f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3A56)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("取消", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    val savedPath = cropAndSaveBitmap(
                                        context = context,
                                        bitmap = bitmap!!,
                                        viewWidthPx = viewWidthPx,
                                        viewHeightPx = viewHeightPx,
                                        scale = scale,
                                        offset = offset,
                                        targetWidth = targetWidth,
                                        targetHeight = targetHeight
                                    )
                                    if (savedPath != null) {
                                        onCropSuccess(savedPath)
                                    } else {
                                        Toast.makeText(context, "保存裁剪失败", Toast.LENGTH_SHORT).show()
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                            ) {
                                Text("保存并使用", color = Color.White)
                            }
                        }
                    }
                }
            }
        } else if (isLoading) {
            Dialog(onDismissRequest = onDismiss) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFF1E2436), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                }
            }
        }
    }
}