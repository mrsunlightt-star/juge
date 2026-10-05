package com.juge.app

import coil.compose.AsyncImage
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import java.io.File
import com.juge.app.account.AccountDialog
import com.juge.app.account.AccountStore
import com.juge.app.account.AccountSync
import com.juge.app.data.*
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.AddColorPresetButton
import com.juge.app.ui.BackgroundColorBlockedDialog
import com.juge.app.ui.CheckChip
import com.juge.app.ui.ColorPickerDialog
import com.juge.app.ui.DeleteColorPresetDialog
import com.juge.app.ui.ThickTrackSlider
import com.juge.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

private val cardBg = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
private val accentBlue = androidx.compose.ui.graphics.Color(0xFF0F766E)
private val borderBlue = androidx.compose.ui.graphics.Color(0xFFE2E8F0)
private val textWhite = androidx.compose.ui.graphics.Color(0xFF0F172A)
private val textGray = androidx.compose.ui.graphics.Color(0xFF64748B)
private val mintInk = androidx.compose.ui.graphics.Color(0xFF134E4A) // 明亮薄荷底上的深色文字/图标，保证对比度
private val selectBlue = androidx.compose.ui.graphics.Color(0xFF42B8EC) // 按钮/页签选中态填充·取自主界面晴空蓝背景（配白字）

class QuickAdjustActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val configId = intent.getLongExtra("config_id", -1L)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val dbHelper = DbHelper.getInstance(this)
        val trialManager = TrialManager.getInstance(this)

        // 自动迁移并读取微件配置库数据
        lifecycleScope.launch(Dispatchers.IO) {
            dbHelper.migrateLegacyDataIfNeeded()
            val configs = dbHelper.getAllWidgetConfigs()
            // 依次回退：点击时携带的 config_id → 组件已绑定配置 → 未被其他组件占用的配置 → 首个配置。
            // 不再无条件 firstOrNull 兜底，避免误编辑其他组件的配置内容。
            val boundConfigId = ReminderWidgetProvider.getBoundConfigId(applicationContext, appWidgetId)
            val currentConfig = configs.find { it.id == configId }
                ?: configs.find { it.id == boundConfigId }
                ?: run {
                    val allWidgetIds = ReminderWidgetProvider.getAllAppWidgetIds(applicationContext)
                    val otherBoundIds = allWidgetIds.filter { it != appWidgetId }
                        .map { ReminderWidgetProvider.getBoundConfigId(applicationContext, it) }
                        .toSet()
                    configs.find { it.id !in otherBoundIds } ?: configs.firstOrNull()
                }

            // 打开面板即确保组件与配置的绑定关系存在，避免渲染层与编辑层各拿各的配置
            if (currentConfig != null && currentConfig.id != boundConfigId) {
                ReminderWidgetProvider.bindConfigToWidget(applicationContext, appWidgetId, currentConfig.id)
            }

            withContext(Dispatchers.Main) {
                if (currentConfig == null) {
                    Toast.makeText(this@QuickAdjustActivity, "无微件配置，请先在应用内创建", Toast.LENGTH_SHORT).show()
                    finish()
                    return@withContext
                }

                setupContent(appWidgetId, currentConfig, dbHelper, trialManager)
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    private fun setupContent(appWidgetId: Int, currentConfig: WidgetConfig, dbHelper: DbHelper, trialManager: TrialManager) {
        setContent {
            MyApplicationTheme {
                val scope = rememberCoroutineScope()
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                var textContent by remember { mutableStateOf(currentConfig.content) }
                var currentStyle by remember {
                    mutableStateOf(
                        ReminderWidgetProvider.getWidgetStyle(this@QuickAdjustActivity, appWidgetId, currentConfig.styleJson)
                    )
                }
                
                var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
                var showProDialog by remember { mutableStateOf(false) }
                // 支付进行中：用于按钮置忙，避免重复拉起收银台
                var isPaying by remember { mutableStateOf(false) }
                // 保存进行中标记：防止快速连点导致重复写库、重复刷新
                var isSaving by remember { mutableStateOf(false) }
                // 激活状态用可变状态承载：面板内完成激活后预设列表的锁定标记能立即刷新
                var isActivated by remember { mutableStateOf(trialManager.isActivated()) }
                var showAccountDialog by remember { mutableStateOf(false) }
                var accountName by remember { mutableStateOf(AccountStore.snapshot(this@QuickAdjustActivity)?.displayName) }

                // 颜色预设：内置色 + 用户自添加色，长按均可删除
                var fontColorPresets by remember { mutableStateOf(UserColorPresets.fontColors(this@QuickAdjustActivity)) }
                var showFontColorPicker by remember { mutableStateOf(false) }
                var pendingDeleteFontColor by remember { mutableStateOf<Int?>(null) }
                var backgroundColorPresets by remember { mutableStateOf(UserColorPresets.backgroundColors(this@QuickAdjustActivity)) }
                var showBackgroundColorPicker by remember { mutableStateOf(false) }
                var pendingDeleteBackgroundColor by remember { mutableStateOf<Int?>(null) }
                var showBackgroundColorBlockedTip by remember { mutableStateOf(false) }

                // 组件面板可从桌面直接拉起，所以这里也要做一次账号对账：
                // 已登录时用服务端结论回灌本地 PRO，换机后这是唯一的找回入口
                LaunchedEffect(Unit) {
                    accountName = AccountStore.snapshot(this@QuickAdjustActivity)?.displayName
                    // 与 MainActivity 保持同一套规则：只有服务端明确说「不是 PRO」且本地激活
                    // 是账号授予的，才撤销；未登录/请求失败一律保持原状。
                    val outcome = AccountSync.refresh(this@QuickAdjustActivity)
                    if (outcome is AccountSync.Outcome.ServerSays) {
                        if (outcome.pro && !trialManager.isActivated()) {
                            trialManager.activate(TrialManager.PAY_METHOD_ACCOUNT)
                            isActivated = true
                            ReminderWidgetProvider.triggerUpdateAllWidgets(this@QuickAdjustActivity)
                            Toast.makeText(applicationContext, "🎉 已通过账号找回 PRO，全部风格已解锁！", Toast.LENGTH_SHORT).show()
                        } else if (!outcome.pro && trialManager.isActivated() &&
                            trialManager.activationRecord()?.payMethod == TrialManager.PAY_METHOD_ACCOUNT
                        ) {
                            trialManager.resetActivation()
                            isActivated = false
                            ReminderWidgetProvider.triggerUpdateAllWidgets(this@QuickAdjustActivity)
                        }
                    }
                }
                val selectImageLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        pendingCropUri = uri
                    }
                }
                
                Dialog(
                    onDismissRequest = { finish() },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { finish() }, 
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.85f)
                                .clickable(enabled = false) {},
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                            // 冷灰调面板（用户选定 #F1F5F9）：与缩略图底色、卡片边框同色系，
                            // 整体最统一；比白色卡片略深，层次清晰且不透壁纸
                            color = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderBlue.copy(alpha = 0.4f)),
                            // tonalElevation 会叠加一层 Material 色调（偏蓝的 primary tint），
                            // 会把白色卡片染蓝，保持 0，层次由卡片自身边框承担。
                            tonalElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 8.dp)
                            ) {
                                // 顶部小药丸装饰条
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .width(36.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(androidx.compose.ui.graphics.Color(0xFFE2E8F0))
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .imePadding(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val sizeStr = ReminderWidgetProvider.getWidgetSizeString(this@QuickAdjustActivity, appWidgetId)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.Smartphone,
                                                    contentDescription = null,
                                                    tint = textWhite,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "桌面快捷个性化 (尺寸: $sizeStr)",
                                                    fontSize = 15.sp,
                                                    color = textWhite,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                            Text(
                                                text = "进入句阁 >",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accentBlue,
                                                modifier = Modifier.clickable {
                                                    val mainIntent = Intent(this@QuickAdjustActivity, MainActivity::class.java).apply {
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                    }
                                                    startActivity(mainIntent)
                                                    finish()
                                                }
                                            )
                                        }
                                    }

                                    // 卡片一（🎨 组件风格）—— 先选风格，再到下方微调文字、字体、颜色等细节
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF1F5F9)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Palette,
                                                        contentDescription = null,
                                                        tint = textWhite,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text("组件风格", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                                }

                                                // 复用：单个预设横滑列表
                                                @Composable
                                                fun QuickPresetRow(presets: List<Pair<String, WidgetStyle>>, title: String?) {
                                                    if (title != null) {
                                                        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                    }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        presets.forEachIndexed { index, (name, preset) ->
                                                            val isLocked = !isActivated && WidgetStyle.isProPreset(preset)
                                                            val isSelected = currentStyle.shape == preset.shape &&
                                                                             currentStyle.backgroundColor == preset.backgroundColor &&
                                                                             currentStyle.gradientColors == preset.gradientColors &&
                                                                             currentStyle.textureType == preset.textureType &&
                                                                             currentStyle.presetImageResName == preset.presetImageResName

                                                            val presetText = name

                                                            val presetBitmap by produceState<Bitmap?>(
                                                                // 缓存已在则同帧就有图：滚动回来看不到空白帧
                                                                initialValue = WidgetCanvasRenderer.cachedThumbnail(150, if (preset.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) 60 else 80, preset),
                                                                preset, presetText
                                                            ) {
                                                                value = withContext(Dispatchers.Default) {
                                                                    try {
                                                                        WidgetCanvasRenderer.renderThumbnail(
                                                                            context = this@QuickAdjustActivity,
                                                                            widthDp = 150,
                                                                            heightDp = if (preset.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) 60 else 80,
                                                                            content = "",
                                                                            style = preset
                                                                        )
                                                                    } catch (t: Throwable) {
                                                                        Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
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
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(androidx.compose.ui.graphics.Color(0xFFF1F5F9))
                                                                    .border(
                                                                        width = if (isSelected) 3.dp else 1.dp,
                                                                        color = if (isSelected) selectBlue else androidx.compose.ui.graphics.Color(0xFFE2E8F0),
                                                                        shape = RoundedCornerShape(8.dp)
                                                                    )
                                                                    .clickable {
                                                                        if (isLocked) {
                                                                            Toast.makeText(this@QuickAdjustActivity, "此高级风格为 PRO 专属，请先一键激活！", Toast.LENGTH_SHORT).show()
                                                                        } else {
                                                                            currentStyle = preset
                                                                        }
                                                                    }
                                                            ) {
                                                                // 图内只放画面；名称以独立文本显示在缩略图下方（与个性定制页统一）
                                                                if (presetBitmap != null) {
                                                                    Image(
                                                                        bitmap = presetBitmap!!.asImageBitmap(),
                                                                        contentDescription = name,
                                                                        modifier = Modifier.fillMaxSize(),
                                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                                    )
                                                                }

                                                                // 免费风格角标：当前只有「纯色圆角」一款免费
                                                                if (!WidgetStyle.isProPreset(preset)) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .align(Alignment.TopStart)
                                                                            .padding(4.dp)
                                                                            .background(selectBlue, RoundedCornerShape(4.dp))
                                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                    ) {
                                                                        Text("免费", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                                                                    }
                                                                }
                                                            }
                                                            Text(
                                                                text = name,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isSelected) selectBlue else textGray,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            )
                                                            }
                                                        }
                                                    }
                                                }

                                                // 1. 经典风格（含唯一免费款「纯色圆角」，其余为会员专属）
                                                QuickPresetRow(WidgetStyle.CLASSIC_PRESETS, "经典风格")
                                                // 分类之间的额外留白：外层 Column 已有 spacedBy(10.dp)，
                                                // 这里只再补 2dp 让分组可辨，再多就显得松散
                                                Spacer(modifier = Modifier.height(2.dp))
                                                // 2. 萌宠风格（位于经典与明信片之间）
                                                QuickPresetRow(WidgetStyle.PET_PRESETS, "萌宠风格 · 会员专属")

                                                Spacer(modifier = Modifier.height(2.dp))

                                                // 精选卡片插画
                                                Text("明信片风格 · 会员专属 · 4×4 / 4×3", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // 代码绘制的大卡（书香书架）排在插图素材之前：
                                                    // 缩略图统一按 150×80 渲染显示（与经典/萌宠/插画组一致），
                                                    // 组件内容在渲染时按画布自适应重排，避免一行内宽窄不一
                                                    val codePreviewHeightDp = 80
                                                    val codePreviewWidthDp = 150
                                                    WidgetStyle.POSTCARD_CODE_PRESETS.forEach { (presetName, preset) ->
                                                        val isCodeSelected = currentStyle.shape == preset.shape &&
                                                            currentStyle.presetImageResName == null &&
                                                            currentStyle.backgroundImagePath.isNullOrEmpty()
                                                        val codeBitmap by produceState<Bitmap?>(
                                                            // 缓存已在则同帧就有图：滚动回来看不到空白帧
                                                            initialValue = WidgetCanvasRenderer.cachedThumbnail(codePreviewWidthDp, codePreviewHeightDp, preset),
                                                            preset, presetName
                                                        ) {
                                                            value = withContext(Dispatchers.Default) {
                                                                try {
                                                                    WidgetCanvasRenderer.renderThumbnail(
                                                                        context = this@QuickAdjustActivity,
                                                                        widthDp = codePreviewWidthDp,
                                                                        heightDp = codePreviewHeightDp,
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
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .border(
                                                                    width = if (isCodeSelected) 3.dp else 1.dp,
                                                                    color = if (isCodeSelected) selectBlue else androidx.compose.ui.graphics.Color(0xFFE2E8F0),
                                                                    shape = RoundedCornerShape(8.dp)
                                                                )
                                                                .clickable {
                                                                    currentStyle = preset.copy(
                                                                        backgroundOpacity = currentStyle.backgroundOpacity,
                                                                        cornerRadiusDp = currentStyle.cornerRadiusDp
                                                                    )
                                                                }
                                                        ) {
                                                            if (codeBitmap != null) {
                                                                Image(
                                                                    bitmap = codeBitmap!!.asImageBitmap(),
                                                                    contentDescription = presetName,
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                                                )
                                                            }
                                                        }
                                                            Text(
                                                                text = presetName,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isCodeSelected) selectBlue else textGray,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            )
                                                        }
                                                    }

                                                    // 「浙江」三连：同一张素材、三种落地方式，缩略图按组件真实渲染
                                                    WidgetStyle.POSTCARD_RENDERED_PRESETS.forEach { (presetName, preset) ->
                                                        val isRenderedSelected = currentStyle.presetId == preset.presetId
                                                        // 缩略图统一按 150×80 渲染：天气盒子（4×4）等方版风格
                                                        // 在缩略图中与明信片同宽，组件内容按画布自适应重排，
                                                        // 避免一行内出现方形/横向混排
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
                                                                        context = this@QuickAdjustActivity,
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
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .border(
                                                                    width = if (isRenderedSelected) 3.dp else 1.dp,
                                                                    color = if (isRenderedSelected) selectBlue else androidx.compose.ui.graphics.Color(0xFFE2E8F0),
                                                                    shape = RoundedCornerShape(8.dp)
                                                                )
                                                                .clickable {
                                                                    currentStyle = preset.copy(
                                                                        backgroundOpacity = currentStyle.backgroundOpacity,
                                                                        cornerRadiusDp = currentStyle.cornerRadiusDp
                                                                    )
                                                                }
                                                        ) {
                                                            if (renderedBitmap != null) {
                                                                Image(
                                                                    bitmap = renderedBitmap!!.asImageBitmap(),
                                                                    contentDescription = presetName,
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                                                )
                                                            }
                                                        }
                                                            Text(
                                                                text = presetName,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isRenderedSelected) selectBlue else textGray,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            )
                                                        }
                                                    }

                                                    val illustrations = WidgetStyle.ILLUSTRATION_PRESETS
                                                    
                                                    illustrations.forEachIndexed { illusIndex, (resName, desc) ->
                                                         val matchingPreset = WidgetStyle.PRESETS.find { it.presetImageResName == resName }
                                                         val targetShape = matchingPreset?.shape ?: WidgetShape.SPLIT_CARD
                                                         val isSelected = currentStyle.presetImageResName == resName && currentStyle.shape == targetShape && currentStyle.backgroundImagePath.isNullOrEmpty()
                                                        val resId = resources.getIdentifier(resName, "drawable", packageName)
                                                        
                                                        Column(
                                                            modifier = Modifier.width(150.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(150.dp)
                                                                .height(80.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(androidx.compose.ui.graphics.Color(0xFFF1F5F9))
                                                                .border(
                                                                    width = if (isSelected) 3.dp else 1.dp,
                                                                    color = if (isSelected) selectBlue else androidx.compose.ui.graphics.Color(0xFFE2E8F0),
                                                                    shape = RoundedCornerShape(8.dp)
                                                                )
                                                                .clickable {
                                                                     val matchingPreset = WidgetStyle.PRESETS.find { it.presetImageResName == resName }
                                                                     val targetShape = matchingPreset?.shape ?: WidgetShape.SPLIT_CARD
                                                                     currentStyle = currentStyle.copy(
                                                                         shape = targetShape,
                                                                         presetImageResName = resName,
                                                                         backgroundImagePath = null,
                                                                         bgImageScaleMode = matchingPreset?.bgImageScaleMode ?: ImageScaleMode.CENTER_CROP,
                                                                         authorSignature = matchingPreset?.authorSignature ?: currentStyle.authorSignature
                                                                     )
                                                                }
                                                        ) {
                                                            // 图内只放画面；名称以独立文本显示在缩略图下方
                                                            if (resId != 0) {
                                                                AsyncImage(
                                                                    model = resId,
                                                                    contentDescription = desc,
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                                    error = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.LightGray)
                                                                )
                                                            } else {
                                                                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Gray))
                                                            }
                                                        }
                                                        Text(
                                                            text = desc,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) selectBlue else textGray,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        )
                                                        }
                                                    }

                                                }
                                            }
                                        }
                                    }

                                    // 卡片二（✍️ 文本与字形）
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF1F5F9)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Filled.EditNote,
                                                        contentDescription = null,
                                                        tint = textWhite,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text("文本内容与字形定制", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                                }
                                                
                                                OutlinedTextField(
                                                    value = textContent,
                                                    onValueChange = { textContent = it.take(500) },
                                                    placeholder = { Text("编辑小组件上显示的文本", color = textGray, fontSize = 12.sp) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .bringIntoViewRequester(bringIntoViewRequester)
                                                        .onFocusChanged { if (it.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() } },
                                                    maxLines = 3,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedTextColor = textWhite,
                                                        unfocusedTextColor = textWhite,
                                                        focusedBorderColor = accentBlue,
                                                        unfocusedBorderColor = borderBlue,
                                                        cursorColor = accentBlue
                                                    )
                                                )

                                                Text("渲染字体", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    WidgetFont.values().forEach { font ->
                                                        FilterChip(
                                                            selected = currentStyle.font == font,
                                                            onClick = { currentStyle = currentStyle.copy(font = font) },
                                                            label = { Text(font.displayName, fontSize = 13.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(
                                                                labelColor = textGray,
                                                                selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                                                containerColor = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
                                                                selectedContainerColor = selectBlue
                                                            ),
                                                            border = FilterChipDefaults.filterChipBorder(
                                                                enabled = true,
                                                                selected = currentStyle.font == font,
                                                                borderColor = borderBlue,
                                                                selectedBorderColor = selectBlue
                                                            )
                                                        )
                                                    }
                                                }

                                                Text("字号大小: ${currentStyle.fontSizeSp.toInt()} sp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                ThickTrackSlider(
                                                    value = currentStyle.fontSizeSp,
                                                    onValueChange = { currentStyle = currentStyle.copy(fontSizeSp = it) },
                                                    valueRange = 12.0f..48.0f,
                                                    modifier = Modifier.fillMaxWidth().height(24.dp)
                                                )

                                                // 加粗 / 倾斜 / 文字阴影：带勾选框的胶囊选项
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    CheckChip(
                                                        checked = currentStyle.fontBold,
                                                        label = "加粗",
                                                        onClick = { currentStyle = currentStyle.copy(fontBold = !currentStyle.fontBold) }
                                                    )
                                                    CheckChip(
                                                        checked = currentStyle.fontItalic,
                                                        label = "倾斜",
                                                        onClick = { currentStyle = currentStyle.copy(fontItalic = !currentStyle.fontItalic) }
                                                    )
                                                    CheckChip(
                                                        checked = currentStyle.shadow.enabled,
                                                        label = "文字阴影",
                                                        onClick = {
                                                            // 统一阴影设置：模糊度 1，水平/垂直偏移 0，颜色跟随字体颜色
                                                            currentStyle = currentStyle.copy(
                                                                shadow = currentStyle.shadow.copy(
                                                                    enabled = !currentStyle.shadow.enabled,
                                                                    color = currentStyle.fontColor,
                                                                    radius = 1f,
                                                                    dx = 0f,
                                                                    dy = 0f
                                                                )
                                                            )
                                                        }
                                                    )
                                                }

                                                Text("对齐方式", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                // 3+1 双排：两端对齐单独一行，避免整行横向滚动与页签左右滑动手势冲突
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
                                                                checked = currentStyle.textAlign.uppercase(java.util.Locale.ROOT) == alignKey,
                                                                label = label,
                                                                onClick = { currentStyle = currentStyle.copy(textAlign = alignKey) }
                                                            )
                                                        }
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        alignments.drop(3).forEach { (alignKey, label) ->
                                                            CheckChip(
                                                                checked = currentStyle.textAlign.uppercase(java.util.Locale.ROOT) == alignKey,
                                                                label = label,
                                                                onClick = { currentStyle = currentStyle.copy(textAlign = alignKey) }
                                                            )
                                                        }
                                                    }
                                                }

                                            }

                                                HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFF1F5F9))

                                                Text("行高间距", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("行高: ${"%.1f".format(currentStyle.lineSpacingMultiplier)}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                        ThickTrackSlider(
                                                            value = currentStyle.lineSpacingMultiplier,
                                                            onValueChange = { currentStyle = currentStyle.copy(lineSpacingMultiplier = it) },
                                                            valueRange = 0.5f..3.0f,
                                                            modifier = Modifier.fillMaxWidth().height(24.dp)
                                                        )
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("字间距: ${"%.1f".format(currentStyle.letterSpacing)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                        ThickTrackSlider(
                                                            value = currentStyle.letterSpacing,
                                                            onValueChange = { currentStyle = currentStyle.copy(letterSpacing = it) },
                                                            valueRange = 0f..1.0f,
                                                            modifier = Modifier.fillMaxWidth().height(24.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                    // 卡片三（🌈 风格与色彩）
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF1F5F9)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Brush,
                                                        contentDescription = null,
                                                        tint = textWhite,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text("风格与色彩艺术", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                                }


                                                // 字体颜色：与个性定制页一致——预设色块条 + 添加按钮（弹窗取色），无渐变滑块
                                                Text("字体颜色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
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
                                                            val isSelected = currentStyle.fontColor == parsedColor
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(34.dp)
                                                                    .clip(RoundedCornerShape(9.dp))
                                                                    .background(androidx.compose.ui.graphics.Color(parsedColor))
                                                                    .border(
                                                                        width = if (isSelected) 3.dp else 1.dp,
                                                                        color = if (isSelected) selectBlue else borderBlue,
                                                                        shape = RoundedCornerShape(9.dp)
                                                                    )
                                                                    .combinedClickable(
                                                                        onClick = {
                                                                            currentStyle = currentStyle.copy(fontColor = parsedColor)
                                                                        },
                                                                        onLongClick = {
                                                                            pendingDeleteFontColor = parsedColor
                                                                        }
                                                                    )
                                                            )
                                                        }
                                                    }
                                                    AddColorPresetButton(
                                                        onClick = { showFontColorPicker = true },
                                                        size = 34.dp,
                                                        corner = 9.dp,
                                                        borderColor = borderBlue,
                                                        contentColor = textGray
                                                    )
                                                }

                                                HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFF1F5F9))

                                                // 主体四周透明的形状不支持背景色：控件常驻置灰，点击弹窗说明原因
                                                // （与"外框圆角（此形状无需调整）"保持同一套处理方式）
                                                val canSetBackgroundColor = WidgetStyle.supportsBackgroundColor(currentStyle.shape)
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                Column(
                                                    modifier = Modifier.alpha(if (canSetBackgroundColor) 1f else 0.45f),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                Text(
                                                    text = if (canSetBackgroundColor) "背景预设" else "背景预设（此形状无法设置）",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (canSetBackgroundColor) textGray else textGray
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        backgroundColorPresets.forEach { colorInt ->
                                                            val isSelected = currentStyle.backgroundColor == colorInt
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(androidx.compose.ui.graphics.Color(colorInt))
                                                                    .border(
                                                                        width = if (isSelected) 3.dp else 1.dp,
                                                                        color = if (isSelected) selectBlue else borderBlue,
                                                                        shape = RoundedCornerShape(8.dp)
                                                                    )
                                                                    .combinedClickable(
                                                                        onClick = {
                                                                            currentStyle = currentStyle.copy(backgroundColor = colorInt)
                                                                        },
                                                                        onLongClick = {
                                                                            pendingDeleteBackgroundColor = colorInt
                                                                        }
                                                                    )
                                                            )
                                                        }
                                                    }
                                                    AddColorPresetButton(
                                                        onClick = { showBackgroundColorPicker = true },
                                                        size = 32.dp,
                                                        corner = 8.dp,
                                                        borderColor = borderBlue,
                                                        contentColor = textGray
                                                    )
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

                                    // 卡片四（🖼 背景与物理外框）
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF1F5F9)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Wallpaper,
                                                        contentDescription = null,
                                                        tint = textWhite,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text("背景与物理外框", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                                }

                                                val canAdjustCorner = currentStyle.shape != WidgetShape.ELLIPSE &&
                                                                      currentStyle.shape != WidgetShape.TORN_PAPER &&
                                                                      // 书香书架本身就是组件、四周透明，没有外框可调圆角
                                                                      currentStyle.shape != WidgetShape.BOOKSHELF &&
                                                                      // 巨剑/毛绒森林/小霸王游戏机是整幅插画，裁剪圆角会切掉剑身、毛绒小树与实物模型
                                                                      currentStyle.shape != WidgetShape.GIANT_SWORD &&
                                                                      currentStyle.shape != WidgetShape.PLUSH_FOREST &&
                                                                      currentStyle.shape != WidgetShape.SUBOR_CONSOLE

                                                // 无论形状是否可调圆角都常驻渲染，避免切换形状时控件移除导致列表高度突变跳动（“页面自动上滑”）
                                                Text(
                                                    text = if (canAdjustCorner) "外框圆角大小: ${currentStyle.cornerRadiusDp.toInt()} dp" else "外框圆角（此形状无需调整）",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (canAdjustCorner) textWhite else textGray
                                                )
                                                ThickTrackSlider(
                                                    value = currentStyle.cornerRadiusDp,
                                                    onValueChange = { currentStyle = currentStyle.copy(cornerRadiusDp = it) },
                                                    valueRange = 0.0f..30.0f,
                                                    steps = 29,
                                                    enabled = canAdjustCorner,
                                                    modifier = Modifier.fillMaxWidth().height(24.dp)
                                                )

                                                Text("背景不透明度: ${(currentStyle.backgroundOpacity * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                ThickTrackSlider(
                                                    value = currentStyle.backgroundOpacity,
                                                    onValueChange = { currentStyle = currentStyle.copy(backgroundOpacity = it) },
                                                    valueRange = 0.0f..1.0f,
                                                    modifier = Modifier.fillMaxWidth().height(24.dp)
                                                )

                                                HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFF1F5F9))

                                                Text("自定义背景图", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (currentStyle.backgroundImagePath.isNullOrEmpty()) {
                                                        Button(
                                                            onClick = {
                                                                if (!trialManager.isActivated()) {
                                                                    Toast.makeText(this@QuickAdjustActivity, "👑 上传自定义背景图为 PRO 专属功能，请在应用内激活！", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    selectImageLauncher.launch("image/*")
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE2E8F0), contentColor = accentBlue),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("+ 选择背景图片", color = accentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    } else {
                                                        Button(
                                                            onClick = { selectImageLauncher.launch("image/*") },
                                                            colors = ButtonDefaults.buttonColors(containerColor = cardBg),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, borderBlue),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("更换图片", color = textWhite, fontSize = 12.sp)
                                                        }
                                                        Button(
                                                            onClick = {
                                                                try {
                                                                    currentStyle.backgroundImagePath?.let { File(it).delete() }
                                                                } catch (e: Exception) {}
                                                                currentStyle = currentStyle.copy(backgroundImagePath = null)
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFEF4444)),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("清除背景图", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp)
                                                        }
                                                    }
                                                }

                                                if (!currentStyle.backgroundImagePath.isNullOrEmpty()) {
                                                    Text("图片填充模式", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
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
                                                                selected = currentStyle.bgImageScaleMode == mode,
                                                                onClick = { currentStyle = currentStyle.copy(bgImageScaleMode = mode) },
                                                                label = { Text(modeDesc, fontSize = 12.sp) },
                                                                colors = FilterChipDefaults.filterChipColors(
                                                                    labelColor = textGray,
                                                                    selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                                                    containerColor = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
                                                                    selectedContainerColor = selectBlue
                                                                ),
                                                                border = FilterChipDefaults.filterChipBorder(
                                                                    enabled = true,
                                                                    selected = currentStyle.bgImageScaleMode == mode,
                                                                    borderColor = borderBlue,
                                                                    selectedBorderColor = selectBlue
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                    }

                                    item {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }

                                // 6. 保存与取消按钮
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { finish() },
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, borderBlue),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textGray)
                                    ) {
                                        Text("取消修改", color = textGray)
                                    }

                                    Button(
                                        onClick = {
                                            if (isSaving) return@Button
                                            if (textContent.isBlank()) {
                                                Toast.makeText(this@QuickAdjustActivity, "内容不能为空，请输入文字", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            // 付费点只有两个：会员专属风格、自定义背景图；字体/字号/颜色/圆角等调整全部免费
                                            val needPay = !trialManager.isActivated() && (
                                                WidgetStyle.isProPreset(currentStyle) || !currentStyle.backgroundImagePath.isNullOrEmpty()
                                            )
                                            if (needPay) {
                                                showProDialog = true
                                                return@Button
                                            }
                                            isSaving = true
                                            val appContext = applicationContext
                                            val contentToSave = textContent
                                            // 主体四周透明的形状不支持背景色，落库前统一清空，避免旧配色残留导致外围露出包裹卡片
                                            val styleBeforeCommit = currentStyle.withoutUnsupportedBackgroundColor()
                                            // 编辑期背景图先落在 bg_tmp，走到这里说明确实在保存，提交为正式资源
                                            val committedPath = CropImageHelper.commitBackground(appContext, styleBeforeCommit.backgroundImagePath)
                                            val styleToSave = if (committedPath != styleBeforeCommit.backgroundImagePath) {
                                                styleBeforeCommit.copy(backgroundImagePath = committedPath)
                                            } else styleBeforeCommit
                                            // 写库与位图渲染都是耗时操作，移出主线程避免卡顿
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                var saved = false
                                                try {
                                                    // 保存前确保组件与当前编辑的配置绑定一致，防止渲染层读到别的配置
                                                    ReminderWidgetProvider.bindConfigToWidget(appContext, appWidgetId, currentConfig.id)
                                                    ReminderWidgetProvider.saveWidgetStyle(appContext, appWidgetId, styleToSave)
                                                    dbHelper.updateWidgetConfig(
                                                        currentConfig.copy(
                                                            content = contentToSave,
                                                            styleJson = styleToSave.toJsonString()
                                                        )
                                                    )
                                                    // 旧背景图延后到保存成功时删除：若用户取消修改，原文件保持可用
                                                    val oldBgPath = WidgetStyle.fromJsonString(currentConfig.styleJson).backgroundImagePath
                                                    if (!oldBgPath.isNullOrEmpty() && oldBgPath != styleToSave.backgroundImagePath) {
                                                        try {
                                                            java.io.File(oldBgPath).delete()
                                                        } catch (e: Exception) {
                                                            timber.log.Timber.w(e, "delete old bg image failed")
                                                        }
                                                    }
                                                    // 与主界面保存路径保持一致：全量刷新，避免多组件场景下桌面状态不一致
                                                    ReminderWidgetProvider.triggerUpdateAllWidgets(appContext)
                                                    saved = true
                                                } catch (e: Exception) {
                                                    timber.log.Timber.e(e, "save widget config failed")
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        // 只有真正落库成功才提示成功并关闭页面；失败则留在页面让用户重试
                                                        if (saved) {
                                                            Toast.makeText(appContext, "同步刷新成功", Toast.LENGTH_SHORT).show()
                                                            finish()
                                                        } else {
                                                            Toast.makeText(appContext, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                                                            isSaving = false
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isSaving,
                                        modifier = Modifier
                                            .weight(1f)
                                            .shadow(3.dp, RoundedCornerShape(12.dp))
                                            .background(
                                                selectBlue,
                                                RoundedCornerShape(12.dp)
                                            ),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                                    ) {
                                        Text("保存并刷新", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        
                        // 先取本地快照再判空：pendingCropUri 是 Compose 状态，直接 !! 在重组时序下没有保障
                        val cropUri = pendingCropUri
                        if (cropUri != null) {
                            val (widgetWidthDp, widgetHeightDp) = ReminderWidgetProvider.getWidgetSizeDp(this@QuickAdjustActivity, appWidgetId)
                            val cropTarget = CropImageHelper.cropTargetForWidget(widgetWidthDp, widgetHeightDp)
                            CropImageHelper.ImageCropDialog(
                                uri = cropUri,
                                onDismiss = { pendingCropUri = null },
                                onCropSuccess = { path ->
                                    // 只更新临时样式，旧背景图待保存成功后再删除，取消修改时原文件不受影响
                                    currentStyle = currentStyle.copy(backgroundImagePath = path)
                                },
                                targetWidth = cropTarget.first,
                                targetHeight = cropTarget.second
                            )
                        }
                        if (showProDialog) {
                            Dialog(onDismissRequest = { if (!isPaying) showProDialog = false }) {
                                androidx.compose.material3.Surface(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = androidx.compose.ui.graphics.Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFE5E7EB))
                                ) {
                                    androidx.compose.foundation.layout.Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.WorkspacePremium,
                                                contentDescription = null,
                                                tint = androidx.compose.ui.graphics.Color(0xFFF59E0B),
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            androidx.compose.material3.Text("PRO 会员", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = androidx.compose.ui.graphics.Color(0xFF1E293B))
                                        }
                                        androidx.compose.material3.Text("${ProPurchase.PRICE_TEXT} 一次性买断 · 永久有效", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF0F766E))
                                        androidx.compose.material3.Text("解锁全部内置卡片风格，后续新增免费更新", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF4B5563))
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                if (!isPaying) {
                                                    isPaying = true
                                                    lifecycleScope.launch {
                                                        when (val outcome = ProPurchase.purchase(this@QuickAdjustActivity)) {
                                                            is ProPurchase.Outcome.Paid -> {
                                                                trialManager.activate(TrialManager.PAY_METHOD_ALIPAY)
                                                                isActivated = true
                                                                showProDialog = false
                                                                Toast.makeText(applicationContext, "🎉 PRO 已激活，全部风格已解锁！", Toast.LENGTH_SHORT).show()
                                                            }

                                                            is ProPurchase.Outcome.Unpaid -> if (outcome.message.isNotEmpty()) {
                                                                Toast.makeText(applicationContext, outcome.message, Toast.LENGTH_LONG).show()
                                                            }

                                                            is ProPurchase.Outcome.Failed -> {
                                                                Toast.makeText(applicationContext, outcome.message, Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                        isPaying = false
                                                    }
                                                }
                                            },
                                            enabled = !isPaying,
                                            modifier = Modifier.fillMaxWidth().height(46.dp).shadow(4.dp, RoundedCornerShape(12.dp)).background(selectBlue, RoundedCornerShape(12.dp)),
                                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            if (isPaying) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                                androidx.compose.material3.Text("支付确认中…", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            } else {
                                                androidx.compose.material3.Text("支付宝支付 ${ProPurchase.PRICE_TEXT}", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                        androidx.compose.material3.Text("支付成功后自动激活，无需手动操作。", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF9CA3AF))
                                        androidx.compose.material3.TextButton(onClick = { if (!isPaying) showProDialog = false }, enabled = !isPaying) { androidx.compose.material3.Text("暂不需要", color = androidx.compose.ui.graphics.Color(0xFF9CA3AF), fontSize = 12.sp) }
                                        // 账号入口常驻：不登录是常态，所以只做引导，不做拦截
                                        Box(Modifier.fillMaxWidth().height(1.dp).background(androidx.compose.ui.graphics.Color(0xFFF1F5F9)))
                                        androidx.compose.material3.TextButton(onClick = { showAccountDialog = true }, enabled = !isPaying) {
                                            androidx.compose.material3.Text(
                                                if (accountName.isNullOrBlank()) "账号登录 · 换手机也能找回 PRO" else "账号：$accountName",
                                                color = androidx.compose.ui.graphics.Color(0xFF0284C7),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (showAccountDialog) {
                            AccountDialog(
                                onProConfirmed = {
                                    trialManager.activate(TrialManager.PAY_METHOD_ACCOUNT)
                                    isActivated = true
                                    ReminderWidgetProvider.triggerUpdateAllWidgets(this@QuickAdjustActivity)
                                    Toast.makeText(applicationContext, "🎉 已通过账号找回 PRO，全部风格已解锁！", Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = {
                                    showAccountDialog = false
                                    accountName = AccountStore.snapshot(this@QuickAdjustActivity)?.displayName
                                }
                            )
                        }

                        if (showBackgroundColorBlockedTip) {
                            BackgroundColorBlockedDialog(onDismiss = { showBackgroundColorBlockedTip = false })
                        }

                        if (showFontColorPicker) {
                            ColorPickerDialog(
                                title = "添加字体颜色预设",
                                initialColor = currentStyle.fontColor,
                                onDismiss = { showFontColorPicker = false },
                                onConfirm = { color ->
                                    showFontColorPicker = false
                                    if (fontColorPresets.contains(color)) {
                                        Toast.makeText(this@QuickAdjustActivity, "该颜色已在预设中", Toast.LENGTH_SHORT).show()
                                    } else {
                                        fontColorPresets = UserColorPresets.addFontColor(this@QuickAdjustActivity, color)
                                    }
                                }
                            )
                        }

                        if (showBackgroundColorPicker) {
                            ColorPickerDialog(
                                title = "添加背景颜色预设",
                                initialColor = currentStyle.backgroundColor,
                                onDismiss = { showBackgroundColorPicker = false },
                                onConfirm = { color ->
                                    showBackgroundColorPicker = false
                                    if (backgroundColorPresets.contains(color)) {
                                        Toast.makeText(this@QuickAdjustActivity, "该颜色已在预设中", Toast.LENGTH_SHORT).show()
                                    } else {
                                        backgroundColorPresets = UserColorPresets.addBackgroundColor(this@QuickAdjustActivity, color)
                                    }
                                }
                            )
                        }

                        pendingDeleteFontColor?.let { color ->
                            DeleteColorPresetDialog(
                                color = color,
                                onDismiss = { pendingDeleteFontColor = null },
                                onConfirm = {
                                    fontColorPresets = UserColorPresets.deleteFontColor(this@QuickAdjustActivity, color)
                                    pendingDeleteFontColor = null
                                }
                            )
                        }

                        pendingDeleteBackgroundColor?.let { color ->
                            DeleteColorPresetDialog(
                                color = color,
                                onDismiss = { pendingDeleteBackgroundColor = null },
                                onConfirm = {
                                    backgroundColorPresets = UserColorPresets.deleteBackgroundColor(this@QuickAdjustActivity, color)
                                    pendingDeleteBackgroundColor = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
