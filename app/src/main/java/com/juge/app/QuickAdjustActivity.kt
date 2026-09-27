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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
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
import com.juge.app.ui.ColorPickerDialog
import com.juge.app.ui.DeleteColorPresetDialog
import com.juge.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

private val panelBg = androidx.compose.ui.graphics.Color(0xFFEEF2F7) // 面板底色（冷调浅灰蓝）：让面板内的纯白卡片分离出来
private val cardBg = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
private val accentBlue = androidx.compose.ui.graphics.Color(0xFF0F766E)
private val borderBlue = androidx.compose.ui.graphics.Color(0xFFE2E8F0)
private val textWhite = androidx.compose.ui.graphics.Color(0xFF0F172A)
private val textGray = androidx.compose.ui.graphics.Color(0xFF64748B)
private val mintBright = androidx.compose.ui.graphics.Color(0xFF2DD4BF) // 选中/激活态填充·薄荷青明亮版（呼应主界面极光渐变）
private val mintSky = androidx.compose.ui.graphics.Color(0xFF38BDF8) // 主按钮渐变终点·晴空天蓝（与主界面同源）
private val mintInk = androidx.compose.ui.graphics.Color(0xFF134E4A) // 明亮薄荷底上的深色文字/图标，保证对比度

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
                var backgroundColorPresets by remember { mutableStateOf(UserColorPresets.backgroundColors(this@QuickAdjustActivity)) }
                var showFontColorPicker by remember { mutableStateOf(false) }
                var showBackgroundColorPicker by remember { mutableStateOf(false) }
                var pendingDeleteFontColor by remember { mutableStateOf<Int?>(null) }
                var pendingDeleteBackgroundColor by remember { mutableStateOf<Int?>(null) }

                // 组件面板可从桌面直接拉起，所以这里也要做一次账号对账：
                // 已登录时用服务端结论回灌本地 PRO，换机后这是唯一的找回入口
                LaunchedEffect(Unit) {
                    val serverSaysPro = AccountSync.refresh(this@QuickAdjustActivity)
                    accountName = AccountStore.snapshot(this@QuickAdjustActivity)?.displayName
                    if (serverSaysPro && !trialManager.isActivated()) {
                        trialManager.activate(TrialManager.PAY_METHOD_ACCOUNT)
                        isActivated = true
                        ReminderWidgetProvider.triggerUpdateAllWidgets(this@QuickAdjustActivity)
                        Toast.makeText(applicationContext, "🎉 已通过账号找回 PRO，全部风格已解锁！", Toast.LENGTH_SHORT).show()
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
                            color = panelBg.copy(alpha = 0.96f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderBlue.copy(alpha = 0.4f)),
                            tonalElevation = 8.dp
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
                                        .background(androidx.compose.ui.graphics.Color(0xFFEDE4D8))
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
                                            Text(
                                                text = "📱 桌面快捷个性化 (尺寸: $sizeStr)",
                                                fontSize = 15.sp,
                                                color = textWhite,
                                                fontWeight = FontWeight.ExtraBold
                                            )
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
                                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF5EFE6)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text("🎨 组件风格", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textWhite)

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
                                                                initialValue = null,
                                                                preset, presetText, isActivated
                                                            ) {
                                                                value = withContext(Dispatchers.Default) {
                                                                    try {
                                                                        WidgetCanvasRenderer.render(
                                                                            context = this@QuickAdjustActivity,
                                                                            widthDp = 120,
                                                                            heightDp = if (preset.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) 60 else 80,
                                                                            content = presetText,
                                                                            style = preset,
                                                                            trialManager = trialManager,
                                                                            isPreview = true
                                                                        )
                                                                    } catch (t: Throwable) {
                                                                        Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
                                                                    }
                                                                }
                                                            }

                                                            Box(
                                                                modifier = Modifier
                                                                    .width(90.dp)
                                                                    .height(72.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(androidx.compose.ui.graphics.Color(0xFFF5EFE6))
                                                                    .border(
                                                                        width = if (isSelected) 3.dp else 1.dp,
                                                                        color = if (isSelected) mintBright else androidx.compose.ui.graphics.Color(0xFFEDE4D8),
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
                                                                Column(
                                                                    modifier = Modifier.fillMaxSize()
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .weight(0.6f)
                                                                    ) {
                                                                        if (presetBitmap != null) {
                                                                            Image(
                                                                                bitmap = presetBitmap!!.asImageBitmap(),
                                                                                contentDescription = name,
                                                                                modifier = Modifier.fillMaxSize(),
                                                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                                            )
                                                                        }
                                                                    }
                                                                    HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFEDE4D8))
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .weight(0.4f)
                                                                            .background(androidx.compose.ui.graphics.Color.White),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Text(
                                                                            text = name,
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = if (isSelected) accentBlue else textGray
                                                                        )
                                                                    }
                                                                }

                                                                // 免费风格角标：当前只有「纯色圆角」一款免费
                                                                if (!WidgetStyle.isProPreset(preset)) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .align(Alignment.TopStart)
                                                                            .padding(4.dp)
                                                                            .background(mintBright, RoundedCornerShape(4.dp))
                                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                    ) {
                                                                        Text("免费", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = mintInk)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                // 1. 经典风格（含唯一免费款「纯色圆角」，其余为会员专属）
                                                QuickPresetRow(WidgetStyle.CLASSIC_PRESETS, "经典风格")
                                                Spacer(modifier = Modifier.height(6.dp))
                                                // 2. 萌宠风格（位于经典与明信片之间）
                                                QuickPresetRow(WidgetStyle.PET_PRESETS, "萌宠风格 · 会员专属")

                                                Spacer(modifier = Modifier.height(4.dp))
                                                Spacer(modifier = Modifier.height(4.dp))

                                                // 精选卡片插画
                                                Text("明信片风格 · 会员专属 · 4×4 / 4×3", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // 代码绘制的大卡（书香书架 4×3）排在插图素材之前：
                                                    // 没有插图素材，按桌面 4×3 的设计尺寸渲染后再缩小显示
                                                    val codePreviewHeightDp = 72
                                                    val codePreviewWidthDp = (codePreviewHeightDp * 4f / 3f).toInt()
                                                    WidgetStyle.POSTCARD_CODE_PRESETS.forEach { (presetName, preset) ->
                                                        val isCodeSelected = currentStyle.shape == preset.shape &&
                                                            currentStyle.presetImageResName == null &&
                                                            currentStyle.backgroundImagePath.isNullOrEmpty()
                                                        val codeBitmap by produceState<Bitmap?>(
                                                            initialValue = null,
                                                            preset, presetName, isActivated
                                                        ) {
                                                            value = withContext(Dispatchers.Default) {
                                                                try {
                                                                    WidgetCanvasRenderer.render(
                                                                        context = this@QuickAdjustActivity,
                                                                        widthDp = WidgetStyle.POSTCARD_CODE_RENDER_WIDTH_DP,
                                                                        heightDp = WidgetStyle.POSTCARD_CODE_RENDER_HEIGHT_DP,
                                                                        content = presetName,
                                                                        style = preset,
                                                                        trialManager = trialManager,
                                                                        isPreview = true
                                                                    )
                                                                } catch (t: Throwable) {
                                                                    Bitmap.createBitmap(WidgetStyle.POSTCARD_CODE_RENDER_WIDTH_DP, WidgetStyle.POSTCARD_CODE_RENDER_HEIGHT_DP, Bitmap.Config.ARGB_8888)
                                                                }
                                                            }
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .width(codePreviewWidthDp.dp)
                                                                .height(codePreviewHeightDp.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .border(
                                                                    width = if (isCodeSelected) 3.dp else 1.dp,
                                                                    color = if (isCodeSelected) mintBright else androidx.compose.ui.graphics.Color(0xFFEDE4D8),
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
                                                                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                                                                )
                                                            }
                                                        }
                                                    }

                                                    val illustrations = WidgetStyle.ILLUSTRATION_PRESETS
                                                    
                                                    illustrations.forEachIndexed { illusIndex, (resName, desc) ->
                                                         val matchingPreset = WidgetStyle.PRESETS.find { it.presetImageResName == resName }
                                                         val targetShape = matchingPreset?.shape ?: WidgetShape.SPLIT_CARD
                                                         val isSelected = currentStyle.presetImageResName == resName && currentStyle.shape == targetShape && currentStyle.backgroundImagePath.isNullOrEmpty()
                                                        val resId = resources.getIdentifier(resName, "drawable", packageName)
                                                        
                                                        Box(
                                                            modifier = Modifier
                                                                .width(90.dp)
                                                                .height(72.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(androidx.compose.ui.graphics.Color(0xFFF5EFE6))
                                                                .border(
                                                                    width = if (isSelected) 3.dp else 1.dp,
                                                                    color = if (isSelected) mintBright else androidx.compose.ui.graphics.Color(0xFFEDE4D8),
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
                                                            Column(
                                                                modifier = Modifier.fillMaxSize()
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .weight(0.6f)
                                                                ) {
                                                                    if (resId != 0) {
                                                                        AsyncImage(
                                                                            model = resId,
                                                                            contentDescription = null,
                                                                            modifier = Modifier.fillMaxSize(),
                                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                                            error = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.LightGray)
                                                                        )
                                                                    } else {
                                                                        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Gray))
                                                                    }
                                                                }
                                                                HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFEDE4D8))
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .weight(0.4f)
                                                                        .background(androidx.compose.ui.graphics.Color.White),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = desc,
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) accentBlue else textGray
                                                                    )
                                                                }
                                                            }
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
                                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF5EFE6)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text("✍️ 文本内容与字形定制", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                                
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
                                                            label = { Text(font.displayName, fontSize = 12.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(
                                                                labelColor = textGray,
                                                                selectedLabelColor = mintInk,
                                                                containerColor = androidx.compose.ui.graphics.Color(0xFFF5EFE6),
                                                                selectedContainerColor = mintBright
                                                            ),
                                                            border = FilterChipDefaults.filterChipBorder(
                                                                enabled = true,
                                                                selected = currentStyle.font == font,
                                                                borderColor = borderBlue,
                                                                selectedBorderColor = mintBright
                                                            )
                                                        )
                                                    }
                                                }

                                                Text("字号大小: ${currentStyle.fontSizeSp.toInt()} sp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                Slider(
                                                    value = currentStyle.fontSizeSp,
                                                    onValueChange = { currentStyle = currentStyle.copy(fontSizeSp = it) },
                                                    valueRange = 12.0f..48.0f,
                                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = mintBright,
                                                        activeTrackColor = mintBright,
                                                        inactiveTrackColor = borderBlue
                                                    )
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = currentStyle.fontBold,
                                                            onCheckedChange = { currentStyle = currentStyle.copy(fontBold = it) },
                                                            colors = CheckboxDefaults.colors(
                                                                checkedColor = mintBright,
                                                                checkmarkColor = mintInk,
                                                                uncheckedColor = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                                                            ),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("加粗", fontSize = 12.sp, color = textWhite)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = currentStyle.fontItalic,
                                                            onCheckedChange = { currentStyle = currentStyle.copy(fontItalic = it) },
                                                            colors = CheckboxDefaults.colors(
                                                                checkedColor = mintBright,
                                                                checkmarkColor = mintInk,
                                                                uncheckedColor = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                                                            ),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("倾斜", fontSize = 12.sp, color = textWhite)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = currentStyle.shadow.enabled,
                                                            onCheckedChange = {
                                                                // 统一阴影设置：模糊度 1，水平/垂直偏移 0，颜色跟随字体颜色
                                                                currentStyle = currentStyle.copy(
                                                                    shadow = currentStyle.shadow.copy(
                                                                        enabled = it,
                                                                        color = currentStyle.fontColor,
                                                                        radius = 1f,
                                                                        dx = 0f,
                                                                        dy = 0f
                                                                    )
                                                                )
                                                            },
                                                            colors = CheckboxDefaults.colors(
                                                                checkedColor = mintBright,
                                                                checkmarkColor = mintInk,
                                                                uncheckedColor = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                                                            ),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("文字阴影", fontSize = 12.sp, color = textWhite)
                                                    }
                                                }

                                                Text("对齐方式", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    val alignments = listOf(
                                                        "LEFT" to "左对齐",
                                                        "CENTER" to "居中对齐",
                                                        "RIGHT" to "右对齐"
                                                    )
                                                    alignments.forEach { (alignKey, label) ->
                                                        FilterChip(
                                                            selected = currentStyle.textAlign.uppercase(java.util.Locale.ROOT) == alignKey,
                                                            onClick = { currentStyle = currentStyle.copy(textAlign = alignKey) },
                                                            label = { Text(label, fontSize = 12.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(
                                                                labelColor = textGray,
                                                                selectedLabelColor = mintInk,
                                                                containerColor = androidx.compose.ui.graphics.Color(0xFFF5EFE6),
                                                                selectedContainerColor = mintBright
                                                            ),
                                                            border = FilterChipDefaults.filterChipBorder(
                                                                enabled = true,
                                                                selected = currentStyle.textAlign.uppercase(java.util.Locale.ROOT) == alignKey,
                                                                borderColor = borderBlue,
                                                                selectedBorderColor = mintBright
                                                            )
                                                        )
                                                    }
                                                }

                                            }

                                                HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFF5EFE6))

                                                Text("行高间距", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("行高: ${"%.1f".format(currentStyle.lineSpacingMultiplier)}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                        Slider(
                                                            value = currentStyle.lineSpacingMultiplier,
                                                            onValueChange = { currentStyle = currentStyle.copy(lineSpacingMultiplier = it) },
                                                            valueRange = 0.5f..3.0f,
                                                            modifier = Modifier.fillMaxWidth().height(24.dp),
                                                            colors = SliderDefaults.colors(
                                                                thumbColor = mintBright,
                                                                activeTrackColor = mintBright,
                                                                inactiveTrackColor = borderBlue
                                                            )
                                                        )
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("字间距: ${"%.1f".format(currentStyle.letterSpacing)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                        Slider(
                                                            value = currentStyle.letterSpacing,
                                                            onValueChange = { currentStyle = currentStyle.copy(letterSpacing = it) },
                                                            valueRange = 0f..1.0f,
                                                            modifier = Modifier.fillMaxWidth().height(24.dp),
                                                            colors = SliderDefaults.colors(
                                                                thumbColor = mintBright,
                                                                activeTrackColor = mintBright,
                                                                inactiveTrackColor = borderBlue
                                                            )
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
                                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF5EFE6)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text("🎨 风格与色彩艺术", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textWhite)


                                                 Text("字体颜色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
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
                                                             val isSelected = currentStyle.fontColor == parsedColor
                                                             Box(
                                                                 modifier = Modifier
                                                                     .size(32.dp)
                                                                     .clip(RoundedCornerShape(8.dp))
                                                                     .background(androidx.compose.ui.graphics.Color(parsedColor))
                                                                     .border(
                                                                         width = if (isSelected) 3.dp else 1.dp,
                                                                         color = if (isSelected) mintBright else borderBlue,
                                                                         shape = RoundedCornerShape(8.dp)
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
                                                         size = 32.dp,
                                                         corner = 8.dp,
                                                         borderColor = borderBlue,
                                                         contentColor = textGray
                                                     )
                                                 }
                                                 Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        val hsv = remember(currentStyle.fontColor) {
                                                            val arr = FloatArray(3)
                                                            android.graphics.Color.colorToHSV(currentStyle.fontColor, arr)
                                                            arr
                                                        }
                                                        var hue by remember(currentStyle.fontColor) { mutableStateOf(hsv[0]) }
                                                        var saturation by remember(currentStyle.fontColor) { mutableStateOf(hsv[1]) }
                                                        var value by remember(currentStyle.fontColor) { mutableStateOf(hsv[2]) }

                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.fillMaxWidth().height(26.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(8.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(
                                                                        Brush.horizontalGradient(
                                                                            colors = listOf(
                                                                                androidx.compose.ui.graphics.Color.Red,
                                                                                androidx.compose.ui.graphics.Color.Yellow,
                                                                                androidx.compose.ui.graphics.Color.Green,
                                                                                androidx.compose.ui.graphics.Color.Cyan,
                                                                                androidx.compose.ui.graphics.Color.Blue,
                                                                                androidx.compose.ui.graphics.Color.Magenta,
                                                                                androidx.compose.ui.graphics.Color.Red
                                                                            )
                                                                        )
                                                                    )
                                                            )
                                                            Slider(
                                                                value = hue,
                                                                onValueChange = {
                                                                    hue = it
                                                                    val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                                                                    currentStyle = currentStyle.copy(fontColor = newColor)
                                                                },
                                                                valueRange = 0f..360f,
                                                                colors = SliderDefaults.colors(
                                                                    thumbColor = androidx.compose.ui.graphics.Color.White,
                                                                    activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                                                                    inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent
                                                                ),
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }

                                                        val baseHueColor = remember(hue) {
                                                            androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                                                        }
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.fillMaxWidth().height(26.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(8.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(
                                                                        Brush.horizontalGradient(
                                                                            colors = listOf(
                                                                                androidx.compose.ui.graphics.Color.Black,
                                                                                baseHueColor,
                                                                                androidx.compose.ui.graphics.Color.White
                                                                            )
                                                                        )
                                                                    )
                                                            )
                                                            Slider(
                                                                value = value,
                                                                onValueChange = {
                                                                    value = it
                                                                    saturation = if (it < 0.5f) 1f else (1f - (it - 0.5f) * 2f).coerceIn(0f, 1f)
                                                                    val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                                                                    currentStyle = currentStyle.copy(fontColor = newColor)
                                                                },
                                                                valueRange = 0f..1f,
                                                                colors = SliderDefaults.colors(
                                                                    thumbColor = androidx.compose.ui.graphics.Color.White,
                                                                    activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                                                                    inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent
                                                                ),
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .background(androidx.compose.ui.graphics.Color(currentStyle.fontColor), RoundedCornerShape(8.dp))
                                                            .border(1.dp, borderBlue, RoundedCornerShape(8.dp))
                                                    )
                                                }

                                                HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFF5EFE6))

                                                Text("背景预设", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
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
                                                            val isSelected = currentStyle.backgroundColor == colorInt
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(androidx.compose.ui.graphics.Color(colorInt))
                                                                    .border(
                                                                        width = if (isSelected) 3.dp else 1.dp,
                                                                        color = if (isSelected) mintBright else borderBlue,
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

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        val bgHsv = remember(currentStyle.backgroundColor) {
                                                            val arr = FloatArray(3)
                                                            android.graphics.Color.colorToHSV(currentStyle.backgroundColor, arr)
                                                            arr
                                                        }
                                                        var bgHue by remember(currentStyle.backgroundColor) { mutableStateOf(bgHsv[0]) }
                                                        var bgSaturation by remember(currentStyle.backgroundColor) { mutableStateOf(bgHsv[1]) }
                                                        var bgValue by remember(currentStyle.backgroundColor) { mutableStateOf(bgHsv[2]) }

                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.fillMaxWidth().height(26.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(8.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(
                                                                        Brush.horizontalGradient(
                                                                            colors = listOf(
                                                                                androidx.compose.ui.graphics.Color.Red,
                                                                                androidx.compose.ui.graphics.Color.Yellow,
                                                                                androidx.compose.ui.graphics.Color.Green,
                                                                                androidx.compose.ui.graphics.Color.Cyan,
                                                                                androidx.compose.ui.graphics.Color.Blue,
                                                                                androidx.compose.ui.graphics.Color.Magenta,
                                                                                androidx.compose.ui.graphics.Color.Red
                                                                            )
                                                                        )
                                                                    )
                                                            )
                                                            Slider(
                                                                value = bgHue,
                                                                onValueChange = {
                                                                    bgHue = it
                                                                    val newColor = android.graphics.Color.HSVToColor(floatArrayOf(bgHue, bgSaturation, bgValue))
                                                                    currentStyle = currentStyle.copy(backgroundColor = newColor)
                                                                },
                                                                valueRange = 0f..360f,
                                                                colors = SliderDefaults.colors(
                                                                    thumbColor = androidx.compose.ui.graphics.Color.White,
                                                                    activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                                                                    inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent
                                                                ),
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }

                                                        val bgBaseHueColor = remember(bgHue) {
                                                            androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(bgHue, 1f, 1f)))
                                                        }
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.fillMaxWidth().height(26.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(8.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(
                                                                        Brush.horizontalGradient(
                                                                            colors = listOf(
                                                                                androidx.compose.ui.graphics.Color.Black,
                                                                                bgBaseHueColor,
                                                                                androidx.compose.ui.graphics.Color.White
                                                                            )
                                                                        )
                                                                    )
                                                            )
                                                            Slider(
                                                                value = bgValue,
                                                                onValueChange = {
                                                                    bgValue = it
                                                                    bgSaturation = if (it < 0.5f) 1f else (1f - (it - 0.5f) * 2f).coerceIn(0f, 1f)
                                                                    val newColor = android.graphics.Color.HSVToColor(floatArrayOf(bgHue, bgSaturation, bgValue))
                                                                    currentStyle = currentStyle.copy(backgroundColor = newColor)
                                                                },
                                                                valueRange = 0f..1f,
                                                                colors = SliderDefaults.colors(
                                                                    thumbColor = androidx.compose.ui.graphics.Color.White,
                                                                    activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                                                                    inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent
                                                                ),
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .background(androidx.compose.ui.graphics.Color(currentStyle.backgroundColor), RoundedCornerShape(8.dp))
                                                            .border(1.dp, borderBlue, RoundedCornerShape(8.dp))
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 卡片四（🖼 背景与物理外框）
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF5EFE6)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text("🖼 背景与物理外框", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textWhite)

                                                val canAdjustCorner = currentStyle.shape != WidgetShape.ELLIPSE &&
                                                                      currentStyle.shape != WidgetShape.TORN_PAPER &&
                                                                      // 书香书架本身就是组件、四周透明，没有外框可调圆角
                                                                      currentStyle.shape != WidgetShape.BOOKSHELF

                                                // 无论形状是否可调圆角都常驻渲染，避免切换形状时控件移除导致列表高度突变跳动（“页面自动上滑”）
                                                Text(
                                                    text = if (canAdjustCorner) "外框圆角大小: ${currentStyle.cornerRadiusDp.toInt()} dp" else "外框圆角（此形状无需调整）",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (canAdjustCorner) textWhite else textGray
                                                )
                                                Slider(
                                                    value = currentStyle.cornerRadiusDp,
                                                    onValueChange = { currentStyle = currentStyle.copy(cornerRadiusDp = it) },
                                                    valueRange = 0.0f..30.0f,
                                                    steps = 29,
                                                    enabled = canAdjustCorner,
                                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = mintBright,
                                                        activeTrackColor = mintBright,
                                                        // steps 会触发 M3 绘制刻度点，默认刻度色偏深，看起来像一排黑点；置透明与其他滑条保持一致
                                                        activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                                                        inactiveTrackColor = borderBlue,
                                                        inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                                                        disabledThumbColor = borderBlue,
                                                        disabledInactiveTrackColor = borderBlue
                                                    )
                                                )

                                                Text("背景不透明度: ${(currentStyle.backgroundOpacity * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textGray)
                                                Slider(
                                                    value = currentStyle.backgroundOpacity,
                                                    onValueChange = { currentStyle = currentStyle.copy(backgroundOpacity = it) },
                                                    valueRange = 0.0f..1.0f,
                                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = mintBright,
                                                        activeTrackColor = mintBright,
                                                        inactiveTrackColor = borderBlue
                                                    )
                                                )

                                                HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFF5EFE6))

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
                                                            colors = ButtonDefaults.buttonColors(containerColor = mintBright, contentColor = mintInk),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("+ 选择背景图片", color = mintInk, fontSize = 12.sp)
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
                                                                    selectedLabelColor = mintInk,
                                                                    containerColor = androidx.compose.ui.graphics.Color(0xFFF5EFE6),
                                                                    selectedContainerColor = mintBright
                                                                ),
                                                                border = FilterChipDefaults.filterChipBorder(
                                                                    enabled = true,
                                                                    selected = currentStyle.bgImageScaleMode == mode,
                                                                    borderColor = borderBlue,
                                                                    selectedBorderColor = mintBright
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
                                            val styleToSave = currentStyle
                                            // 写库与位图渲染都是耗时操作，移出主线程避免卡顿
                                            lifecycleScope.launch(Dispatchers.IO) {
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
                                                } catch (e: Exception) {
                                                    timber.log.Timber.e(e, "save widget config failed")
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(appContext, "同步刷新成功", Toast.LENGTH_SHORT).show()
                                                        finish()
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isSaving,
                                        modifier = Modifier
                                            .weight(1f)
                                            .shadow(3.dp, RoundedCornerShape(12.dp))
                                            .background(
                                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    listOf(
                                                        mintBright,
                                                        mintSky
                                                    )
                                                ),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                                    ) {
                                        Text("保存并刷新", color = mintInk, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        
                        if (pendingCropUri != null) {
                            val cropTarget = CropImageHelper.cropTargetForWidget(
                                ReminderWidgetProvider.getWidgetSizeString(this@QuickAdjustActivity, appWidgetId)
                            )
                            CropImageHelper.ImageCropDialog(
                                uri = pendingCropUri!!,
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
                                        androidx.compose.material3.Text("👑 PRO 会员", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = androidx.compose.ui.graphics.Color(0xFF1E293B))
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
                                            modifier = Modifier.fillMaxWidth().height(46.dp).shadow(4.dp, RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(mintBright, mintSky)), RoundedCornerShape(12.dp)),
                                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            if (isPaying) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = mintInk, strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                                androidx.compose.material3.Text("支付确认中…", color = mintInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            } else {
                                                androidx.compose.material3.Text("支付宝支付 ${ProPurchase.PRICE_TEXT}", color = mintInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
