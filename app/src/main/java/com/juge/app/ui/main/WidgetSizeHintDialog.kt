package com.juge.app.ui.main

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.juge.app.data.WidgetDisplaySize
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.AppDialogTitle
import com.juge.app.ui.AppTextButton
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.textGray

/** 风格默认尺寸与桌面组件实际尺寸不一致时的提示。 */
internal data class WidgetSizeHint(
    val current: String,
    val target: String,
    val styleName: String,
)

/**
 * 只在「实际行数 != 风格默认行数」时返回提示；改文字/颜色等细节不会触发。
 * [actualSize] 是 [com.juge.app.ReminderWidgetProvider.getWidgetSizeString] 的 "4*2" 格式。
 */
internal fun widgetSizeHintFor(style: WidgetStyle, actualSize: String): WidgetSizeHint? {
    val actualRows = actualSize.substringAfter('*', "").toIntOrNull() ?: return null
    val targetRows = if (style.bestDisplaySize == WidgetDisplaySize.SQUARE_4X4) 4 else 2
    if (actualRows == targetRows) return null
    return WidgetSizeHint(
        current = actualSize.replace('*', '×'),
        target = if (targetRows == 4) "4×4" else "4×2",
        styleName = style.shape.displayName,
    )
}

@Composable
internal fun WidgetSizeHintDialog(hint: WidgetSizeHint, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShape.dialog,
        containerColor = cardBg,
        title = { AppDialogTitle("建议调整桌面组件尺寸") },
        text = {
            Text(
                text = "当前桌面组件是 ${hint.current}，「${hint.styleName}」的最佳显示尺寸是 ${hint.target}。\n\n" +
                    "你可以在桌面长按组件，直接拖动边缘把它拉成 ${hint.target}，不需要删除后重新添加。",
                style = AppType.body,
                color = textGray
            )
        },
        confirmButton = {
            AppTextButton(text = "知道了", onClick = onDismiss, color = accentBlue)
        }
    )
}
