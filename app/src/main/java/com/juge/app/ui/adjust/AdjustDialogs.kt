package com.juge.app.ui.adjust

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.juge.app.CropImageHelper
import com.juge.app.ReminderWidgetProvider
import com.juge.app.ui.ColorPickerDialog

/**
 * 「添加一个颜色预设」弹窗：重复颜色只提示、不写入，是两处颜色预设共用的入口。
 *
 * 预设列表状态由调用方持有（不改变状态归属），[onNewColor] 只在确认了一个新颜色时回调。
 */
@Composable
internal fun AddColorPresetDialog(
    title: String,
    initialColor: Int,
    existingPresets: List<Int>,
    onDismiss: () -> Unit,
    onNewColor: (Int) -> Unit
) {
    val context = LocalContext.current
    ColorPickerDialog(
        title = title,
        initialColor = initialColor,
        onDismiss = onDismiss,
        onConfirm = { color ->
            onDismiss()
            if (existingPresets.contains(color)) {
                Toast.makeText(context, "该颜色已在预设中", Toast.LENGTH_SHORT).show()
            } else {
                onNewColor(color)
            }
        }
    )
}

/**
 * 背景图裁剪弹窗：把「按组件真实尺寸取裁剪比例」的规则收在这里，
 * 裁剪成功后由调用方决定怎么写样式。
 */
@Composable
internal fun BackgroundCropDialog(
    cropUri: Uri?,
    selectedWidgetId: Int,
    onDismiss: () -> Unit,
    onCropped: (String) -> Unit
) {
    if (cropUri != null) {
        val context = LocalContext.current
        val (widgetWidthDp, widgetHeightDp) = ReminderWidgetProvider.getWidgetSizeDp(context, selectedWidgetId)
        val cropTarget = CropImageHelper.cropTargetForWidget(widgetWidthDp, widgetHeightDp)
        CropImageHelper.ImageCropDialog(
            uri = cropUri,
            onDismiss = onDismiss,
            onCropSuccess = onCropped,
            targetWidth = cropTarget.first,
            targetHeight = cropTarget.second
        )
    }
}
