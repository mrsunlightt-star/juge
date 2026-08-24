package com.juge.app


import android.graphics.Bitmap
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import coil.compose.AsyncImage
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.data.*
import com.juge.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import timber.log.Timber
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.io.File
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

private val darkBg = Color(0xFFF8FAFC) // 网站同款清爽晨曦底色 (Fresh Breeze Background)
private val cardBg = Color(0xFFFFFFFF) // 纯净白卡片
private val accentBlue = Color(0xFF0F766E) // 网站薄荷青翠主色 (Fresh Mint Teal)
private val accentLightBlue = Color(0xFF0284C7) // 晴空天蓝 (Sky Cyan)
private val borderBlue = Color(0xFFE2E8F0) // 网站同款精细边框与网格线
private val textWhite = Color(0xFF0F172A) // 现代极简墨色 (Slate Ink)
private val textGray = Color(0xFF64748B) // 板岩轻灰，副标题/描述文字 (Slate Muted)

class MainActivity : ComponentActivity() {

    companion object {
        private var _dbHelper: DbHelper? = null
        private var _trialManager: TrialManager? = null
    }

    private val dbHelper: DbHelper get() = _dbHelper!!
    private val trialManager: TrialManager get() = _trialManager!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (_dbHelper == null) {
            _dbHelper = DbHelper.getInstance(this)
            _trialManager = TrialManager.getInstance(this)
        }

        val goToActivate = intent.getBooleanExtra("go_to_activate", false)
        val editConfigId = intent.getLongExtra("config_id", -1L)

        setContent {
            MyApplicationTheme {
                var isPrivacyAccepted by remember {
                    mutableStateOf(
                        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                            .getBoolean("privacy_accepted", false)
                    )
                }

                if (!isPrivacyAccepted) {
                    PrivacyPolicyDialog(
                        onAgree = {
                            getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("privacy_accepted", true)
                                .apply()
                            isPrivacyAccepted = true
                            // 刷新桌面小组件以恢复正常内容
                            ReminderWidgetProvider.triggerUpdateAllWidgets(this)
                        },
                        onReject = {
                            finish()
                        }
                    )
                } else {
                    MainAppScreen(goToActivate, editConfigId)
                }
            }
        }
    }

    @Composable
    fun PrivacyPolicyDialog(onAgree: () -> Unit, onReject: () -> Unit) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderBlue)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "用户服务与隐私协议提示",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "感谢您使用《句阁》！我们非常重视您的隐私与个人信息保护。在您使用本应用的服务（包括金句展示、自定义背景与桌面组件刷新等）之前，请仔细阅读《用户协议》和《隐私政策》。\n\n1. 本应用为纯本地小部件应用，您的自定义提醒与偏好配置全部保存在您的设备本地中，我们不会收集或向第三方服务器传输您的任何金句数据。\n2. 自定义背景图通过系统图片选择器挑选，图片仅在您主动选择确认后才会导入本应用，未经您选择不会读取相册中的任何内容。\n\n如您同意以上协议，请点击“同意”开始使用我们的服务。",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, borderBlue),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textGray)
                        ) {
                            Text("不同意并退出", fontSize = 13.sp)
                        }

                        Button(
                            onClick = onAgree,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                        ) {
                            Text("同意并继续", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainAppScreen(startOnActivate: Boolean, targetEditConfigId: Long) {
        var subTab by remember { mutableStateOf("library") }
        var isActivatedState by remember { mutableStateOf(trialManager.isActivated()) }

        var categories by remember { mutableStateOf(emptyList<Category>()) }
        var reminders by remember { mutableStateOf(emptyList<Reminder>()) }
        var widgetConfigs by remember { mutableStateOf(emptyList<WidgetConfig>()) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                dbHelper.migrateLegacyDataIfNeeded()
                val cats = dbHelper.getAllCategories()
                val rems = dbHelper.getAllReminders()
                val configs = dbHelper.getAllWidgetConfigs()
                withContext(Dispatchers.Main) {
                    categories = cats
                    reminders = rems
                    widgetConfigs = configs
                }
            }
        }
        var selectedCategory by remember { mutableStateOf<Category?>(null) }

        // 编辑状态弹窗控制 (专属于书库内容编辑)
        var editingReminderForContent by remember { mutableStateOf<Reminder?>(null) }

        // Pager 核心状态与桌面小组件映射
        val appWidgetIds = remember {
            ReminderWidgetProvider.getAllAppWidgetIds(this@MainActivity)
        }
        val widgetCount = appWidgetIds.size

        var selectedWidgetId by remember(appWidgetIds) {
            mutableStateOf(appWidgetIds.firstOrNull() ?: -1)
        }

        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { if (widgetCount == 0) 1 else widgetCount }
        )

        LaunchedEffect(pagerState.currentPage, appWidgetIds) {
            if (appWidgetIds.isNotEmpty()) {
                val page = pagerState.currentPage.coerceIn(0, appWidgetIds.size - 1)
                selectedWidgetId = appWidgetIds[page]
            }
        }

        val selectedConfigId = remember(selectedWidgetId) {
            if (selectedWidgetId != -1) {
                ReminderWidgetProvider.getBoundConfigId(this@MainActivity, selectedWidgetId)
            } else {
                -1L
            }
        }
        val selectedConfig = remember(selectedConfigId, widgetConfigs) {
            if (selectedConfigId != -1L) {
                widgetConfigs.find { it.id == selectedConfigId } ?: widgetConfigs.firstOrNull()
            } else {
                widgetConfigs.firstOrNull()
            }
        }

        // key 使用 config.id 而非对象本身：写库后刷新 configs 会产生新实例，
        // 若以对象为 key 会把用户正在编辑的样式重置回数据库旧值
        var currentStyle by remember(selectedWidgetId, selectedConfig?.id) {
            mutableStateOf(
                if (selectedWidgetId != -1) {
                    ReminderWidgetProvider.getWidgetStyle(this@MainActivity, selectedWidgetId, selectedConfig?.styleJson)
                } else {
                    WidgetStyle.fromJsonString(selectedConfig?.styleJson)
                }
            )
        }

        // 预览区高度随当前组件规格自适应：短组件(4×2)不再预留 4×4 的空间，
        // 从而让“小组件#N”标签行与“跃然纸上/个性定制”页签行同步上移、去掉底部空洞。
        // 封顶 268dp 确保 4×4 组件不被裁切，下限保住最小可见高度。
        fun previewHeightForPage(page: Int): Int {
            val wid = if (appWidgetIds.isNotEmpty()) appWidgetIds[page.coerceIn(0, appWidgetIds.size - 1)] else -1
            val cid = if (wid != -1) ReminderWidgetProvider.getBoundConfigId(this@MainActivity, wid) else -1L
            val cfg = if (cid != -1L) {
                widgetConfigs.find { it.id == cid } ?: widgetConfigs.firstOrNull()
            } else {
                widgetConfigs.firstOrNull()
            }
            val shape = if (wid == selectedWidgetId) currentStyle.shape
                else if (wid != -1) ReminderWidgetProvider.getWidgetStyle(this@MainActivity, wid, cfg?.styleJson).shape
                else WidgetStyle.fromJsonString(cfg?.styleJson).shape
            val spanY = cfg?.sizeType?.split("x", "*")?.getOrNull(1)?.toIntOrNull()
                ?: if (wid != -1) {
                    ReminderWidgetProvider.getWidgetSizeString(this@MainActivity, wid)
                        .split("*").getOrNull(1)?.toIntOrNull() ?: 2
                } else 2
            val sy = spanY.coerceIn(2, 4)
            return when (shape) {
                WidgetShape.SPLIT_CARD_HORIZONTAL -> 130
                else -> 60 + sy * 50
            }
        }
        // 放在 currentStyle 与 previewHeightForPage 声明之后，保证内部引用均已初始化
        // 统一只收紧一行(24dp)：固定 244dp，仅当组件需要的渲染高度超过时才抬高，
        // 封顶 268dp 保证 4×4(预览高 260)不被裁切；这样各种组件都恰好上移一行。
        val previewBoxHeightDp = (244)
            .coerceAtLeast(previewHeightForPage(pagerState.currentPage) + 8)
            .coerceAtMost(268)

        var textContentState by remember(selectedConfig?.id) {
            mutableStateOf(selectedConfig?.content ?: "")
        }

        // 防抖写库任务：声明在 LaunchedEffect 之前，切换组件时可 join 等待未落库的写入完成
        var persistJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        LaunchedEffect(selectedWidgetId) {
            if (selectedWidgetId != -1) {
                // 等待 400ms 防抖写入落库后再读取，避免切换组件时读到数据库旧值覆盖用户草稿
                persistJob?.join()
                val boundConfigId = ReminderWidgetProvider.getBoundConfigId(this@MainActivity, selectedWidgetId)
                val config = widgetConfigs.find { it.id == boundConfigId } ?: widgetConfigs.firstOrNull()
                textContentState = config?.content ?: ""
                currentStyle = ReminderWidgetProvider.getWidgetStyle(this@MainActivity, selectedWidgetId, config?.styleJson)
            }
        }

        val selectedReminder = selectedConfig

        var showProDialog by remember { mutableStateOf(startOnActivate) }

        // 样式保存与更新回调。
        // 新付费规则：预览任意风格、默认组件免费、仅在"同步到桌面"时拦截。
        // 未激活时切到付费风格仍允许在 App 内预览，但点保存同步到桌面时弹激活。
        val onStyleChange: (Int, Long, String, WidgetStyle) -> Unit = onStyleChange@{ widgetId, configId, newContent, newStyle ->
            val isSyncToDesktop = widgetId != -1
            val isProShapeSync = newStyle.shape != WidgetShape.RECTANGLE &&
                newStyle.shape != WidgetShape.ELLIPSE
            val needPay = isSyncToDesktop && !trialManager.isActivated() && (
                WidgetStyle.isProPreset(newStyle) || isProShapeSync || !newStyle.backgroundImagePath.isNullOrEmpty()
                )
            if (needPay) {
                showProDialog = true
                return@onStyleChange
            }
            if (widgetId != -1) {
                ReminderWidgetProvider.saveWidgetStyle(this@MainActivity, widgetId, newStyle)
            }
            val target = widgetConfigs.find { it.id == configId }
            if (target != null) {
                persistJob?.cancel()
                // 用 lifecycleScope 而非组合作用域：页面销毁时未到期的防抖写入仍能完成，避免丢失最后 400ms 的编辑
                persistJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(400)
                    withContext(Dispatchers.IO) {
                        dbHelper.updateWidgetConfig(
                            target.copy(
                                content = newContent,
                                styleJson = newStyle.toJsonString()
                            )
                        )
                        val updatedConfigs = dbHelper.getAllWidgetConfigs()
                        withContext(Dispatchers.Main) {
                            widgetConfigs = updatedConfigs
                        }
                    }
                    ReminderWidgetProvider.triggerUpdateAllWidgets(this@MainActivity)
                }
            }
        }

        if (showProDialog) {
            ProActivationDialog(
                isActivated = isActivatedState,
                onActivate = {
                    trialManager.activate()
                    isActivatedState = true
                    showProDialog = false
                    // 桌面组件上未激活时的提示位图需要重绘
                    ReminderWidgetProvider.triggerUpdateAllWidgets(this@MainActivity)
                    Toast.makeText(this@MainActivity, "🎉 PRO 已激活，全部风格已解锁！", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showProDialog = false }
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8FAFC)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // 1. 鲜明浓郁的薄荷极光渐变 (从生机深薄荷绿渐变至晴空天蓝)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF5EEAD4), // 鲜明深薄荷 (Rich Vibrant Mint)
                                    Color(0xFF38BDF8), // 晴空澄澈天蓝 (Sky Cyan)
                                    Color(0xFFBAE6FD).copy(alpha = 0.50f), // 晨露浅青
                                    Color(0xFFF8FAFC)  // 渐入清爽白
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height * 0.44f)
                            )
                        )

                        // 2. 网站同款精细方格网 (Subtle Clean Grid)
                        val gridSize = 24.dp.toPx()
                        val gridColor = Color(0xFF0F766E).copy(alpha = 0.12f)
                        val stroke = 1.dp.toPx()

                        var x = 0f
                        while (x < size.width) {
                            drawLine(
                                color = gridColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = stroke
                            )
                            x += gridSize
                        }

                        var y = 0f
                        while (y < size.height) {
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = stroke
                            )
                            y += gridSize
                        }
                    }
            ) {
                Scaffold(
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // 1. 顶部苹果风格导航栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "句阁",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF064E3B)
                                )
                                Text(
                                    text = "DeskQuotes",
                                    fontSize = 11.sp,
                                    color = Color(0xFF065F46),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            val rems = dbHelper.getAllReminders()
                                            val cats = dbHelper.getAllCategories()
                                            val configs = dbHelper.getAllWidgetConfigs()
                                            withContext(Dispatchers.Main) {
                                                reminders = rems
                                                categories = cats
                                                widgetConfigs = configs
                                            }
                                        }
                                        ReminderWidgetProvider.triggerUpdateAllWidgets(this@MainActivity)
                                        Toast.makeText(this@MainActivity, "数据已同步刷新", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .shadow(2.dp, CircleShape)
                                        .background(Color.White, CircleShape)
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)), CircleShape)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF065F46), modifier = Modifier.size(18.dp))
                                }

                                val badgeText = if (isActivatedState) "✨ PRO" else "👑 激活PRO ￥1"
                                val badgeBg = if (isActivatedState) {
                                    Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF0284C7)))
                                } else {
                                    Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(badgeBg)
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), RoundedCornerShape(20.dp))
                                        .clickable { showProDialog = true }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // 2. 上 1/3 预览区 — 高度随组件规格自适应，整体仅比原固定高度收紧约一行，
                        // 让“小组件#N”标签与页签行同步上移一行；封顶 268dp 保证 4×4 不被裁切
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(previewBoxHeightDp.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(previewBoxHeightDp.dp)
                                    .padding(top = 0.dp),
                                contentPadding = PaddingValues(horizontal = 36.dp),
                                pageSpacing = 16.dp
                            ) { page ->
                                val pageWidgetId = if (appWidgetIds.isNotEmpty()) appWidgetIds[page] else -1
                                val pageConfigId = if (pageWidgetId != -1) {
                                    ReminderWidgetProvider.getBoundConfigId(this@MainActivity, pageWidgetId)
                                } else {
                                    -1L
                                }
                                val pageConfig = if (pageConfigId != -1L) {
                                    widgetConfigs.find { it.id == pageConfigId } ?: widgetConfigs.firstOrNull()
                                } else {
                                    widgetConfigs.firstOrNull()
                                }

                                val pageStyle = if (pageWidgetId == selectedWidgetId) {
                                    currentStyle
                                } else if (pageWidgetId != -1) {
                                    ReminderWidgetProvider.getWidgetStyle(this@MainActivity, pageWidgetId, pageConfig?.styleJson)
                                } else {
                                    WidgetStyle.fromJsonString(pageConfig?.styleJson)
                                }

                                val pageContent = if (pageWidgetId == selectedWidgetId) {
                                    textContentState
                                } else {
                                    pageConfig?.content ?: "静静地，坐一会。"
                                }

                                // 复用与预览盒一致的规格计算，避免渲染高度与盒高度不一致造成裁切/空洞
                                val previewHeightDp = previewHeightForPage(page)
                                android.util.Log.d("JugeH", "shape=${pageStyle.shape} h=$previewHeightDp sizeType=${pageConfig?.sizeType}")
                                val pageBitmap by produceState<Bitmap?>(
                                    initialValue = null,
                                    pageContent, pageStyle, isActivatedState, previewHeightDp
                                ) {
                                    value = withContext(Dispatchers.Default) {
                                        try {
                                            WidgetCanvasRenderer.render(
                                                context = this@MainActivity,
                                                widthDp = 312,
                                                heightDp = previewHeightDp,
                                                content = pageContent,
                                                style = pageStyle,
                                                trialManager = trialManager,
                                                isPreview = true
                                            )
                                        } catch (t: Throwable) {
                                            // 极端情况下（如 OOM）返回一个空白位图
                                            Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(previewHeightDp.dp)
                                        .clickable { subTab = "adjust" },
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    // 用 Crossfade 让预览位图在切换风格时平滑过渡，避免颜色/形状突变造成"晃动"感
                                    androidx.compose.animation.Crossfade(
                                        targetState = pageBitmap,
                                        modifier = Modifier.fillMaxSize()
                                    ) { bmp ->
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Style Preview",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Style",
                                        tint = Color(pageStyle.fontColor).copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(16.dp)
                                            .align(Alignment.BottomEnd)
                                    )
                                }
                            }
                        }

                        // 组件标签：位于预览卡片与下方抽屉之间的空白区域，随 Pager 翻页同步
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "小组件#${pagerState.currentPage + 1} 实时设计·仅为预览·以桌面组件显示为准",
                                fontSize = 12.sp,
                                color = Color(0xFF0F766E),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 下 2/3 面板容器 (苹果风格大抽屉)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(top = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .width(36.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFE2E8F0))
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // 苹果风分段控制器 Segmented Control (全动态测量)
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .height(42.dp)
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                        .padding(3.dp)
                                ) {
                                    val width = maxWidth
                                    val isLibrarySelected = subTab == "library"
                                    val indicatorWidth = width / 2

                                    // 无动效：指示器位置随 Tab 即时切换（无过渡动画）
                                    val selectedOffset = if (isLibrarySelected) 0.dp else indicatorWidth

                                    // 背景滑块：仅静态到位，不含隐式/显式位移动画
                                    Box(
                                        modifier = Modifier
                                            .offset(x = selectedOffset)
                                            .width(indicatorWidth)
                                            .fillMaxHeight()
                                            .shadow(2.dp, RoundedCornerShape(10.dp))
                                            .background(Color.White, RoundedCornerShape(10.dp))
                                    )

                                    // Tab 按钮文字
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable { subTab = "library" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "📜 跃然纸上",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLibrarySelected) Color(0xFF0F766E) else Color(0xFF64748B)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable { subTab = "adjust" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "⚙️ 个性定制",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (!isLibrarySelected) Color(0xFF0F766E) else Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // 内容区
                                if (subTab == "library") {
                                    LibraryTabContent(
                                        categories = categories,
                                        reminders = reminders,
                                        selectedCategory = selectedCategory,
                                        editingReminder = editingReminderForContent,
                                        onEditReminderRequest = { editingReminderForContent = it },
                                        onCategorySelect = { selectedCategory = it },
                                        onAddCategory = { name ->
                                            scope.launch(Dispatchers.IO) {
                                                dbHelper.insertCategory(name)
                                                val cats = dbHelper.getAllCategories()
                                                withContext(Dispatchers.Main) {
                                                    categories = cats
                                                }
                                            }
                                        },
                                        onDeleteCategory = { id ->
                                            scope.launch(Dispatchers.IO) {
                                                dbHelper.deleteCategory(id)
                                                val cats = dbHelper.getAllCategories()
                                                val rems = dbHelper.getAllReminders()
                                                withContext(Dispatchers.Main) {
                                                    categories = cats
                                                    reminders = rems
                                                }
                                            }
                                        },
                                        onRenameCategory = { id, name ->
                                            scope.launch(Dispatchers.IO) {
                                                dbHelper.renameCategory(id, name)
                                                val cats = dbHelper.getAllCategories()
                                                withContext(Dispatchers.Main) {
                                                    categories = cats
                                                }
                                            }
                                        },
                                        onSwapCategoryOrder = { cat1Id, cat1Order, cat2Id, cat2Order ->
                                            scope.launch(Dispatchers.IO) {
                                                dbHelper.swapCategoryOrder(cat1Id, cat1Order, cat2Id, cat2Order)
                                                val cats = dbHelper.getAllCategories()
                                                withContext(Dispatchers.Main) {
                                                    categories = cats
                                                }
                                            }
                                        },
                                        onAddReminder = { content, catId ->
                                            scope.launch(Dispatchers.IO) {
                                                dbHelper.insertReminder(content, catId)
                                                val rems = dbHelper.getAllReminders()
                                                withContext(Dispatchers.Main) {
                                                    reminders = rems
                                                }
                                            }
                                        },
                                        onEditReminderSave = { id, content, catId ->
                                            scope.launch(Dispatchers.IO) {
                                                val rem = reminders.find { it.id == id }
                                                if (rem != null) {
                                                    dbHelper.updateReminder(id, content, catId, rem.isFavorite, rem.styleJson)
                                                    val rems = dbHelper.getAllReminders()
                                                    withContext(Dispatchers.Main) {
                                                        reminders = rems
                                                    }
                                                }
                                            }
                                        },
                                        onDeleteReminder = { id ->
                                            scope.launch(Dispatchers.IO) {
                                                dbHelper.deleteReminder(id)
                                                val rems = dbHelper.getAllReminders()
                                                withContext(Dispatchers.Main) {
                                                    reminders = rems
                                                }
                                            }
                                        },
                                        onReminderClick = { reminder ->
                                            textContentState = reminder.content
                                            if (selectedWidgetId != -1) {
                                                // 绑定可能新建 widget_config 并写入新绑定关系，
                                                // 必须在绑定完成后重新读取配置 ID，否则下方保存会命中旧的 -1 配置
                                                val widgetId = selectedWidgetId
                                                scope.launch(Dispatchers.IO) {
                                                    ReminderWidgetProvider.bindReminderToWidget(this@MainActivity, widgetId, reminder.id)
                                                    val newConfigId = ReminderWidgetProvider.getBoundConfigId(this@MainActivity, widgetId)
                                                    val newConfig = dbHelper.getWidgetConfigById(newConfigId)
                                                    if (newConfig != null) {
                                                        dbHelper.updateWidgetConfig(
                                                            newConfig.copy(
                                                                content = reminder.content,
                                                                styleJson = currentStyle.toJsonString()
                                                            )
                                                        )
                                                    }
                                                    val configs = dbHelper.getAllWidgetConfigs()
                                                    withContext(Dispatchers.Main) {
                                                        widgetConfigs = configs
                                                        textContentState = reminder.content
                                                    }
                                                    ReminderWidgetProvider.triggerUpdateAllWidgets(this@MainActivity)
                                                }
                                            } else {
                                                onStyleChange(selectedWidgetId, selectedConfigId, reminder.content, currentStyle)
                                            }
                                        }
                                    )
                                } else {
                                    AdjustTabContent(
                                        widgetConfigs = widgetConfigs,
                                        isActivated = isActivatedState,
                                        selectedWidgetId = selectedWidgetId,
                                        selectedReminderId = selectedReminder?.id ?: -1L,
                                        selectedStyle = currentStyle,
                                        selectedContent = textContentState,
                                        onStyleStateChange = { currentStyle = it },
                                        onContentStateChange = { textContentState = it },
                                        onSelectPreset = { wId, rId, newStyle ->
                                            currentStyle = newStyle
                                            onStyleChange(wId, rId, textContentState, newStyle)
                                        },
                                        onStyleChange = { wId, rId, text, newStyle ->
                                            onStyleChange(wId, rId, text, newStyle)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ProActivationDialog(
        isActivated: Boolean,
        onActivate: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("👑 PRO 会员", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                    Text("￥1 一次性买断 · 永久有效", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "解锁全部内置卡片风格，后续新增免费更新",
                            "自定义背景图、字体、颜色与形状全部开放",
                            "多组件独立配置，一次激活永久保留"
                        ).forEach { benefit ->
                            Text("✓ $benefit", fontSize = 12.sp, color = Color(0xFF4B5563))
                        }
                    }

                    if (isActivated) {
                        Text("✨ 您已是 PRO 会员，感谢支持！", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .shadow(2.dp, RoundedCornerShape(12.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF0284C7))), RoundedCornerShape(12.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("好的", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("完成支付后，点击下方按钮立即完成激活。", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                        Button(
                            onClick = onActivate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .shadow(4.dp, RoundedCornerShape(12.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF0284C7))), RoundedCornerShape(12.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("我已支付，立即激活", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        TextButton(onClick = onDismiss) {
                            Text("暂不需要", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // --- 子Tab：我的文库内容 ---
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LibraryTabContent(
        categories: List<Category>,
        reminders: List<Reminder>,
        selectedCategory: Category?,
        editingReminder: Reminder?,
        onEditReminderRequest: (Reminder?) -> Unit,
        onCategorySelect: (Category?) -> Unit,
        onAddCategory: (String) -> Unit,
        onDeleteCategory: (Long) -> Unit,
        onRenameCategory: (Long, String) -> Unit,
        onSwapCategoryOrder: (Long, Int, Long, Int) -> Unit,
        onAddReminder: (String, Long) -> Unit,
        onEditReminderSave: (Long, String, Long) -> Unit,
        onDeleteReminder: (Long) -> Unit,
        onReminderClick: (Reminder) -> Unit
    ) {
        var showAddCategoryDialog by remember { mutableStateOf(false) }
        var showAddReminderDialog by remember { mutableStateOf(false) }
        var newCategoryName by remember { mutableStateOf("") }
        var newReminderContent by remember { mutableStateOf("") }
        var selectedCatForReminder by remember { mutableStateOf<Category?>(categories.firstOrNull()) }

        // 纯文本及分类修改对话框的控制状态
        var editContentField by remember { mutableStateOf("") }
        var deletingCategory by remember { mutableStateOf<Category?>(null) }
        var editCategorySelection by remember { mutableStateOf<Category?>(null) }

        // 分类管理中心对话框控制状态
        var showCategoryManagerDialog by remember { mutableStateOf(false) }
        var renamingCategory by remember { mutableStateOf<Category?>(null) }
        var renameCategoryNewName by remember { mutableStateOf("") }

        LaunchedEffect(editingReminder) {
            if (editingReminder != null) {
                editContentField = editingReminder.content
                editCategorySelection = categories.find { it.id == editingReminder.categoryId }
            }
        }

        val filteredReminders = if (selectedCategory == null) {
            reminders
        } else {
            reminders.filter { it.categoryId == selectedCategory.id }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onCategorySelect(null) },
                            label = { Text("全部", fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color(0xFF64748B),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF1F5F9),
                                selectedContainerColor = Color(0xFF0F766E)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategory == null,
                                borderColor = Color(0xFFE2E8F0),
                                selectedBorderColor = Color(0xFF0F766E)
                            )
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory?.id == cat.id,
                            onClick = { onCategorySelect(cat) },
                            label = { Text(cat.name, fontWeight = if (selectedCategory?.id == cat.id) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color(0xFF64748B),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF1F5F9),
                                selectedContainerColor = Color(0xFF0F766E)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategory?.id == cat.id,
                                borderColor = Color(0xFFE2E8F0),
                                selectedBorderColor = Color(0xFF0F766E)
                            ),
                            modifier = Modifier.pointerInput(cat) {
                                detectTapGestures(
                                    onLongPress = {
                                        deletingCategory = cat
                                    }
                                )
                            }
                        )
                    }
                    item {
                        IconButton(
                            onClick = {
                                showCategoryManagerDialog = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Manage Categories", tint = Color(0xFF64748B))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredReminders, key = { it.id }) { reminder ->
                        val catName = categories.find { it.id == reminder.categoryId }?.name ?: "普通"

                        val visibleState = remember { MutableTransitionState(false) }.apply { targetState = true }
                        AnimatedVisibility(
                            visibleState = visibleState,
                            enter = slideInVertically(
                                animationSpec = tween(durationMillis = 400)
                            ) { it / 2 } + fadeIn(
                                animationSpec = tween(durationMillis = 400)
                            )
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onReminderClick(reminder)
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFCCFBF1)
                                        ) {
                                            Text(
                                                text = catName,
                                                fontSize = 11.sp,
                                                color = Color(0xFF0F766E),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Reminder",
                                                tint = Color(0xFF8A9A91),
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable {
                                                        onEditReminderRequest(reminder)
                                                    }
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable { onDeleteReminder(reminder.id) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = reminder.content,
                                        fontSize = 15.sp,
                                        color = Color(0xFF19241E),
                                        lineHeight = 23.sp
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .height(50.dp)
                    .shadow(6.dp, RoundedCornerShape(25.dp))
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF059669), Color(0xFF0284C7))
                        )
                    )
                    .clickable {
                        if (categories.isEmpty()) {
                            Toast.makeText(this@MainActivity, "请先创建一个分类目录", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedCatForReminder = categories.first()
                            showAddReminderDialog = true
                        }
                    }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("录入新句", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                }
            }
        }

        // --- 全局统一：高级暗黑系 Compose Dialog 自定义弹窗 ---

        // 1. 新建分类弹窗
        if (showAddCategoryDialog) {
            Dialog(
                onDismissRequest = { showAddCategoryDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderBlue)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("新建分类目录", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textWhite)
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            placeholder = { Text("输入分类名称，如“电影”", color = Color(0xFF94A3B8)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textWhite,
                                unfocusedTextColor = textWhite,
                                focusedBorderColor = accentBlue,
                                unfocusedBorderColor = borderBlue,
                                cursorColor = accentBlue
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showAddCategoryDialog = false }) {
                                Text("取消", color = Color(0xFF94A3B8))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newCategoryName.isNotBlank()) {
                                        onAddCategory(newCategoryName)
                                        newCategoryName = ""
                                        showAddCategoryDialog = false
                                    }
                                },
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(8.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF0284C7))), RoundedCornerShape(8.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("确认", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. 新增句阁记录弹窗
        if (showAddReminderDialog) {
            Dialog(
                onDismissRequest = { showAddReminderDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderBlue)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                         Text("新增记录到句阁", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textWhite)
                        
                        // 放大文本输入框，设定固定高度以显示更多内容
                        OutlinedTextField(
                            value = newReminderContent,
                            onValueChange = { newReminderContent = it },
                            placeholder = { Text("输入金句或提醒文字", color = textGray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textWhite,
                                unfocusedTextColor = textWhite,
                                focusedBorderColor = accentBlue,
                                unfocusedBorderColor = borderBlue,
                                cursorColor = accentBlue
                            )
                        )
                        
                        // 分类标题行并排集成“+ 新建目录”按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("归类目录:", color = Color(0xFF1F2937), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "+ 新建目录",
                                fontSize = 12.sp,
                                color = Color(0xFF0F766E),
                                modifier = Modifier
                                    .clickable {
                                        showAddReminderDialog = false
                                        showAddCategoryDialog = true
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        }

                        // 压缩单选行高的归类列表
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            categories.forEach { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { selectedCatForReminder = cat }
                                        .background(if (selectedCatForReminder?.id == cat.id) Color(0xFFC6FBF3) else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedCatForReminder?.id == cat.id,
                                        onClick = { selectedCatForReminder = cat },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = cat.name, color = textWhite, fontSize = 13.sp)
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showAddReminderDialog = false }) {
                                Text("取消", color = textGray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newReminderContent.isNotBlank() && selectedCatForReminder != null) {
                                        onAddReminder(newReminderContent, selectedCatForReminder!!.id)
                                        newReminderContent = ""
                                        showAddReminderDialog = false
                                    }
                                },
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(8.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF0284C7))), RoundedCornerShape(8.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("确认", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. 点击金句卡片后：弹出的纯粹文字与分类编辑弹窗
        if (editingReminder != null) {
            Dialog(
                onDismissRequest = { onEditReminderRequest(null) },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                         Text("编辑金句内容", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                        
                        // 放大文本输入框
                        OutlinedTextField(
                            value = editContentField,
                            onValueChange = { editContentField = it },
                            label = { Text("金句内容") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                 focusedTextColor = Color(0xFF1F2937),
                                 unfocusedTextColor = Color(0xFF1F2937),
                                 focusedBorderColor = Color(0xFF0F766E),
                                 unfocusedBorderColor = Color(0xFFE5E7EB),
                                 focusedLabelColor = Color(0xFF0F766E),
                                 unfocusedLabelColor = Color(0xFF6B7280),
                                 cursorColor = Color(0xFF0F766E)
                             )
                        )
                        
                        // 归类并排集成“+ 新建目录”按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("归类目录:", color = Color(0xFF1F2937), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "+ 新建目录",
                                fontSize = 12.sp,
                                color = Color(0xFF0F766E),
                                modifier = Modifier
                                    .clickable {
                                        onEditReminderRequest(null) // 先关掉编辑框
                                        showAddCategoryDialog = true // 开启新建分类
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        }

                        // 压缩间距的分类列表
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            categories.forEach { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { editCategorySelection = cat }
                                        .background(if (editCategorySelection?.id == cat.id) Color(0xFFCBFBEF) else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = editCategorySelection?.id == cat.id,
                                        onClick = { editCategorySelection = cat },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = cat.name, color = Color(0xFF1F2937), fontSize = 13.sp)
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onEditReminderRequest(null) }) {
                                 Text("取消", color = Color(0xFF6B7280))
                             }
                             Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (editContentField.isNotBlank() && editCategorySelection != null) {
                                        onEditReminderSave(editingReminder.id, editContentField, editCategorySelection!!.id)
                                    }
                                },
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(8.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF0284C7))), RoundedCornerShape(8.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("保存并更新", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 5. 分类管理中心弹窗
        if (showCategoryManagerDialog) {
            Dialog(
                onDismissRequest = { showCategoryManagerDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("文库分类管理中心", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                            Text(
                                text = "< 关闭",
                                fontSize = 13.sp,
                                color = Color(0xFF0F766E),
                                modifier = Modifier.clickable { showCategoryManagerDialog = false }
                            )
                        }

                        HorizontalDivider(color = Color(0xFFE5E7EB))

                        // 分类项列表 (支持滑动，高度自适应)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories.size) { index ->
                                val cat = categories[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF3F4F6), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = cat.name,
                                        color = Color(0xFF1F2937),
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 排序：上移按钮
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val prevCat = categories[index - 1]
                                                    onSwapCategoryOrder(cat.id, cat.sortOrder, prevCat.id, prevCat.sortOrder)
                                                }
                                            },
                                            modifier = Modifier.size(28.dp),
                                            enabled = index > 0
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Up",
                                                tint = if (index > 0) Color(0xFF1F2937) else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // 排序：下移按钮
                                        IconButton(
                                            onClick = {
                                                if (index < categories.size - 1) {
                                                    val nextCat = categories[index + 1]
                                                    onSwapCategoryOrder(cat.id, cat.sortOrder, nextCat.id, nextCat.sortOrder)
                                                }
                                            },
                                            modifier = Modifier.size(28.dp),
                                            enabled = index < categories.size - 1
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Down",
                                                tint = if (index < categories.size - 1) Color(0xFF1F2937) else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // 重命名按钮
                                        IconButton(
                                            onClick = {
                                                renamingCategory = cat
                                                renameCategoryNewName = cat.name
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                tint = Color(0xFF0F766E),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // 删除按钮 (移入设置中心)
                                        IconButton(
                                            onClick = {
                                                deletingCategory = cat
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE5E7EB))

                        // 底部 inline 新建分类输入区
                        var inlineNewCatName by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inlineNewCatName,
                                onValueChange = { inlineNewCatName = it },
                                placeholder = { Text("新建分类名称", color = Color(0xFF6B7280)) },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF1F2937),
                                    unfocusedTextColor = Color(0xFF1F2937),
                                    focusedBorderColor = Color(0xFF0F766E),
                                    unfocusedBorderColor = Color(0xFFE5E7EB),
                                    focusedLabelColor = Color(0xFF0F766E),
                                    unfocusedLabelColor = Color(0xFF6B7280),
                                    cursorColor = Color(0xFF0F766E)
                                )
                            )
                            Button(
                                onClick = {
                                    if (inlineNewCatName.isNotBlank()) {
                                        onAddCategory(inlineNewCatName)
                                        inlineNewCatName = ""
                                    }
                                },
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(8.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF0284C7))), RoundedCornerShape(8.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("添加", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 6. 重命名分类子 Dialog
        if (renamingCategory != null) {
            Dialog(
                onDismissRequest = { renamingCategory = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("修改分类目录名称", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                        OutlinedTextField(
                            value = renameCategoryNewName,
                            onValueChange = { renameCategoryNewName = it },
                            placeholder = { Text("输入新分类名称", color = Color(0xFF6B7280)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1F2937),
                                unfocusedTextColor = Color(0xFF1F2937),
                                focusedBorderColor = Color(0xFF0F766E),
                                unfocusedBorderColor = Color(0xFFE5E7EB),
                                focusedLabelColor = Color(0xFF0F766E),
                                unfocusedLabelColor = Color(0xFF6B7280),
                                cursorColor = Color(0xFF0F766E)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { renamingCategory = null }) {
                                Text("取消", color = Color(0xFF6B7280))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (renameCategoryNewName.isNotBlank()) {
                                        onRenameCategory(renamingCategory!!.id, renameCategoryNewName)
                                        renamingCategory = null
                                    }
                                },
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(8.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF0284C7))), RoundedCornerShape(8.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("保存", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 4. 二次确认删除分类目录弹窗 (防误触高级 Dialog)
        if (deletingCategory != null) {
            Dialog(
                onDismissRequest = { deletingCategory = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("确定要删除分类目录吗？", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                        Text(
                            text = "确定要删除“${deletingCategory!!.name}”吗？这将同时清空该目录下的所有诗词金句，此操作不可恢复。",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280),
                            lineHeight = 18.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { deletingCategory = null }) {
                                Text("取消", color = Color(0xFF6B7280))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onDeleteCategory(deletingCategory!!.id)
                                    deletingCategory = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)) // 危险操作使用亮红色按钮
                            ) {
                                Text("删除并清空", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

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
        onStyleChange: (Int, Long, String, WidgetStyle) -> Unit
    ) {
        val scope = rememberCoroutineScope()
        val appWidgetIds = remember {
            ReminderWidgetProvider.getAllAppWidgetIds(this@MainActivity)
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

        // 折叠控制
        var isTutorialExpanded by remember { mutableStateOf(false) }

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
                            Text(
                                text = "📱 桌面组件添加教程",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
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
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Spacer(modifier = Modifier.height(10.dp))

                                Text("💡 常见国产手机添加与权限教程：", fontSize = 13.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• 华为 / 鸿蒙系统：在桌面双指捏合 -> 选择“服务卡片” -> 滑动到最下方选择“窗口小工具” -> 找到“句阁 / DeskQuotes”拖动到桌面。\n" +
                                           "• 小米 / HyperOS / MIUI：桌面双指捏合 -> 点击“添加小部件” -> 搜索“句阁”或滑动选择安卓原生小部件添加。\n" +
                                           "• OPPO / VIVO：桌面双指捏合 -> 点击“卡片 / 插件” -> 选择“句阁”添加。\n" +
                                           "• 无法弹出快捷面板？：请确保在手机的“应用设置 -> 权限管理”中，为本应用开启了【后台弹出界面】和【悬浮窗】权限，防止系统拦截快捷面板 Activity 的启动。",
                                    fontSize = 13.sp,
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
                        Text(
                            text = "✍️ 文本内容与字形定制",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )

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
                                    label = { Text(fnt.displayName, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        labelColor = Color(0xFF64748B),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFFF1F5F9),
                                        selectedContainerColor = Color(0xFF0F766E)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selectedStyle.font == fnt,
                                        borderColor = Color(0xFFE2E8F0),
                                        selectedBorderColor = Color(0xFF0F766E)
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
                        Slider(
                            value = selectedStyle.fontSizeSp,
                            onValueChange = {
                                val newStyle = selectedStyle.copy(fontSizeSp = it)
                                onStyleStateChange(newStyle)
                                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                            },
                            valueRange = 12.0f..48.0f,
                            modifier = Modifier.fillMaxWidth().height(24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF0F766E),
                                activeTrackColor = Color(0xFF0F766E),
                                inactiveTrackColor = Color(0xFFE2E8F0)
                            )
                        )

                        // 加粗与倾斜 Checkbox
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selectedStyle.fontBold,
                                    onCheckedChange = {
                                        val newStyle = selectedStyle.copy(fontBold = it)
                                        onStyleStateChange(newStyle)
                                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF0F766E),
                                        uncheckedColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("加粗", fontSize = 12.sp, color = Color(0xFF1E293B))
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selectedStyle.fontItalic,
                                    onCheckedChange = {
                                        val newStyle = selectedStyle.copy(fontItalic = it)
                                        onStyleStateChange(newStyle)
                                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF0F766E),
                                        uncheckedColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("倾斜", fontSize = 12.sp, color = Color(0xFF1E293B))
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selectedStyle.shadow.enabled,
                                    onCheckedChange = {
                                        // 统一阴影设置：模糊度 1，水平/垂直偏移 0，颜色跟随字体颜色
                                        val newStyle = selectedStyle.copy(
                                            shadow = selectedStyle.shadow.copy(
                                                enabled = it,
                                                color = selectedStyle.fontColor,
                                                radius = 1f,
                                                dx = 0f,
                                                dy = 0f
                                            )
                                        )
                                        onStyleStateChange(newStyle)
                                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF0F766E),
                                        uncheckedColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("文字阴影", fontSize = 12.sp, color = Color(0xFF1E293B))
                            }
                        }

                        // 文字对齐方式
                        Text("对齐方式", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
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
                                    selected = selectedStyle.textAlign.uppercase(java.util.Locale.ROOT) == alignKey,
                                    onClick = {
                                        val newStyle = selectedStyle.copy(textAlign = alignKey)
                                        onStyleStateChange(newStyle)
                                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        labelColor = Color(0xFF64748B),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFFF1F5F9),
                                        selectedContainerColor = Color(0xFF0F766E)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selectedStyle.textAlign.uppercase(java.util.Locale.ROOT) == alignKey,
                                        borderColor = Color(0xFFE2E8F0),
                                        selectedBorderColor = Color(0xFF0F766E)
                                    )
                                )
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
                        Text(
                            text = "🎨 色彩与质感艺术",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )

                        // 4.1. 字体颜色设置
                        Text("字体颜色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val presetFontColors = listOf(
                                "#434446",
                                "#1393cf",
                                "#23c3c0",
                                "#f6c250",
                                "#56309f",
                                "#aa6790"
                            )
                            presetFontColors.forEach { hex ->
                                val parsedColor = android.graphics.Color.parseColor(hex)
                                val isSelected = selectedStyle.fontColor == parsedColor
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(parsedColor))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF0F766E) else Color(0xFFE2E8F0),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            val newStyle = selectedStyle.copy(fontColor = parsedColor)
                                            onStyleStateChange(newStyle)
                                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                        }
                                )
                            }
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
                                val hsv = remember(selectedStyle.fontColor) {
                                    val arr = FloatArray(3)
                                    android.graphics.Color.colorToHSV(selectedStyle.fontColor, arr)
                                    arr
                                }
                                var hue by remember(selectedStyle.fontColor) { mutableStateOf(hsv[0]) }
                                var saturation by remember(selectedStyle.fontColor) { mutableStateOf(hsv[1]) }
                                var value by remember(selectedStyle.fontColor) { mutableStateOf(hsv[2]) }

                                // 字体色调
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().height(28.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                                    )
                                                )
                                            )
                                    )
                                    Slider(
                                        value = hue,
                                        onValueChange = {
                                            hue = it
                                            val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                                            val newStyle = selectedStyle.copy(fontColor = newColor)
                                            onStyleStateChange(newStyle)
                                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                        },
                                        valueRange = 0f..360f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.Transparent,
                                            inactiveTrackColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // 字体亮度
                                val baseHueColor = remember(hue) {
                                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().height(28.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(Color.Black, baseHueColor, Color.White)
                                                )
                                            )
                                    )
                                    Slider(
                                        value = value,
                                        onValueChange = {
                                            value = it
                                            saturation = if (it < 0.5f) 1f else (1f - (it - 0.5f) * 2f).coerceIn(0f, 1f)
                                            val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                                            val newStyle = selectedStyle.copy(fontColor = newColor)
                                            onStyleStateChange(newStyle)
                                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                        },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.Transparent,
                                            inactiveTrackColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // 字体颜色预览框
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(selectedStyle.fontColor), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(6.dp))

                        // 4.2. 卡片背景颜色设置
                        Text("小组件背景颜色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        
                        // 预设背景色彩
                        val presetColors = listOf(
                            "#F5F5F5" to "极简灰",
                            "#FFFFFF" to "纯白",
                            "#121212" to "极简黑",
                            "#F4ECD8" to "宣纸杏",
                            "#FFFDE7" to "手账黄",
                            "#FFEBEE" to "莫兰迪粉",
                            "#F3E5F5" to "淡雅紫",
                            "#E3F2FD" to "清新蓝",
                            "#E8F5E9" to "极简绿",
                            "#FFF3E0" to "暖橙橘",
                            "#263238" to "深空灰",
                            "#1F2436" to "漫画蓝"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            presetColors.forEach { (hex, name) ->
                                val colorInt = android.graphics.Color.parseColor(hex)
                                val isSelected = selectedStyle.backgroundColor == colorInt
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF0F766E) else Color(0xFFE2E8F0),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            val newStyle = selectedStyle.copy(backgroundColor = colorInt)
                                            onStyleStateChange(newStyle)
                                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                        }
                                )
                            }
                        }

                        // 自定义背景色调 HSV 滑块
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val bgHsv = remember(selectedStyle.backgroundColor) {
                                    val arr = FloatArray(3)
                                    android.graphics.Color.colorToHSV(selectedStyle.backgroundColor, arr)
                                    arr
                                }
                                var bgHue by remember(selectedStyle.backgroundColor) { mutableStateOf(bgHsv[0]) }
                                var bgSaturation by remember(selectedStyle.backgroundColor) { mutableStateOf(bgHsv[1]) }
                                var bgValue by remember(selectedStyle.backgroundColor) { mutableStateOf(bgHsv[2]) }

                                // 背景色调色环
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().height(28.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                                    )
                                                )
                                            )
                                    )
                                    Slider(
                                        value = bgHue,
                                        onValueChange = {
                                            bgHue = it
                                            val newColor = android.graphics.Color.HSVToColor(floatArrayOf(bgHue, bgSaturation, bgValue))
                                            val newStyle = selectedStyle.copy(backgroundColor = newColor)
                                            onStyleStateChange(newStyle)
                                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                        },
                                        valueRange = 0f..360f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.Transparent,
                                            inactiveTrackColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // 背景亮度
                                val bgBaseHueColor = remember(bgHue) {
                                    Color(android.graphics.Color.HSVToColor(floatArrayOf(bgHue, 1f, 1f)))
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().height(28.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(Color.Black, bgBaseHueColor, Color.White)
                                                )
                                            )
                                    )
                                    Slider(
                                        value = bgValue,
                                        onValueChange = {
                                            bgValue = it
                                            bgSaturation = if (it < 0.5f) 1f else (1f - (it - 0.5f) * 2f).coerceIn(0f, 1f)
                                            val newColor = android.graphics.Color.HSVToColor(floatArrayOf(bgHue, bgSaturation, bgValue))
                                            val newStyle = selectedStyle.copy(backgroundColor = newColor)
                                            onStyleStateChange(newStyle)
                                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                        },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.Transparent,
                                            inactiveTrackColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // 背景颜色预览框
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(selectedStyle.backgroundColor), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            )
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
                        Text(
                            text = "🖼 物理形状与背景材质",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )



                        // 背景圆角尺寸 Slider (适用于卡片类形状)
                        // 无论形状是否可调圆角都常驻渲染，避免切换形状时控件移除导致列表高度突变跳动（“页面自动上滑”）
                        val canAdjustCorner = selectedStyle.shape != WidgetShape.ELLIPSE &&
                                              selectedStyle.shape != WidgetShape.TORN_PAPER
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
                        Slider(
                            value = selectedStyle.cornerRadiusDp,
                            onValueChange = {
                                val newStyle = selectedStyle.copy(cornerRadiusDp = it)
                                onStyleStateChange(newStyle)
                                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                            },
                            valueRange = 0.0f..30.0f,
                            steps = 29,
                            enabled = canAdjustCorner,
                            modifier = Modifier.fillMaxWidth().height(24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF0F766E),
                                activeTrackColor = Color(0xFF0F766E),
                                inactiveTrackColor = Color(0xFFE2E8F0),
                                disabledThumbColor = Color(0xFFCBD5E1),
                                disabledInactiveTrackColor = Color(0xFFE2E8F0)
                            )
                        )

                        // 背景不透明度 Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("背景不透明度: ${(selectedStyle.backgroundOpacity * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        }
                        Slider(
                            value = selectedStyle.backgroundOpacity,
                            onValueChange = {
                                val newStyle = selectedStyle.copy(backgroundOpacity = it)
                                onStyleStateChange(newStyle)
                                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                            },
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier.fillMaxWidth().height(24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF0F766E),
                                activeTrackColor = Color(0xFF0F766E),
                                inactiveTrackColor = Color(0xFFE2E8F0)
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(6.dp))

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
                                            Toast.makeText(this@MainActivity, "👑 上传自定义背景图为 PRO 专属功能，请先激活！", Toast.LENGTH_SHORT).show()
                                        } else {
                                            selectImageLauncher.launch("image/*")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+ 选择本地图片", color = Color.White, fontSize = 12.sp)
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
                                            selectedContainerColor = Color(0xFF0F766E)
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selectedStyle.bgImageScaleMode == mode,
                                            borderColor = Color(0xFFE2E8F0),
                                            selectedBorderColor = Color(0xFF0F766E)
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(6.dp))

                        // 风格预设区：按分类展示（经典风格 → 萌宠风格 → 明信片风格）
                        val widgetSizeStr = ReminderWidgetProvider.getWidgetSizeString(this@MainActivity, selectedWidgetId)
                        // 分类标题尾部统一附加默认卡片尺寸，如 4×2 / 4×4
                        val sizeLabel = widgetSizeStr.replace("x", "×").replace("*", "×")
                        Text("推荐风格套用 (当前小组件大小: $widgetSizeStr)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(6.dp))

                        // 复用：单个预设横滑列表
                        @Composable
                        fun PresetRow(presets: List<Pair<String, WidgetStyle>>, title: String?) {
                            if (title != null) {
                                // 标题后附加"会员专属"角标（分类含会员功能时）
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
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
                                        initialValue = null,
                                        preset, presetName, isActivated
                                    ) {
                                        value = withContext(Dispatchers.Default) {
                                            try {
                                                WidgetCanvasRenderer.render(
                                                    context = this@MainActivity,
                                                    widthDp = 150,
                                                    heightDp = if (preset.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) 60 else 80,
                                                    content = presetName,
                                                    style = preset,
                                                    trialManager = trialManager,
                                                    isPreview = true
                                                )
                                            } catch (t: Throwable) {
                                                Bitmap.createBitmap(150, 80, Bitmap.Config.ARGB_8888)
                                            }
                                        }
                                    }

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
                                                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                                            )
                                        }

                                        // 去掉卡片右上角 PRO 徽标，改为在分类标题后统一标注"会员专属"
                                    }
                                }
                            }
                        }

                        // 1. 经典风格（除纯色圆角外为会员专属）
                        PresetRow(WidgetStyle.CLASSIC_PRESETS, "经典风格 · 会员专属 · $sizeLabel")
                        Spacer(modifier = Modifier.height(6.dp))
                        // 2. 萌宠风格（位于经典与明信片之间）
                        PresetRow(WidgetStyle.PET_PRESETS, "萌宠风格 · 会员专属 · $sizeLabel")

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(4.dp))

                        // 精选卡片插画
                        Text("明信片风格 · 会员专属 · $sizeLabel", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val illustrations = WidgetStyle.ILLUSTRATION_PRESETS
                            
                            illustrations.forEachIndexed { illusIndex, (resName, desc) ->
                                 val matchingPreset = WidgetStyle.PRESETS.find { it.presetImageResName == resName }
                                 val targetShape = matchingPreset?.shape ?: WidgetShape.SPLIT_CARD
                                 val isSelected = selectedStyle.presetImageResName == resName && selectedStyle.shape == targetShape && selectedStyle.backgroundImagePath.isNullOrEmpty()
                                val resId = resources.getIdentifier(resName, "drawable", packageName)
                                
                                Box(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .height(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF0F766E) else Color(0xFFE2E8F0),
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
                                                    error = androidx.compose.ui.graphics.painter.ColorPainter(Color.LightGray)
                                                )
                                            } else {
                                                Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFFE2E8F0))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(0.4f)
                                                .background(Color.White),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = desc,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF0F766E) else Color(0xFF64748B)
                                            )
                                        }
                                    }
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
                        .background(Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF0284C7))))
                        .clickable {
                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, selectedStyle)
                            Toast.makeText(this@MainActivity, "✨ 样式已成功保存并同步至手机桌面！", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存并应用到桌面小组件", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        if (pendingCropUri != null) {
            val cropTarget = CropImageHelper.cropTargetForWidget(
                ReminderWidgetProvider.getWidgetSizeString(this@MainActivity, selectedWidgetId)
            )
            CropImageHelper.ImageCropDialog(
                uri = pendingCropUri!!,
                onDismiss = { pendingCropUri = null },
                onCropSuccess = { path ->
                    try {
                        selectedStyle.backgroundImagePath?.let { File(it).delete() }
                    } catch (e: Exception) {}
                    val newStyle = selectedStyle.copy(backgroundImagePath = path)
                    onStyleStateChange(newStyle)
                    onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                },
                targetWidth = cropTarget.first,
                targetHeight = cropTarget.second
            )
        }
    }
}
