package com.juge.app.ui.main

import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.juge.app.CropImageHelper
import com.juge.app.JugeApplication
import com.juge.app.ReminderWidgetProvider
import com.juge.app.account.AccountStore
import com.juge.app.account.AccountSync
import com.juge.app.data.Category
import com.juge.app.data.DbHelper
import com.juge.app.data.Reminder
import com.juge.app.data.TrialManager
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetDisplaySize
import com.juge.app.data.WidgetStyle
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.PreviewMetrics
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.requiresProForDesktop
import com.juge.app.ui.adjust.AdjustTabContent
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.darkBg
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
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
    var isLibraryLoaded by remember { mutableStateOf(false) }
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
                isLibraryLoaded = true
            }
        }
    }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }

    // 编辑状态弹窗控制 (专属于书库内容编辑)
    var editingReminderForContent by remember { mutableStateOf<Reminder?>(null) }

    // Pager 核心状态与桌面小组件映射。
    // 组件集合必须是**状态**并在 ON_RESUME 重读：用户会一边把 App 留在后台一边在桌面
    // 添加/删除组件，一次性快照会让预览 Pager 排布旧数量——新组件永远不出现在列表里，
    // 删掉的组件留在页面上，其样式还会被写进指向死组件的 prefs
    val appWidgetIdsState = remember {
        mutableStateOf(ReminderWidgetProvider.getAllAppWidgetIds(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val appWidgetIds = appWidgetIdsState.value
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val latest = ReminderWidgetProvider.getAllAppWidgetIds(context)
                if (!latest.contentEquals(appWidgetIdsState.value)) {
                    appWidgetIdsState.value = latest
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedWidgetId by remember(appWidgetIds) {
        mutableStateOf(appWidgetIds.firstOrNull() ?: -1)
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        // 读状态而非捕获值：组件数量变化后 pageCount 必须跟着走，否则新页不可滑入
        pageCount = { appWidgetIdsState.value.size.coerceAtLeast(1) }
    )

    LaunchedEffect(pagerState.currentPage, appWidgetIds) {
        if (appWidgetIds.isNotEmpty()) {
            val page = pagerState.currentPage.coerceIn(0, appWidgetIds.size - 1)
            selectedWidgetId = appWidgetIds[page]
        }
    }

    // 「点击桌面组件 → 打开 App」的定位请求（MainActivity 带上被点组件的 config_id）。
    // 反查绑定关系找到对应的 appWidgetId，选中它并直接落在「个性定制」页——
    // 用户点组件的目的就是改这个组件，所以直接定位到它的编辑页。
    //
    // 之所以靠 config_id 而不是 appWidgetId 反查：真正决定显示内容与样式的是配置行，
    // 而绑定关系（appWidgetId → configId）存在 SharedPreferences 里，可以不依赖数据库就完成定位。
    //
    // 一次性消费：消费后立刻置 -1，否则用户在 App 内左右切页时会被反复拉回个性定制页。
    var pendingTargetConfigId by remember { mutableStateOf(targetEditConfigId) }
    LaunchedEffect(pendingTargetConfigId) {
        val target = pendingTargetConfigId
        if (target == -1L) return@LaunchedEffect
        pendingTargetConfigId = -1L
        val index = appWidgetIds.indexOfFirst {
            ReminderWidgetProvider.getBoundConfigId(context, it) == target
        }
        if (index >= 0) {
            selectedWidgetId = appWidgetIds[index]
            // 翻预览 Pager：currentPage 变化后会触发上面的 LaunchedEffect 同步 selectedWidgetId
            if (pagerState.currentPage != index) pagerState.scrollToPage(index)
        }
        scope.launch { subTabPagerState.animateScrollToPage(1) }
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

    // 预览区高度随**当前风格的最佳显示尺寸**自适应：4×2 款不预留 4×4 的空间，
    // 从而让“小组件#N”标签行与“跃然纸上/个性定制”页签行同步上移、去掉底部空洞。
    // 封顶 268dp 确保 4×4 款不被裁切，下限保住最小可见高度。
    fun previewHeightForPage(page: Int): Int {
        val wid = if (appWidgetIds.isNotEmpty()) appWidgetIds[page.coerceIn(0, appWidgetIds.size - 1)] else -1
        val cid = if (wid != -1) ReminderWidgetProvider.getBoundConfigId(context, wid) else -1L
        val cfg = if (cid != -1L) {
            widgetConfigs.find { it.id == cid } ?: widgetConfigs.firstOrNull()
        } else {
            widgetConfigs.firstOrNull()
        }
        val style = if (wid == selectedWidgetId) currentStyle
            else if (wid != -1) ReminderWidgetProvider.getWidgetStyle(context, wid, cfg?.styleJson)
            else WidgetStyle.fromJsonString(cfg?.styleJson)
        // 高度取风格的**最佳显示尺寸**（每款风格在 WidgetStyle.bestDisplaySize 上声明：
        // 4×4 款 250×250、4×2 款 250×110），与 WidgetPreviewPager 的出图尺寸同一来源，
        // 预览图与显示盒不会各算一套。
        // 不读桌面组件的入口/实时尺寸：同一个风格挂在不同入口、被拉成不同大小，
        // 预览都要按它自己的尺寸显示（产品规则，2026-10-08）。
        // 高度映射本身是纯函数，已抽到 PreviewMetrics 并有单测覆盖
        // （下限 180dp、4×4 上限 268dp、横向分割卡固定 130dp 等规则都在那边）。
        return PreviewMetrics.previewHeightForStyle(style)
    }
    // 放在 currentStyle 与 previewHeightForPage 声明之后，保证内部引用均已初始化
    // 显示盒高度固定 244dp（用户确认的视觉高度）：预览位图按真实比例等比渲染后
    // 以 ContentScale.Fit 贴顶显示在盒内，4×2 时卡片约 155dp 高、下方自然留白。
    // 渲染比例正确的前提下，盒子只提供"预览区域"的视觉高度，不影响卡片形状。
    val previewBoxHeightDp = PreviewMetrics.previewBoxHeightDp(previewHeightForPage(pagerState.currentPage))

    // 抽屉上沿在 root 坐标中的位置（px）：顶部海滩图按它精确收边，图片下缘正好落在抽屉上沿，
    // 两者之间不再露出一条底色缝；抽屉圆角下面也由底板的 darkBg 兜底。
    // 用实测值而不是固定 dp：状态栏高度、字体缩放、4×4/4×2 预览高度都会挪动抽屉上沿。
    var drawerTopPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    // 首帧还没量到抽屉位置时沿用原经验值，量到后立即贴合（只在首帧生效，通常不可见）
    val heroHeight = if (drawerTopPx > 0) {
        with(density) { drawerTopPx.toDp() }
    } else {
        previewBoxHeightDp.dp + 140.dp
    }

    var textContentState by remember(selectedConfig?.id) {
        mutableStateOf(selectedConfig?.content ?: "")
    }

    // 防抖写库任务：声明在 LaunchedEffect 之前，切换组件时可 join 等待未落库的写入完成
    var persistJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 防抖任务当前针对的配置行：只有「同一配置」的新写入才允许重置它的 400ms 窗口，
    // 换了配置就各自独立排队，避免互相取消丢编辑
    var pendingPersistConfigId by remember { mutableStateOf<Long?>(null) }

    // 上次真正落库的风格尺寸：只在「风格尺寸发生变化」时提示手动拉伸，
    // 改文字/颜色等细节不会再弹提示
    var lastPersistedBestSize by remember { mutableStateOf<WidgetDisplaySize?>(null) }

    LaunchedEffect(selectedWidgetId) {
        if (selectedWidgetId != -1) {
            // 等待 400ms 防抖写入落库后再读取，避免切换组件时读到数据库旧值覆盖用户草稿
            persistJob?.join()
            val boundConfigId = ReminderWidgetProvider.getBoundConfigId(context, selectedWidgetId)
            val config = widgetConfigs.find { it.id == boundConfigId } ?: widgetConfigs.firstOrNull()
            textContentState = config?.content ?: ""
            val loadedStyle = ReminderWidgetProvider.getWidgetStyle(context, selectedWidgetId, config?.styleJson)
            currentStyle = loadedStyle
            lastPersistedBestSize = loadedStyle.bestDisplaySize
        }
    }

    val selectedReminder = selectedConfig

    var showProDialog by remember { mutableStateOf(startOnActivate) }

    var sizeHint by remember { mutableStateOf<WidgetSizeHint?>(null) }

    // 尺寸不匹配提示：只提醒，不阻断保存。桌面组件的实际大小由 launcher 控制，
    // App 没有强制 resize 的 API，所以引导用户直接在桌面长按拖动边缘。
    val maybeShowSizeHint: (Int, WidgetStyle) -> Unit = { widgetId, style ->
        if (widgetId != -1) {
            widgetSizeHintFor(
                style,
                ReminderWidgetProvider.getWidgetSizeString(context, widgetId)
            )?.let { sizeHint = it }
        }
    }

    // 编辑/预览期真正落库。调用前已经判断过付费权限。
    val persistStyle: (Int, Long, String, WidgetStyle) -> SaveOutcome = persist@{ widgetId, configId, newContent, style ->
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
        if (target == null) {
            // 没有可写目标（典型是未放置桌面组件、配置行也未绑定）：UI 状态已更新，
            // 但库里没落任何东西。不能返回 SAVED——那会让调用方弹「已成功保存并同步」，
            // 而这些内容下次进来并不会出现
            return@persist SaveOutcome.PREVIEW_ONLY
        }
        // 防抖写库：同一配置的连续编辑只保留末次（取消一个正在等待中的任务重新排期）；
        // **不同配置之间不能互相取消**——快速切到另一个组件再改动时，上一个组件的
        // 末次编辑说不定还没等到 400ms，被取消就永远丢了
        if (pendingPersistConfigId == configId) {
            persistJob?.cancel()
        }
        // 用进程级 scope 而非 lifecycleScope：Activity 销毁（返回键/最近任务划掉）
        // 正常会取消 lifecycleScope 的 delay(400)，与防抖机制的本意背道而驰；
        // 组件实际尺寸变化提示仍在这里做
        pendingPersistConfigId = configId
        persistJob = (context.applicationContext as JugeApplication).appScope.launch {
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
                // 新背景已落库，才删除被替换掉的旧背景文件；
                // 两条配置行/组件级样式缓存可能共用同一张图，删前必须查引用，
                // 否则先保存的那次替换会删掉另一个组件还在用的文件
                val oldBgPath = runCatching {
                    WidgetStyle.fromJsonString(target.styleJson).backgroundImagePath
                }.getOrNull()
                if (!oldBgPath.isNullOrEmpty() && oldBgPath != finalStyle.backgroundImagePath &&
                    !CropImageHelper.isBackgroundFileStillReferenced(context, oldBgPath, target.id)
                ) {
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
        if (widgetId != -1) {
            val sizeChanged = lastPersistedBestSize != style.bestDisplaySize
            lastPersistedBestSize = style.bestDisplaySize
            if (sizeChanged) maybeShowSizeHint(widgetId, style)
        }
        SaveOutcome.SAVED
    }

    // 编辑回调（输入文字、改字体/颜色/圆角/不透明度等）：
    // 未激活时如果当前是付费风格/自定义背景图，只更新 App 内预览，
    // 不落库、不触发桌面更新，也不弹付费框；等用户点「保存并应用到桌面」时再拦截。
    val onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome = onStyleChange@{ widgetId, configId, newContent, newStyle ->
        // 主体四周透明的形状不支持背景色，落库前统一清空，避免旧配色残留导致外围露出包裹卡片
        val style = newStyle.withoutUnsupportedBackgroundColor()
        if (requiresProForDesktop(style, trialManager.isActivated(), widgetId)) {
            return@onStyleChange SaveOutcome.PREVIEW_ONLY
        }
        persistStyle(widgetId, configId, newContent, style)
    }

    // 唯一付费拦截点：点「保存并应用到桌面小组件」时才判断。
    // 没有桌面目标（widgetId == -1）时不拦截，保持与旧逻辑一致。
    val onApplyToDesktop: (Int, Long, String, WidgetStyle) -> SaveOutcome = apply@{ widgetId, configId, newContent, newStyle ->
        val style = newStyle.withoutUnsupportedBackgroundColor()
        if (requiresProForDesktop(style, trialManager.isActivated(), widgetId)) {
            showProDialog = true
            return@apply SaveOutcome.REQUIRES_PRO
        }
        persistStyle(widgetId, configId, newContent, style)
    }

    MainOverlays(
        showProDialog = showProDialog,
        isActivated = isActivatedState,
        isPaying = isPaying,
        accountName = accountName,
        activity = activity,
        trialManager = trialManager,
        onActivatedChange = { isActivatedState = it },
        onPayingChange = { isPaying = it },
        onOpenAccount = { showAccountDialog = true },
        onProDialogDismiss = { showProDialog = false },
        showAccountDialog = showAccountDialog,
        onAccountDismiss = {
            showAccountDialog = false
            accountName = AccountStore.snapshot(context)?.displayName
        },
        legalDoc = showLegalDoc,
        onLegalDismiss = { showLegalDoc = null }
    )

    sizeHint?.let { hint ->
        WidgetSizeHintDialog(hint) { sizeHint = null }
    }

    MainScreenBackground(heroHeight = heroHeight) {
        MainTopBar(
            isActivated = isActivatedState,
            dbHelper = dbHelper,
            trialManager = trialManager,
            scope = scope,
            onReloaded = { rems, cats, configs ->
                reminders = rems
                categories = cats
                widgetConfigs = configs
            },
            onActivatedChange = { isActivatedState = it },
            onOpenPro = { showProDialog = true }
        )
        WidgetPreviewPager(
            pagerState = pagerState,
            appWidgetIds = appWidgetIds,
            widgetConfigs = widgetConfigs,
            selectedWidgetId = selectedWidgetId,
            currentStyle = currentStyle,
            textContentState = textContentState,
            previewBoxHeightDp = previewBoxHeightDp,
            previewHeightForPage = ::previewHeightForPage,
            onEditClick = { scope.launch { subTabPagerState.animateScrollToPage(1) } }
        )
        // 组件标签：位于预览卡片与下方抽屉之间的空白区域，随 Pager 翻页同步
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "小组件#${pagerState.currentPage + 1} 实时设计·仅为预览·以桌面组件显示为准",
                style = AppType.label,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.30f), AppShape.pill)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 下 2/3 面板容器 (苹果风格大抽屉)
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    // 取抽屉上沿的绝对位置交给背景图收边；只在变化时写回，避免无谓重组
                    val top = coordinates.positionInRoot().y.roundToInt()
                    if (top != drawerTopPx) drawerTopPx = top
                },
            colors = CardDefaults.cardColors(
                containerColor = darkBg
            ),
            border = BorderStroke(1.dp, borderBlue),
            shape = AppShape.sheet,
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
                        .clip(AppShape.handle)
                        .background(borderBlue)
                )

                Spacer(modifier = Modifier.height(AppSpacing.controlGap))

                // 苹果风分段控制器 Segmented Control (全动态测量)
                SubTabSwitcher(
                    pagerState = subTabPagerState,
                    onSelectTab = { scope.launch { subTabPagerState.animateScrollToPage(it) } }
                )

                Spacer(modifier = Modifier.height(AppSpacing.sectionGap))

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
                        LibraryPane(
                            dbHelper = dbHelper,
                            scope = scope,
                            categories = categories,
                            reminders = reminders,
                            isLoaded = isLibraryLoaded,
                            selectedCategory = selectedCategory,
                            editingReminder = editingReminderForContent,
                            onEditReminderRequest = { editingReminderForContent = it },
                            onCategorySelect = { selectedCategory = it },
                            onCategoriesChanged = { cats ->
                                categories = cats
                                // 当前选中的分类被删除后保持 id 会让筛选条死掉：
                                // 没有呈选中态的 chip、列表却筛出空集，与「金句全没了」无异。
                                // 列表刷新处统一对账（新建/改名/排序也会走到这里，均无害）
                                if (selectedCategory != null && cats.none { it.id == selectedCategory?.id }) {
                                    selectedCategory = null
                                }
                            },
                            onRemindersChanged = { reminders = it },
                            onWidgetConfigsChanged = { widgetConfigs = it },
                            selectedWidgetId = selectedWidgetId,
                            selectedConfigId = selectedConfigId,
                            currentStyle = currentStyle,
                            onContentChanged = { textContentState = it },
                            onStyleChange = onStyleChange,
                            onOpenDoc = { showLegalDoc = it }
                        )
                    } else {
                        AdjustTabContent(
                            widgetConfigs = widgetConfigs,
                            isActivated = isActivatedState,
                            appWidgetIds = appWidgetIds,
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
                            onApplyToDesktop = { wId, rId, text, newStyle ->
                                onApplyToDesktop(wId, rId, text, newStyle)
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
