package com.juge.app.ui.adjust

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.juge.app.CropImageHelper
import com.juge.app.ReminderWidgetProvider
import com.juge.app.WidgetCanvasRenderer
import com.juge.app.data.*
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.UserColorPresets
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetFont
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.AddColorPresetButton
import com.juge.app.ui.BackgroundColorBlockedDialog
import com.juge.app.ui.CheckChip
import com.juge.app.ui.ColorPickerDialog
import com.juge.app.ui.DeleteColorPresetDialog
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.findHostActivity
import com.juge.app.ui.ThickTrackSlider
import com.juge.app.ui.legal.LegalFooter
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.selectBlue
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTutorialExpanded = !isTutorialExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Widgets,
                                contentDescription = null,
                                tint = Color(0xFF1E293B),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "桌面组件添加教程",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        }
                        Text(
                            text = if (isTutorialExpanded) "收起 ▲" else "展开 ▼",
                            fontSize = 12.sp,
                            color = Color(0xFF0F766E)
                        )
                    }

                    AnimatedVisibility(visible = isTutorialExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "国产手机系统的桌面组件添加入口比较隐蔽，且大多不允许 App 内一键添加到桌面，请在桌面通过系统入口手动添加：",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // 通用步骤提示
                            Text(
                                text = "通用步骤：回到桌面 -> 双指捏合（或长按桌面空白处）-> 点击“添加小部件 / 卡片 / 窗口小工具” -> 找到「句阁 / DeskQuotes」-> 拖动到桌面。",
                                fontSize = 13.sp,
                                color = Color(0xFF0F766E),
                                fontWeight = FontWeight.Bold,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            // 从桌面快捷方式引导进入时附带一键回桌面：
                            // 系统组件列表页没有对外入口，回桌面长按是唯一路径，帮用户省掉切回桌面的操作
                            if (initiallyTutorialExpanded) {
                                Button(
                                    onClick = { context.findHostActivity()?.moveTaskToBack(true) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = selectBlue, contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("回到桌面，长按空白处添加", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFF1E293B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("常见国产手机添加与权限教程：", fontSize = 13.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• 华为 / 鸿蒙系统：在桌面双指捏合 -> 选择“服务卡片” -> 滑动到最下方选择“窗口小工具” -> 找到“句阁 / DeskQuotes”拖动到桌面。\n" +
                                       "• 小米 / HyperOS / MIUI：桌面双指捏合 -> 点击“添加小部件” -> 搜索“句阁”或滑动选择安卓原生小部件添加。\n" +
                                       "• OPPO / VIVO：桌面双指捏合 -> 点击“卡片 / 插件” -> 选择“句阁”添加。\n" +
                                       "• 无法弹出快捷面板？：请确保在手机的“应用设置 -> 权限管理”中，为本应用开启了【后台弹出界面】和【悬浮窗】权限，防止系统拦截快捷面板 Activity 的启动。",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = accentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "组件风格",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentBlue
                        )
                    }

                    // 风格预设区：按分类展示（经典风格 → 萌宠风格 → 明信片风格）
                    // 间距统一交给外层 Column 的 spacedBy(10.dp)，这里不再叠加 Spacer/分割线
                    val widgetSizeStr = ReminderWidgetProvider.getWidgetSizeString(context, selectedWidgetId)
                    // 分类标题尾部统一附加默认卡片尺寸，如 4×2 / 4×4
                    val sizeLabel = widgetSizeStr.replace("x", "×").replace("*", "×")

                    // 复用：单个预设横滑列表
                    @Composable
                    fun PresetRow(presets: List<Pair<String, WidgetStyle>>, title: String?) {
                        if (title != null) {
                            // 标题后附加"会员专属"角标（分类含会员功能时）
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            }
                        }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(presets.size) { index ->
                                val (presetName, preset) = presets[index]
                                val isProPreset = WidgetStyle.isProPreset(preset)
                                val isLocked = !isActivated && isProPreset

                                val presetBitmap by produceState<Bitmap?>(
                                    // 缓存已在则同帧就有图：滚动回来看不到空白帧
                                    initialValue = WidgetCanvasRenderer.cachedThumbnail(150, if (preset.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) 60 else 80, preset),
                                    preset, presetName
                                ) {
                                    value = withContext(Dispatchers.Default) {
                                        try {
                                            WidgetCanvasRenderer.renderThumbnail(
                                                context = context,
                                                widthDp = 150,
                                                heightDp = if (preset.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) 60 else 80,
                                                // 缩略图内不写风格名：名称以独立文本显示在缩略图下方，
                                                // 避免"图里的字"与"图下的名"重复，也省掉一次 StaticLayout 排版
                                                content = "",
                                                style = preset
                                            )
                                        } catch (t: Throwable) {
                                            Bitmap.createBitmap(150, 80, Bitmap.Config.ARGB_8888)
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.width(150.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                Box(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .height(80.dp)
                                        .clickable {
                                            val newPresetStyle = preset.copy(
                                                backgroundOpacity = selectedStyle.backgroundOpacity,
                                                cornerRadiusDp = selectedStyle.cornerRadiusDp
                                            )
                                            android.util.Log.d("JugeTap", "preset=$presetName shape=${preset.shape} locked=$isLocked selectPath=${if (isLocked) "preview" else "save"}")
                                            if (isLocked) {
                                                // 非激活用户：预览模式，仅更新顶部预览组件，不保存
                                                onStyleStateChange(newPresetStyle)
                                            } else {
                                                onSelectPreset(selectedWidgetId, selectedReminderId, newPresetStyle)
                                            }
                                        }
                                ) {
                                    if (presetBitmap != null) {
                                        Image(
                                            bitmap = presetBitmap!!.asImageBitmap(),
                                            contentDescription = "Preset Style ${index + 1}",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    }

                                    // 免费风格角标：当前只有「纯色圆角」一款免费，单独标出来避免与分类标题的"会员专属"混淆
                                    if (!isProPreset) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                                .background(selectBlue, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text("免费", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                                    // 风格名称独立显示在缩略图下方（缩略图内不再渲染文字）
                                    Text(
                                        text = presetName,
                                        fontSize = 13.sp,
                                        color = if (isProPreset) Color(0xFF64748B) else selectBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 1. 经典风格（含唯一免费款「纯色圆角」，其余为会员专属）
                    PresetRow(WidgetStyle.CLASSIC_PRESETS, "经典风格 · $sizeLabel")
                    // 2. 萌宠风格（位于经典与明信片之间）
                    PresetRow(WidgetStyle.PET_PRESETS, "萌宠风格 · 会员专属 · $sizeLabel")

                    // 精选卡片插画
                    // 明信片风格固定为 4×4（竖版上下分割）/ 4×3（书香书架），不随当前组件尺寸变化
                    Text("明信片风格 · 会员专属 · 4×4 / 4×3", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 代码绘制的大卡（书香书架）排在插图素材之前：
                        // 缩略图统一按 150×80 渲染显示（与经典/萌宠/插画组一致），
                        // 组件内容在渲染时按画布自适应重排，避免一行内宽窄不一
                        val codePreviewHeightDp = 80
                        val codePreviewWidthDp = 150
                        WidgetStyle.POSTCARD_CODE_PRESETS.forEach { (presetName, preset) ->
                            val isCodeSelected = selectedStyle.shape == preset.shape &&
                                selectedStyle.presetImageResName == null &&
                                selectedStyle.backgroundImagePath.isNullOrEmpty()
                            val codeBitmap by produceState<Bitmap?>(
                                // 缓存已在则同帧就有图：滚动回来看不到空白帧
                                initialValue = WidgetCanvasRenderer.cachedThumbnail(WidgetStyle.POSTCARD_CODE_RENDER_WIDTH_DP, WidgetStyle.POSTCARD_CODE_RENDER_HEIGHT_DP, preset),
                                preset, presetName
                            ) {
                                value = withContext(Dispatchers.Default) {
                                    try {
                                        WidgetCanvasRenderer.renderThumbnail(
                                            context = context,
                                            widthDp = codePreviewWidthDp,
                                            heightDp = codePreviewHeightDp,
                                            // 缩略图内不写风格名，名称显示在下方标签
                                            content = "",
                                            style = preset
                                        )
                                    } catch (t: Throwable) {
                                        Bitmap.createBitmap(codePreviewWidthDp, codePreviewHeightDp, Bitmap.Config.ARGB_8888)
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.width(codePreviewWidthDp.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                            Box(
                                modifier = Modifier
                                    .width(codePreviewWidthDp.dp)
                                    .height(codePreviewHeightDp.dp)
                                    .clickable {
                                        val newPresetStyle = preset.copy(
                                            backgroundOpacity = selectedStyle.backgroundOpacity,
                                            cornerRadiusDp = selectedStyle.cornerRadiusDp
                                        )
                                        onSelectPreset(selectedWidgetId, selectedReminderId, newPresetStyle)
                                    }
                            ) {
                                if (codeBitmap != null) {
                                    Image(
                                        bitmap = codeBitmap!!.asImageBitmap(),
                                        contentDescription = presetName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isCodeSelected) 3.dp else 1.dp,
                                                color = if (isCodeSelected) selectBlue else Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                                Text(
                                    text = presetName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                            }
                        }

                        // 明信片行的微缩城市 / 立体场景：缩略图按组件真实渲染——
                        // 若像插图那样裁原图，几张会长得一模一样、分不出方案。
                        WidgetStyle.POSTCARD_RENDERED_PRESETS.forEach { (presetName, preset) ->
                            val isRenderedSelected = selectedStyle.presetId == preset.presetId
                            val isRenderedLocked = !isActivated && WidgetStyle.isProPreset(preset)
                            // 缩略图统一按 150×80 渲染：天气盒子（4×4）等方版风格
                            // 在缩略图中与明信片同宽，组件内容按画布自适应重排
                            val renderedW = codePreviewWidthDp
                            val renderedH = codePreviewHeightDp
                            val renderedBitmap by produceState<Bitmap?>(
                                // 缓存已在则同帧就有图：滚动回来看不到空白帧
                                initialValue = WidgetCanvasRenderer.cachedThumbnail(renderedW, renderedH, preset),
                                preset, presetName
                            ) {
                                value = withContext(Dispatchers.Default) {
                                    try {
                                        WidgetCanvasRenderer.renderThumbnail(
                                                                    context = context,
                                                                    widthDp = renderedW,
                                                                    heightDp = renderedH,
                                                                    content = "",
                                                                    style = preset
                                                                )
                                    } catch (t: Throwable) {
                                        Bitmap.createBitmap(renderedW, renderedH, Bitmap.Config.ARGB_8888)
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.width(codePreviewWidthDp.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                            Box(
                                modifier = Modifier
                                    .width(codePreviewWidthDp.dp)
                                    .height(codePreviewHeightDp.dp)
                                    .clickable {
                                        val newPresetStyle = preset.copy(
                                            backgroundOpacity = selectedStyle.backgroundOpacity,
                                            cornerRadiusDp = selectedStyle.cornerRadiusDp
                                        )
                                        if (isRenderedLocked) {
                                            onStyleStateChange(newPresetStyle)
                                        } else {
                                            onSelectPreset(selectedWidgetId, selectedReminderId, newPresetStyle)
                                        }
                                    }
                            ) {
                                if (renderedBitmap != null) {
                                    Image(
                                        bitmap = renderedBitmap!!.asImageBitmap(),
                                        contentDescription = presetName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isRenderedSelected) 3.dp else 1.dp,
                                                color = if (isRenderedSelected) selectBlue else Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                                Text(
                                    text = presetName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                            }
                        }

                        val illustrations = WidgetStyle.ILLUSTRATION_PRESETS
                        
                        illustrations.forEachIndexed { illusIndex, (resName, desc) ->
                             val matchingPreset = WidgetStyle.PRESETS.find { it.presetImageResName == resName }
                             val targetShape = matchingPreset?.shape ?: WidgetShape.SPLIT_CARD
                             val isSelected = selectedStyle.presetImageResName == resName && selectedStyle.shape == targetShape && selectedStyle.backgroundImagePath.isNullOrEmpty()
                            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                            
                            Column(
                                modifier = Modifier.width(150.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                            Box(
                                modifier = Modifier
                                    .width(150.dp)
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) selectBlue else Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                         val matchingPreset = WidgetStyle.PRESETS.find { it.presetImageResName == resName }
                                         val targetShape = matchingPreset?.shape ?: WidgetShape.SPLIT_CARD
                                         val newStyle = selectedStyle.copy(
                                             shape = targetShape,
                                             presetImageResName = resName,
                                             backgroundImagePath = null,
                                             bgImageScaleMode = matchingPreset?.bgImageScaleMode ?: ImageScaleMode.CENTER_CROP,
                                             authorSignature = matchingPreset?.authorSignature ?: selectedStyle.authorSignature
                                         )
                                        onStyleStateChange(newStyle)
                                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                    }
                            ) {
                                // 图内只放画面；名称以独立文本显示在缩略图下方（与其他风格行统一）
                                AsyncImage(
                                    model = resId,
                                    contentDescription = desc,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    error = androidx.compose.ui.graphics.painter.ColorPainter(Color.LightGray)
                                )
                            }
                            Text(
                                text = desc,
                                fontSize = 13.sp,
                                color = if (isSelected) selectBlue else Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                            }
                        }

                    }
                }
            }
        }

        // 3. ✍️ 文本内容与字形定制
        item(key = "text_style_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.EditNote,
                            contentDescription = null,
                            tint = accentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "文本内容与字形定制",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentBlue
                        )
                    }

                    // 文本框输入（最多 500 字）
                    OutlinedTextField(
                        value = selectedContent,
                        onValueChange = {
                            if (it.length <= 500) {
                                onContentStateChange(it)
                                onStyleChange(selectedWidgetId, selectedReminderId, it, selectedStyle)
                            }
                        },
                        placeholder = { Text("输入桌面小组件上显示的金句", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B),
                            focusedBorderColor = Color(0xFF0F766E),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            cursorColor = Color(0xFF0F766E)
                        )
                    )

                    // 字体选择 (横滚动)
                    Text("选择金句字体", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WidgetFont.values().forEach { fnt ->
                            FilterChip(
                                selected = selectedStyle.font == fnt,
                                onClick = {
                                    val newStyle = selectedStyle.copy(font = fnt)
                                    onStyleStateChange(newStyle)
                                    onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                },
                                label = { Text(fnt.displayName, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = Color(0xFF64748B),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFFF1F5F9),
                                    selectedContainerColor = selectBlue
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedStyle.font == fnt,
                                    borderColor = Color(0xFFE2E8F0),
                                    selectedBorderColor = selectBlue
                                )
                            )
                        }
                    }

                    // 字体大小调节 Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("字体大小: ${selectedStyle.fontSizeSp.toInt()} sp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    }
                    ThickTrackSlider(
                        value = selectedStyle.fontSizeSp,
                        onValueChange = {
                            val newStyle = selectedStyle.copy(fontSizeSp = it)
                            onStyleStateChange(newStyle)
                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                        },
                        valueRange = 12.0f..48.0f,
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )

                    // 加粗 / 倾斜 / 文字阴影：带勾选框的胶囊选项
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CheckChip(
                            checked = selectedStyle.fontBold,
                            label = "加粗",
                            onClick = {
                                val newStyle = selectedStyle.copy(fontBold = !selectedStyle.fontBold)
                                onStyleStateChange(newStyle)
                                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                            }
                        )
                        CheckChip(
                            checked = selectedStyle.fontItalic,
                            label = "倾斜",
                            onClick = {
                                val newStyle = selectedStyle.copy(fontItalic = !selectedStyle.fontItalic)
                                onStyleStateChange(newStyle)
                                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                            }
                        )
                        CheckChip(
                            checked = selectedStyle.shadow.enabled,
                            label = "文字阴影",
                            onClick = {
                                // 统一阴影设置：模糊度 1，水平/垂直偏移 0，颜色跟随字体颜色
                                val newStyle = selectedStyle.copy(
                                    shadow = selectedStyle.shadow.copy(
                                        enabled = !selectedStyle.shadow.enabled,
                                        color = selectedStyle.fontColor,
                                        radius = 1f,
                                        dx = 0f,
                                        dy = 0f
                                    )
                                )
                                onStyleStateChange(newStyle)
                                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                            }
                        )
                    }

                    // 文字对齐方式：3+1 双排——两端对齐单独一行，避免整行横向滚动与页签左右滑动手势冲突
                    Text("对齐方式", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    val alignments = listOf(
                        "LEFT" to "左对齐",
                        "CENTER" to "居中对齐",
                        "RIGHT" to "右对齐",
                        "JUSTIFY" to "两端对齐"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            alignments.take(3).forEach { (alignKey, label) ->
                                CheckChip(
                                    checked = selectedStyle.textAlign.uppercase(java.util.Locale.ROOT) == alignKey,
                                    label = label,
                                    onClick = {
                                        val newStyle = selectedStyle.copy(textAlign = alignKey)
                                        onStyleStateChange(newStyle)
                                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                    }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            alignments.drop(3).forEach { (alignKey, label) ->
                                CheckChip(
                                    checked = selectedStyle.textAlign.uppercase(java.util.Locale.ROOT) == alignKey,
                                    label = label,
                                    onClick = {
                                        val newStyle = selectedStyle.copy(textAlign = alignKey)
                                        onStyleStateChange(newStyle)
                                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                    }
                                )
                            }
                        }
                    }

                }
            }
        }

        // 4. 🎨 色彩与质感艺术
        item(key = "color_texture_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Brush,
                            contentDescription = null,
                            tint = accentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "色彩与质感艺术",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentBlue
                        )
                    }

                    // 4.1. 字体颜色设置
                    Text("字体颜色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            fontColorPresets.forEach { parsedColor ->
                                val isSelected = selectedStyle.fontColor == parsedColor
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(parsedColor))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) selectBlue else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(9.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                val newStyle = selectedStyle.copy(fontColor = parsedColor)
                                                onStyleStateChange(newStyle)
                                                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                            },
                                            onLongClick = {
                                                pendingDeleteFontColor = parsedColor
                                            }
                                        )
                                )
                            }
                        }
                        AddColorPresetButton(onClick = { showFontColorPicker = true })
                    }
                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // 4.2. 卡片背景颜色设置
                    // 主体四周透明的形状不支持背景色：控件常驻置灰，点击弹窗说明原因
                    // （与"外框圆角（此形状无需调整）"保持同一套处理方式）
                    val canSetBackgroundColor = WidgetStyle.supportsBackgroundColor(selectedStyle.shape)
                    Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.alpha(if (canSetBackgroundColor) 1f else 0.45f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                    Text(
                        text = if (canSetBackgroundColor) "小组件背景颜色" else "小组件背景颜色（此形状无法设置）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSetBackgroundColor) Color(0xFF64748B) else Color(0xFF94A3B8)
                    )
                    
                    // 预设背景色彩
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            backgroundColorPresets.forEach { colorInt ->
                                val isSelected = selectedStyle.backgroundColor == colorInt
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(colorInt))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) selectBlue else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(9.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                val newStyle = selectedStyle.copy(backgroundColor = colorInt)
                                                onStyleStateChange(newStyle)
                                                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                            },
                                            onLongClick = {
                                                pendingDeleteBackgroundColor = colorInt
                                            }
                                        )
                                )
                            }
                        }
                        AddColorPresetButton(onClick = { showBackgroundColorPicker = true })
                    }

                    }
                    if (!canSetBackgroundColor) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showBackgroundColorBlockedTip = true }
                        )
                    }
                    }
                }
            }
        }

        // 5. 🖼 物理形状与背景材质
        item(key = "shape_background_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Wallpaper,
                            contentDescription = null,
                            tint = accentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "物理形状与背景材质",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentBlue
                        )
                    }

                    // 背景圆角尺寸 Slider (适用于卡片类形状)
                    // 无论形状是否可调圆角都常驻渲染，避免切换形状时控件移除导致列表高度突变跳动（“页面自动上滑”）
                    val canAdjustCorner = selectedStyle.shape != WidgetShape.ELLIPSE &&
                                          selectedStyle.shape != WidgetShape.TORN_PAPER &&
                                          // 书香书架本身就是组件、四周透明，没有外框可调圆角
                                          selectedStyle.shape != WidgetShape.BOOKSHELF &&
                                          // 巨剑/毛绒森林/小霸王游戏机是整幅插画，裁剪圆角会切掉剑身、毛绒小树与实物模型
                                          selectedStyle.shape != WidgetShape.GIANT_SWORD &&
                                          selectedStyle.shape != WidgetShape.PLUSH_FOREST &&
                                          selectedStyle.shape != WidgetShape.SUBOR_CONSOLE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (canAdjustCorner) {
                                "外框圆角大小: ${selectedStyle.cornerRadiusDp.toInt()} dp"
                            } else {
                                "外框圆角（此形状无需调整）"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canAdjustCorner) Color(0xFF334155) else Color(0xFF94A3B8)
                        )
                    }
                    ThickTrackSlider(
                        value = selectedStyle.cornerRadiusDp,
                        onValueChange = {
                            val newStyle = selectedStyle.copy(cornerRadiusDp = it)
                            onStyleStateChange(newStyle)
                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                        },
                        valueRange = 0.0f..30.0f,
                        steps = 29,
                        enabled = canAdjustCorner,
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )

                    // 背景不透明度 Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("背景不透明度: ${(selectedStyle.backgroundOpacity * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    }
                    ThickTrackSlider(
                        value = selectedStyle.backgroundOpacity,
                        onValueChange = {
                            val newStyle = selectedStyle.copy(backgroundOpacity = it)
                            onStyleStateChange(newStyle)
                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                        },
                        valueRange = 0.0f..1.0f,
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // 上传本地背景图
                    Text("自定义背景图", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedStyle.backgroundImagePath.isNullOrEmpty()) {
                            Button(
                                onClick = {
                                    if (!isActivated) {
                                        Toast.makeText(context, "👑 上传自定义背景图为 PRO 专属功能，请先激活！", Toast.LENGTH_SHORT).show()
                                    } else {
                                        selectImageLauncher.launch("image/*")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0), contentColor = accentBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+ 选择本地图片", color = accentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { selectImageLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("更换图片", color = Color(0xFF1E293B), fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    try {
                                        selectedStyle.backgroundImagePath?.let { File(it).delete() }
                                    } catch (e: Exception) {}
                                    val newStyle = selectedStyle.copy(backgroundImagePath = null)
                                    onStyleStateChange(newStyle)
                                    onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("清除背景图", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    // 如果包含背景图，选择平铺模式
                    if (!selectedStyle.backgroundImagePath.isNullOrEmpty()) {
                        Text("图片填充模式", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ImageScaleMode.values().forEach { mode ->
                                val modeDesc = when (mode) {
                                    ImageScaleMode.STRETCH -> "拉伸"
                                    ImageScaleMode.CENTER_CROP -> "裁剪"
                                    ImageScaleMode.CENTER_FIT -> "完整"
                                    ImageScaleMode.CENTER_CROP_TOP -> "铺满"
                                    ImageScaleMode.TILE -> "平铺"
                                }
                                FilterChip(
                                    selected = selectedStyle.bgImageScaleMode == mode,
                                    onClick = {
                                        val newStyle = selectedStyle.copy(bgImageScaleMode = mode)
                                        onStyleStateChange(newStyle)
                                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                    },
                                    label = { Text(modeDesc, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        labelColor = Color(0xFF64748B),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFFF1F5F9),
                                        selectedContainerColor = selectBlue
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selectedStyle.bgImageScaleMode == mode,
                                        borderColor = Color(0xFFE2E8F0),
                                        selectedBorderColor = selectBlue
                                    )
                                )
                            }
                        }
                    }

                }
            }
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

    // 先取本地快照再判空：pendingCropUri 是 Compose 状态，直接 !! 在重组时序下没有保障
    val cropUri = pendingCropUri
    if (cropUri != null) {
        val (widgetWidthDp, widgetHeightDp) = ReminderWidgetProvider.getWidgetSizeDp(context, selectedWidgetId)
        val cropTarget = CropImageHelper.cropTargetForWidget(widgetWidthDp, widgetHeightDp)
        CropImageHelper.ImageCropDialog(
            uri = cropUri,
            onDismiss = { pendingCropUri = null },
            onCropSuccess = { path ->
                // 旧背景图不在这里删：延后到新图真正落库时再删，避免用户中途取消后原图已丢
                val newStyle = selectedStyle.copy(backgroundImagePath = path)
                onStyleStateChange(newStyle)
                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
            },
            targetWidth = cropTarget.first,
            targetHeight = cropTarget.second
        )
    }

    if (showFontColorPicker) {
        ColorPickerDialog(
            title = "添加字体颜色预设",
            initialColor = selectedStyle.fontColor,
            onDismiss = { showFontColorPicker = false },
            onConfirm = { color ->
                showFontColorPicker = false
                if (fontColorPresets.contains(color)) {
                    Toast.makeText(context, "该颜色已在预设中", Toast.LENGTH_SHORT).show()
                } else {
                    fontColorPresets = UserColorPresets.addFontColor(context, color)
                }
            }
        )
    }

    if (showBackgroundColorBlockedTip) {
        BackgroundColorBlockedDialog(onDismiss = { showBackgroundColorBlockedTip = false })
    }

    if (showBackgroundColorPicker) {
        ColorPickerDialog(
            title = "添加背景颜色预设",
            initialColor = selectedStyle.backgroundColor,
            onDismiss = { showBackgroundColorPicker = false },
            onConfirm = { color ->
                showBackgroundColorPicker = false
                if (backgroundColorPresets.contains(color)) {
                    Toast.makeText(context, "该颜色已在预设中", Toast.LENGTH_SHORT).show()
                } else {
                    backgroundColorPresets = UserColorPresets.addBackgroundColor(context, color)
                }
            }
        )
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
