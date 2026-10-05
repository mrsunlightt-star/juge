package com.juge.app.ui.adjust

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.CropImageHelper
import com.juge.app.ReminderWidgetProvider
import com.juge.app.data.UserColorPresets
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.BackgroundColorBlockedDialog
import com.juge.app.ui.ColorPickerDialog
import com.juge.app.ui.DeleteColorPresetDialog
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.legal.LegalFooter
import com.juge.app.ui.theme.selectBlue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdjustTabContent(
    widgetConfigs: List<WidgetConfig>,
    isActivated: Boolean,
    selectedWidgetId: Int,
    selectedReminderId: Long,
    selectedStyle: WidgetStyle,
    selectedContent: String,
    onStyleStateChange: (WidgetStyle) -> Unit,
    onContentStateChange: (String) -> Unit,
    onSelectPreset: (Int, Long, WidgetStyle) -> Unit,
    onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome,
    onOpenDoc: (String) -> Unit,
    initiallyTutorialExpanded: Boolean = false
) {
    val context = LocalContext.current
    val appWidgetIds = remember {
        ReminderWidgetProvider.getAllAppWidgetIds(context)
    }
    val widgetCount = appWidgetIds.size

    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropUri = uri
        }
    }

    // 折叠控制（从桌面快捷方式进入时默认展开，配合一键添加不可用时的引导）
    var isTutorialExpanded by remember { mutableStateOf(initiallyTutorialExpanded) }

    // 颜色预设：内置色 + 用户自添加色，长按均可删除
    var fontColorPresets by remember { mutableStateOf(UserColorPresets.fontColors(context)) }
    var backgroundColorPresets by remember { mutableStateOf(UserColorPresets.backgroundColors(context)) }
    var showFontColorPicker by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var pendingDeleteFontColor by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteBackgroundColor by remember { mutableStateOf<Int?>(null) }
    var showBackgroundColorBlockedTip by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 国产手机桌面小组件添加指南 (默认折叠)
        item(key = "tutorial_card") {
            TutorialCard(
                isExpanded = isTutorialExpanded,
                fromShortcut = initiallyTutorialExpanded,
                onToggleExpand = { isTutorialExpanded = !isTutorialExpanded }
            )
        }

        // 仅在桌面未检测到任何组件时展示轻量警告提示
        if (widgetCount == 0) {
            item(key = "no_widget_warning") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFFBEB), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "未检测到已放置桌面小组件。您可在下方进行“模拟预览设计”，添加后将自动应用！",
                            fontSize = 13.sp,
                            color = Color(0xFF78350F)
                        )
                    }
                }
            }
        }

        // 2. 🎨 组件风格（先选风格，再到下方微调文字/字体/颜色等细节）
        item(key = "style_preset_card") {
            StylePresetCard(
                selectedStyle = selectedStyle,
                selectedContent = selectedContent,
                isActivated = isActivated,
                selectedWidgetId = selectedWidgetId,
                selectedReminderId = selectedReminderId,
                onSelectPreset = onSelectPreset,
                onStyleStateChange = onStyleStateChange,
                onStyleChange = onStyleChange
            )
        }

        // 3. ✍️ 文本内容与字形定制
        item(key = "text_style_card") {
            TextStyleCard(
                selectedStyle = selectedStyle,
                selectedContent = selectedContent,
                selectedWidgetId = selectedWidgetId,
                selectedReminderId = selectedReminderId,
                onStyleStateChange = onStyleStateChange,
                onContentStateChange = onContentStateChange,
                onStyleChange = onStyleChange
            )
        }

        // 4. 🎨 色彩与质感艺术
        item(key = "color_texture_card") {
            ColorTextureCard(
                selectedStyle = selectedStyle,
                selectedContent = selectedContent,
                selectedWidgetId = selectedWidgetId,
                selectedReminderId = selectedReminderId,
                fontColorPresets = fontColorPresets,
                backgroundColorPresets = backgroundColorPresets,
                onStyleStateChange = onStyleStateChange,
                onStyleChange = onStyleChange,
                onRequestAddFontColor = { showFontColorPicker = true },
                onRequestAddBackgroundColor = { showBackgroundColorPicker = true },
                onRequestDeleteFontColor = { pendingDeleteFontColor = it },
                onRequestDeleteBackgroundColor = { pendingDeleteBackgroundColor = it },
                onBackgroundColorBlocked = { showBackgroundColorBlockedTip = true }
            )
        }

        // 5. 🖼 物理形状与背景材质
        item(key = "shape_background_card") {
            ShapeBackgroundCard(
                isActivated = isActivated,
                selectedStyle = selectedStyle,
                selectedContent = selectedContent,
                selectedWidgetId = selectedWidgetId,
                selectedReminderId = selectedReminderId,
                onStyleStateChange = onStyleStateChange,
                onStyleChange = onStyleChange,
                onPickImage = { selectImageLauncher.launch("image/*") }
            )
        }

        // 6. 保存修改并同步按钮
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(3.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(selectBlue)
                    .clickable {
                        val outcome = onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, selectedStyle)
                        if (outcome == SaveOutcome.SAVED) {
                            Toast.makeText(context, "✨ 样式已成功保存并同步至手机桌面！", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("保存并应用到桌面小组件", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        item {
            LegalFooter(onOpenDoc)
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // 背景图裁剪 + 颜色预设弹窗（状态留在本页，弹窗实现见 AdjustDialogs.kt）
    BackgroundCropDialog(
        cropUri = pendingCropUri,
        selectedWidgetId = selectedWidgetId,
        onDismiss = { pendingCropUri = null },
        onCropped = { path ->
            // 旧背景图不在这里删：延后到新图真正落库时再删，避免用户中途取消后原图已丢
            val newStyle = selectedStyle.copy(backgroundImagePath = path)
            onStyleStateChange(newStyle)
            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
        }
    )

    if (showFontColorPicker) {
        AddColorPresetDialog(
            title = "添加字体颜色预设",
            initialColor = selectedStyle.fontColor,
            existingPresets = fontColorPresets,
            onDismiss = { showFontColorPicker = false },
            onNewColor = { fontColorPresets = UserColorPresets.addFontColor(context, it) }
        )
    }

    if (showBackgroundColorPicker) {
        AddColorPresetDialog(
            title = "添加背景颜色预设",
            initialColor = selectedStyle.backgroundColor,
            existingPresets = backgroundColorPresets,
            onDismiss = { showBackgroundColorPicker = false },
            onNewColor = { backgroundColorPresets = UserColorPresets.addBackgroundColor(context, it) }
        )
    }

    if (showBackgroundColorBlockedTip) {
        BackgroundColorBlockedDialog(onDismiss = { showBackgroundColorBlockedTip = false })
    }

    pendingDeleteFontColor?.let { color ->
        DeleteColorPresetDialog(
            color = color,
            onDismiss = { pendingDeleteFontColor = null },
            onConfirm = {
                fontColorPresets = UserColorPresets.deleteFontColor(context, color)
                pendingDeleteFontColor = null
            }
        )
    }

    pendingDeleteBackgroundColor?.let { color ->
        DeleteColorPresetDialog(
            color = color,
            onDismiss = { pendingDeleteBackgroundColor = null },
            onConfirm = {
                backgroundColorPresets = UserColorPresets.deleteBackgroundColor(context, color)
                pendingDeleteBackgroundColor = null
            }
        )
    }
}
