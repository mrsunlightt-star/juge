package com.juge.app.ui.main

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.juge.app.CropImageHelper
import com.juge.app.ReminderWidgetProvider
import com.juge.app.WidgetCanvasRenderer
import com.juge.app.account.AccountDialog
import com.juge.app.account.AccountStore
import com.juge.app.account.AccountSync
import com.juge.app.data.Category
import com.juge.app.data.DbHelper
import com.juge.app.data.LegalDocs
import com.juge.app.data.Reminder
import com.juge.app.data.TrialManager
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetStyle
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.PreviewMetrics
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.adjust.AdjustTabContent
import com.juge.app.ui.legal.LegalDocDialog
import com.juge.app.ui.library.LibraryTabContent
import com.juge.app.ui.theme.darkBg
import com.juge.app.ui.theme.selectBlue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    dbHelper: DbHelper,
    trialManager: TrialManager,
    activity: ComponentActivity,
    startOnActivate: Boolean,
    targetEditConfigId: Long,
    startOnAdjust: Boolean = false,
    startTutorialExpanded: Boolean = false
) {
    val context: Context = activity
    // 「跃然纸上 / 个性定制」翻页状态：页签点击、预览卡片点击与内容区左右滑动共用。
    // 指示器与文字颜色都直接读 pager（currentPage 越过一半即切换），避免等落定才变色
    val subTabPagerState = rememberPagerState(initialPage = if (startOnAdjust) 1 else 0, pageCount = { 2 })
    var isActivatedState by remember { mutableStateOf(trialManager.isActivated()) }
    // 支付进行中：用于按钮置忙，避免重复拉起收银台
    var isPaying by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    // 「我的」页打开的协议全文：null / "user" / "privacy"
    var showLegalDoc by remember { mutableStateOf<String?>(null) }
    var accountName by remember { mutableStateOf(AccountStore.snapshot(context)?.displayName) }

    // 上次支付被中断（App 被杀 / 切后台）时复查留存订单，兜底「已付款但没激活成功」
    LaunchedEffect(Unit) {
        if (!trialManager.isActivated() &&
            ProPurchase.recoverPending(context) is ProPurchase.Outcome.Paid
        ) {
            trialManager.activate(TrialManager.PAY_METHOD_ALIPAY)
            isActivatedState = true
            ReminderWidgetProvider.triggerUpdateAllWidgets(context)
            Toast.makeText(context, "🎉 已找回支付订单，PRO 激活成功！", Toast.LENGTH_SHORT).show()
        }
    }

    // 已登录时用服务端结论回灌本地 PRO：换机、重装后这是唯一的找回入口。
    // 未登录或网络不可用时静默跳过，不影响本地任何功能。
    LaunchedEffect(Unit) {
        accountName = AccountStore.snapshot(context)?.displayName
        when (val outcome = AccountSync.refresh(context)) {
            is AccountSync.Outcome.ServerSays -> {
                if (outcome.pro && !trialManager.isActivated()) {
                    trialManager.activate(TrialManager.PAY_METHOD_ACCOUNT)
                    isActivatedState = true
                    ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                    Toast.makeText(context, "🎉 已通过账号找回 PRO，全部风格已解锁！", Toast.LENGTH_SHORT).show()
                } else if (!outcome.pro && trialManager.isActivated()) {
                    // 服务端明确说这个账号不是 PRO（盗号、共享账号、退款等），才撤销本地激活，
                    // 否则激活标记会在本机永久残留。两个例外必须保留：
                    //   1. 本机直接购买（payMethod=支付宝）——钱是这台机器付的，与账号状态无关；
                    //   2. 开发开关——保留调试能力。
                    val grantedByAccount =
                        trialManager.activationRecord()?.payMethod == TrialManager.PAY_METHOD_ACCOUNT
                    if (grantedByAccount) {
                        trialManager.resetActivation()
                        isActivatedState = false
                        ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                        Toast.makeText(context, "该账号的 PRO 已失效，已切换回免费版", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            // 未登录 / 没问成：保持本地状态不变
            AccountSync.Outcome.NotLoggedIn, AccountSync.Outcome.Unavailable -> Unit
        }
    }

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
        ReminderWidgetProvider.getAllAppWidgetIds(context)
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
            ReminderWidgetProvider.getBoundConfigId(context, selectedWidgetId)
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
                ReminderWidgetProvider.getWidgetStyle(context, selectedWidgetId, selectedConfig?.styleJson)
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
        val cid = if (wid != -1) ReminderWidgetProvider.getBoundConfigId(context, wid) else -1L
        val cfg = if (cid != -1L) {
            widgetConfigs.find { it.id == cid } ?: widgetConfigs.firstOrNull()
        } else {
            widgetConfigs.firstOrNull()
        }
        val shape = if (wid == selectedWidgetId) currentStyle.shape
            else if (wid != -1) ReminderWidgetProvider.getWidgetStyle(context, wid, cfg?.styleJson).shape
            else WidgetStyle.fromJsonString(cfg?.styleJson).shape
        // 行数一律以**系统上报的实时尺寸**为准，不读库里的 sizeType。
        // 原因：sizeType 只在新建配置那一刻写一次（ReminderWidgetProvider:177），
        // 之后用户在桌面拉伸组件、或者组件被桌面重新分配格子，库里那份都不更新，
        // 于是 4×2 的组件会一直按陈旧的 "4x3"/"4x4" 渲染，预览被拉成近正方形。
        // getWidgetSizeString 读的是 OPTION_APPWIDGET_MIN_WIDTH/HEIGHT，是当前真实落位。
        val liveSpanY = if (wid != -1) {
            ReminderWidgetProvider.getWidgetSizeString(context, wid)
                .split("*").getOrNull(1)?.toIntOrNull()
        } else null
        // 无桌面组件可问（wid == -1，App 内预览的常态）时，**按声明的默认落位 4×2 兜底**，
        // 不读数据库里的 sizeType —— 它是建配置那一刻写死的（默认 "4x3"），之后永不更新，
        // 桌面组件撤销后拿它算高度，4×2 的预览就会被按 3 行拉成近正方形。
        // 高度映射本身是纯函数，已抽到 PreviewMetrics 并有单测覆盖
        // （下限 180dp、4×4 上限 268dp、横向分割卡固定 130dp 等规则都在那边）。
        return PreviewMetrics.previewHeightDp(shape, liveSpanY)
    }
    // 放在 currentStyle 与 previewHeightForPage 声明之后，保证内部引用均已初始化
    // 显示盒高度固定 244dp（用户确认的视觉高度）：预览位图按真实比例等比渲染后
    // 以 ContentScale.Fit 贴顶显示在盒内，4×2 时卡片约 155dp 高、下方自然留白。
    // 渲染比例正确的前提下，盒子只提供"预览区域"的视觉高度，不影响卡片形状。
    val previewBoxHeightDp = PreviewMetrics.previewBoxHeightDp(previewHeightForPage(pagerState.currentPage))

    var textContentState by remember(selectedConfig?.id) {
        mutableStateOf(selectedConfig?.content ?: "")
    }

    // 防抖写库任务：声明在 LaunchedEffect 之前，切换组件时可 join 等待未落库的写入完成
    var persistJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(selectedWidgetId) {
        if (selectedWidgetId != -1) {
            // 等待 400ms 防抖写入落库后再读取，避免切换组件时读到数据库旧值覆盖用户草稿
            persistJob?.join()
            val boundConfigId = ReminderWidgetProvider.getBoundConfigId(context, selectedWidgetId)
            val config = widgetConfigs.find { it.id == boundConfigId } ?: widgetConfigs.firstOrNull()
            textContentState = config?.content ?: ""
            currentStyle = ReminderWidgetProvider.getWidgetStyle(context, selectedWidgetId, config?.styleJson)
        }
    }

    val selectedReminder = selectedConfig

    var showProDialog by remember { mutableStateOf(startOnActivate) }

    // 样式保存与更新回调。
    // 新付费规则：预览任意风格、默认组件免费、仅在"同步到桌面"时拦截。
    // 未激活时切到付费风格仍允许在 App 内预览，但点保存同步到桌面时弹激活。
    val onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome = onStyleChange@{ widgetId, configId, newContent, newStyle ->
        // 主体四周透明的形状不支持背景色，落库前统一清空，避免旧配色残留导致外围露出包裹卡片
        val style = newStyle.withoutUnsupportedBackgroundColor()
        val isSyncToDesktop = widgetId != -1
        // 付费点只有两个：会员专属风格、自定义背景图。
        // 字体、字号、颜色、圆角、不透明度等细节调整全部免费，不再参与判定。
        val needPay = isSyncToDesktop && !trialManager.isActivated() && (
            WidgetStyle.isProPreset(style) || !style.backgroundImagePath.isNullOrEmpty()
            )
        if (needPay) {
            showProDialog = true
            return@onStyleChange SaveOutcome.REQUIRES_PRO
        }
        // 编辑期背景图先落在 bg_tmp；走到这里说明用户确实在保存，提交为正式资源（可重复调用）
        val committedPath = CropImageHelper.commitBackground(context, style.backgroundImagePath)
        val finalStyle = if (committedPath != style.backgroundImagePath) {
            style.copy(backgroundImagePath = committedPath)
        } else style
        if (committedPath != style.backgroundImagePath && widgetId == selectedWidgetId) {
            // 路径已变更，同步内存态，避免预览继续引用已被移走的临时文件
            currentStyle = finalStyle
        }
        if (widgetId != -1) {
            ReminderWidgetProvider.saveWidgetStyle(context, widgetId, finalStyle)
        }
        val target = widgetConfigs.find { it.id == configId }
        if (target != null) {
            persistJob?.cancel()
            // 用 lifecycleScope 而非组合作用域：页面销毁时未到期的防抖写入仍能完成，避免丢失最后 400ms 的编辑
            persistJob = activity.lifecycleScope.launch {
                kotlinx.coroutines.delay(400)
                try {
                    withContext(Dispatchers.IO) {
                        dbHelper.updateWidgetConfig(
                            target.copy(
                                content = newContent,
                                styleJson = finalStyle.toJsonString()
                            )
                        )
                        val updatedConfigs = dbHelper.getAllWidgetConfigs()
                        withContext(Dispatchers.Main) {
                            widgetConfigs = updatedConfigs
                        }
                    }
                    // 新背景已落库，才删除被替换掉的旧背景文件，避免长期堆积孤儿图
                    val oldBgPath = runCatching {
                        WidgetStyle.fromJsonString(target.styleJson).backgroundImagePath
                    }.getOrNull()
                    if (!oldBgPath.isNullOrEmpty() && oldBgPath != finalStyle.backgroundImagePath) {
                        runCatching { File(oldBgPath).delete() }
                    }
                    ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                } catch (e: Exception) {
                    Timber.e(e, "persist widget style failed")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        SaveOutcome.SAVED
    }

    if (showProDialog) {
        ProActivationDialog(
            isActivated = isActivatedState,
            isPaying = isPaying,
            accountName = accountName,
            onActivate = {
                if (!isPaying) {
                    isPaying = true
                    activity.lifecycleScope.launch {
                        when (val outcome = ProPurchase.purchase(activity)) {
                            is ProPurchase.Outcome.Paid -> {
                                trialManager.activate(TrialManager.PAY_METHOD_ALIPAY)
                                isActivatedState = true
                                showProDialog = false
                                // 桌面组件上未激活时的提示位图需要重绘
                                ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                                Toast.makeText(context, "🎉 PRO 已激活，全部风格已解锁！", Toast.LENGTH_SHORT).show()
                            }

                            is ProPurchase.Outcome.Unpaid -> if (outcome.message.isNotEmpty()) {
                                Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                            }

                            is ProPurchase.Outcome.Failed -> {
                                Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                            }
                        }
                        isPaying = false
                    }
                }
            },
            onOpenAccount = { showAccountDialog = true },
            onDismiss = { if (!isPaying) showProDialog = false }
        )
    }

    if (showAccountDialog) {
        AccountDialog(
            onProConfirmed = {
                trialManager.activate(TrialManager.PAY_METHOD_ACCOUNT)
                isActivatedState = true
                ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                Toast.makeText(context, "🎉 已通过账号找回 PRO，全部风格已解锁！", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showAccountDialog = false
                accountName = AccountStore.snapshot(context)?.displayName
            },
        )
    }

    showLegalDoc?.let { doc ->
        LegalDocDialog(
            title = LegalDocs.titleOf(doc),
            body = LegalDocs.bodyOf(doc),
            onDismiss = { showLegalDoc = null }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = darkBg
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
                                darkBg  // 渐入页面底色
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
                                fontSize = 13.sp,
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
                                    ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                                    Toast.makeText(context, "数据已同步刷新", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(2.dp, CircleShape)
                                    .background(Color.White, CircleShape)
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)), CircleShape)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF065F46), modifier = Modifier.size(18.dp))
                            }

                            val badgeBg = if (isActivatedState) {
                                Brush.horizontalGradient(listOf(selectBlue, selectBlue))
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
                                // 扁平线性图标替代实体 emoji：已激活用认证徽章，未激活用Premium徽章
                                Icon(
                                    imageVector = if (isActivatedState) Icons.Filled.Verified else Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isActivatedState) "PRO" else "激活PRO ${ProPurchase.PRICE_TEXT}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // ⚠️ 开发期临时开关：一键在「已激活 / 未激活」之间切换，用于验证付费墙与锁定态。
                            // 发布前必须整块删除——留着等于把付费墙拆了。
                            DevProToggle(
                                activated = isActivatedState,
                                onToggle = { checked ->
                                    if (checked) {
                                        trialManager.activate(TrialManager.PAY_METHOD_DEV)
                                    } else {
                                        trialManager.resetActivation()
                                    }
                                    isActivatedState = checked
                                    ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                                    Toast.makeText(
                                        context,
                                        if (checked) "开发开关：已切换到 PRO" else "开发开关：已切换到免费",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
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
                                ReminderWidgetProvider.getBoundConfigId(context, pageWidgetId)
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
                                ReminderWidgetProvider.getWidgetStyle(context, pageWidgetId, pageConfig?.styleJson)
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
                            // 预览按组件**真实 dp 尺寸**渲染，而不是写死宽度：
                            // 写死 360dp 会让渲染比例与桌面实际比例脱节（4×2 实际是 250:110≈2.27:1），
                            // 各类按比例分配的几何（图文卡、天气盒子的腔体等）会算错格子。
                            val (realW, realH) = if (pageWidgetId != -1) {
                                ReminderWidgetProvider.getWidgetSizeDp(context, pageWidgetId)
                            } else 250 to 110
                            // 渲染高度直接用真实高度：位图比例与桌面组件完全一致，
                            // 显示层用 ContentScale.Fit 等比缩小，预览比例自然正确。
                            // previewHeightDp（行数估算）只用于外层显示盒高度防裁切，不参与渲染。
                            val renderHeightDp = if (realH > 0) realH else previewHeightDp
                            val pageBitmap by produceState<Bitmap?>(
                                initialValue = null,
                                pageContent, pageStyle, renderHeightDp, realW
                            ) {
                                value = withContext(Dispatchers.Default) {
                                    try {
                                        WidgetCanvasRenderer.render(
                                            context = context,
                                            widthDp = realW,
                                            heightDp = renderHeightDp,
                                            content = pageContent,
                                            style = pageStyle
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
                                    .clickable {
                                        scope.launch { subTabPagerState.animateScrollToPage(1) }
                                    },
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
                            containerColor = darkBg
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
                                    .background(Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                    .padding(3.dp)
                            ) {
                                val width = maxWidth
                                val tabKeys = listOf("library", "adjust")
                                val indicatorWidth = width / 2

                                // 指示器随左右滑动实时跟手，落定后与页签一致
                                val indicatorPosition = subTabPagerState.currentPage + subTabPagerState.currentPageOffsetFraction
                                val selectedOffset = indicatorWidth * indicatorPosition

                                // 背景滑块：仅静态到位，不含隐式/显式位移动画
                                Box(
                                    modifier = Modifier
                                        .offset(x = selectedOffset)
                                        .width(indicatorWidth)
                                        .fillMaxHeight()
                                        .shadow(2.dp, RoundedCornerShape(10.dp))
                                        .background(selectBlue, RoundedCornerShape(10.dp))
                                )

                                // Tab 按钮：扁平线性图标 + 文字，替代实体 emoji
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(
                                        Triple("library", "跃然纸上", Icons.AutoMirrored.Filled.MenuBook),
                                        Triple("adjust", "个性定制", Icons.Filled.Tune)
                                    ).forEach { (key, label, icon) ->
                                        val tabTint = if (subTabPagerState.currentPage == tabKeys.indexOf(key)) {
                                            Color.White
                                        } else {
                                            Color(0xFF64748B)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable {
                                                    scope.launch {
                                                        subTabPagerState.animateScrollToPage(tabKeys.indexOf(key))
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = tabTint,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = label,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tabTint,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 内容区：左右滑动即可切换「跃然纸上 / 个性定制」。
                            // beyondViewportPageCount=1 让两页常驻组合，保住各自的滚动位置与内部状态
                            HorizontalPager(
                                state = subTabPagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                beyondViewportPageCount = 1
                            ) { page ->
                            if (page == 0) {
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
                                                ReminderWidgetProvider.bindReminderToWidget(context, widgetId, reminder.id)
                                                val newConfigId = ReminderWidgetProvider.getBoundConfigId(context, widgetId)
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
                                                ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                                            }
                                        } else {
                                            onStyleChange(selectedWidgetId, selectedConfigId, reminder.content, currentStyle)
                                        }
                                    },
                                    onOpenDoc = { showLegalDoc = it }
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
                                    },
                                    onOpenDoc = { showLegalDoc = it },
                                    initiallyTutorialExpanded = startTutorialExpanded
                                )
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}
