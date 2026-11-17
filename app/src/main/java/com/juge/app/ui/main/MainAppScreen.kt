package com.juge.app.ui.main

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.juge.app.CropImageHelper
import com.juge.app.JugeApplication
import com.juge.app.ReminderWidgetProvider
import com.juge.app.data.Category
import com.juge.app.data.DbHelper
import com.juge.app.data.Reminder
import com.juge.app.data.TrialManager
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetDisplaySize
import com.juge.app.data.WidgetStyle
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.AppText
import com.juge.app.ui.PreviewMetrics
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.animateToPageOnUserInput
import com.juge.app.ui.requiresProForDesktop
import com.juge.app.ui.swipeBackChrome
import com.juge.app.ui.swipeBackToPage
import com.juge.app.ui.adjust.AdjustTabContent
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.hazeEffect
import com.juge.app.ui.theme.drawerGlassBg
import com.juge.app.ui.theme.drawerGlassTint
import com.juge.app.ui.theme.sheetHandle
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** 半页兜底吸附的静默判定：位移停止这么久、且没有手指/动画，才认为真的停在了半页 */
private const val PAGE_SETTLE_DEBOUNCE_MS = 120L

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalHazeApi::class
)
@Composable
fun MainAppScreen(
    dbHelper: DbHelper,
    trialManager: TrialManager,
    activity: ComponentActivity,
    startOnActivate: Boolean,
    targetEditConfigId: Long,
    startOnAdjust: Boolean = false,
    startTutorialExpanded: Boolean = false,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit
) {
    val context: Context = activity
    // 「跃然纸上 / 个性定制」翻页状态：页签点击、预览卡片点击与内容区左右滑动共用。
    // 指示器与文字颜色都直接读 pager（currentPage 越过一半即切换），避免等落定才变色
    val subTabPagerState = rememberPagerState(initialPage = if (startOnAdjust) 1 else 0, pageCount = { 2 })
    // 半页兜底吸附：pager 不允许静止在非整页位置。
    // 风格行的手势被横向 scrollable 截住，pager 只能被嵌套滚动 dispatchRawDelta 推着走，
    // 松手时没有 fling 帮它 settle；和页签翻页动画叠在一起时就会停在两页正中间（实测卡死）。
    // 只要「位移停止一段时间、没有程序化滚动、手指也已离开 pager」，就吸到最近页。
    var pagerTouchDown by remember { mutableStateOf(false) }
    // 吸附动画不能跑在 collectLatest 回调里：动画自己会把 isScrollInProgress 置真，
    // 新 emission 会把“旧回调”连同动画一起取消（实测每 120ms 自杀一次）。放到独立
    // scope 执行，collectLatest 只负责防抖。
    val settleScope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(subTabPagerState) {
        snapshotFlow {
            Triple(
                subTabPagerState.currentPageOffsetFraction,
                subTabPagerState.isScrollInProgress,
                pagerTouchDown
            )
        }.collectLatest { (fraction, scrolling, touching) ->
            if (fraction == 0f || scrolling || touching) return@collectLatest
            delay(PAGE_SETTLE_DEBOUNCE_MS)
            if (!pagerTouchDown &&
                !subTabPagerState.isScrollInProgress &&
                subTabPagerState.currentPageOffsetFraction != 0f &&
                settleJob?.isActive != true
            ) {
                settleJob = settleScope.launch {
                    subTabPagerState.animateToPageOnUserInput(subTabPagerState.currentPage)
                }
            }
        }
    }
    var isActivatedState by remember { mutableStateOf(trialManager.isActivated()) }
    // 支付 / 找回的「进行中」不在这里保存：那是进程级状态（见 PayFlow），
    // 存在 composition 里的话，旋转重建会让按钮在两个流程都还在跑的时候重新可点。
    // 会员状态卡：点顶栏 PRO 徽章打开。激活记录只在打开时读一次 prefs，不在组合期反复读
    var showStatusDialog by remember { mutableStateOf(false) }
    val activationRecord = remember(showStatusDialog, isActivatedState) {
        if (showStatusDialog && isActivatedState) trialManager.activationRecord() else null
    }
    // 协议行打开的协议全文：null / "user" / "privacy" / "license"
    var showLegalDoc by remember { mutableStateOf<String?>(null) }

    // 上次支付被中断（App 被杀 / 切后台）时复查留存订单，兜底「已付款但没激活成功」
    LaunchedEffect(Unit) {
        if (!trialManager.isActivated() &&
            ProPurchase.recoverPending(context) is ProPurchase.Outcome.Paid
        ) {
            isActivatedState = true
            ReminderWidgetProvider.triggerUpdateAllWidgets(context)
            Toast.makeText(context, "🎉 已找回支付订单，PRO 激活成功！", Toast.LENGTH_SHORT).show()
        }
    }

    // 换机找回走「付费墙 → 支付宝授权」那条路（见 AlipayRecovery），
    // 启动时不再有任何账号对账动作：没有登录态，也就没有可对账的东西。

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

    // 预览 Pager 每页的「绑定关系 + 组件级样式 + 正文」快照。
    // 这三个值原先都在组合函数体里现算（synchronized + 读 prefs + 解析 29 字段 JSON），
    // 而 Pager 的 content lambda 每重组一次、每滑一页都会跑一遍——等于把磁盘/锁的开销
    // 摊到每一帧上。改成在 IO 上一次性拍好，组合阶段只做下标查表（见 WidgetPageSnapshot）。
    // key 带 widgetConfigs：配置行写库后要重新拍，否则预览停在旧内容上。
    val widgetPages by produceState(
        initialValue = emptyList<WidgetPage>(),
        appWidgetIds,
        widgetConfigs
    ) {
        value = withContext(Dispatchers.IO) {
            buildWidgetPages(context, appWidgetIds, widgetConfigs)
        }
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
        // 反查要走 bindingLock + 读 prefs，放到 IO 上做：这是一次性定位请求，
        // 但没必要让主线程在启动首帧等它（组件多时就是 N 次锁 + N 次 prefs 读）
        val index = withContext(Dispatchers.IO) {
            appWidgetIds.indexOfFirst {
                ReminderWidgetProvider.getBoundConfigId(context, it) == target
            }
        }
        if (index >= 0) {
            selectedWidgetId = appWidgetIds[index]
            // 翻预览 Pager：currentPage 变化后会触发上面的 LaunchedEffect 同步 selectedWidgetId。
            // 用动画翻页而非瞬跳——从桌面点编辑进来时，预览卡滑动到位比"闪现"更明确
            if (pagerState.currentPage != index) scope.launch { pagerState.animateScrollToPage(index) }
        }
        scope.launch { subTabPagerState.animateScrollToPage(1) }
    }

    // 这两处仍在组合期同步读 prefs（getBoundConfigId / getWidgetStyle），是有意保留：
    // remember/rememberSaveable 的 key 门控让它们只在「切换组件 / 配置写库」时各跑一次，
    // 不是每帧执行。P1-11 真正每帧付代价的那部分（Pager 每页 content lambda 里
    // 现算绑定 + 解析 29 字段 JSON）已由上面的 widgetPages 快照挪到 IO。
    // 若把这里也改成异步派生，首帧 selectedConfig / selectedConfig?.id 会先给出兜底值
    // 再跳变，而 textContentState 与 currentStyleJson 都以 selectedConfig?.id 为
    // rememberSaveable 的 key —— 跳变会重置用户正在编辑的草稿，得不偿失。
    val selectedConfigId = remember(selectedWidgetId, widgetConfigs) {
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
    // 样式草稿同样要跨重建存活：见上面 textContentState 的说明。
    // 存 JSON 而不是 WidgetStyle 本身：rememberSaveable 只认 Bundle 能装的东西。
    var currentStyleJson by rememberSaveable(selectedWidgetId, selectedConfig?.id) {
        mutableStateOf(
            if (selectedWidgetId != -1) {
                ReminderWidgetProvider.getWidgetStyle(context, selectedWidgetId, selectedConfig?.styleJson).toJsonString()
            } else {
                WidgetStyle.fromJsonString(selectedConfig?.styleJson).toJsonString()
            }
        )
    }
    var currentStyle by remember(currentStyleJson) {
        mutableStateOf(WidgetStyle.fromJsonString(currentStyleJson))
    }
    fun updateCurrentStyle(newStyle: WidgetStyle) {
        currentStyle = newStyle
        currentStyleJson = newStyle.toJsonString()
    }

    // 预览区高度随**当前风格的最佳显示尺寸**自适应：4×2 的卡片矮，盒内下方自然留白；
    // 但显示盒本身高度固定（见 PreviewMetrics.previewBoxHeightDp）——切换 4×2/4×4 时
    // 标签行、抽屉和背景图都不再跟着上下跳（用户反馈的「页面晃动」）。
    // 下限保住最小可见高度，封顶到盒高避免 4×4 页内容溢出预览盒。
    // 只做纯计算：样式由调用方给（Pager 页内已经拿到快照，外层传 currentStyle）。
    // 之前这个函数自己去读 getBoundConfigId / getWidgetStyle，等于每次重组都要
    // 拿锁 + 读 prefs + 解析 JSON —— 见 WidgetPageSnapshot 里的说明。
    fun previewHeightForPage(@Suppress("UNUSED_PARAMETER") page: Int, style: WidgetStyle): Int {
        // 高度取风格的**最佳显示尺寸**（每款风格在 WidgetStyle.bestDisplaySize 上声明：
        // 4×4 款 250×250、4×2 款 250×110），与 WidgetPreviewPager 的出图尺寸同一来源，
        // 预览图与显示盒不会各算一套。
        // 不读桌面组件的入口/实时尺寸：同一个风格挂在不同入口、被拉成不同大小，
        // 预览都要按它自己的尺寸显示（产品规则，2026-10-08）。
        // 高度映射本身是纯函数，已抽到 PreviewMetrics 并有单测覆盖
        // （下限 180dp、4×4 上限 260dp、横向分割卡固定 130dp 等规则都在那边）。
        // 这里再封顶到显示盒高度：页内盒不得高于预览盒，否则 4×4 会顶着盒底被裁。
        return PreviewMetrics.previewHeightForStyle(style)
            .coerceAtMost(PreviewMetrics.PREVIEW_BOX_BASE_DP)
    }
    // 放在 currentStyle 与 previewHeightForPage 声明之后，保证内部引用均已初始化
    // 显示盒高度固定 244dp（用户确认的视觉高度）：预览位图按真实比例等比渲染后
    // 以 ContentScale.Fit 贴顶显示在盒内，4×2 时卡片约 155dp 高、下方自然留白。
    // 渲染比例正确的前提下，盒子只提供"预览区域"的视觉高度，不影响卡片形状。
    val previewBoxHeightDp = PreviewMetrics.previewBoxHeightDp(previewHeightForPage(pagerState.currentPage, currentStyle))

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

    // rememberSaveable 而不是 remember：Manifest 里没有 android:configChanges，
    // 旋转 / 折叠屏展开 / 改系统字号都会重建 Activity。用 remember 的话
    // 用户刚敲的一半文案会当场消失，而 currentStyle 又会从库里重读旧 JSON，
    // 表现为「刚调好的字号和背景自己回滚了」。
    // key 仍带 selectedConfig?.id：切换到另一个组件时必须换一份草稿。
    var textContentState by rememberSaveable(selectedConfig?.id) {
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
            updateCurrentStyle(loadedStyle)
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
        val target = try {
            if (widgetId != -1) {
                ReminderWidgetProvider.ensureIndependentConfig(context, widgetId, configId)
            } else {
                widgetConfigs.find { it.id == configId }
            }
        } catch (e: Exception) {
            Timber.e(e, "prepare widget config failed")
            Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
            null
        } ?: return@persist SaveOutcome.PREVIEW_ONLY
        // 编辑期背景图先落在 bg_tmp；走到这里说明用户确实在保存，提交为正式资源（可重复调用）
        val committedPath = CropImageHelper.commitBackground(context, style.backgroundImagePath)
        val finalStyle = if (committedPath != style.backgroundImagePath) {
            style.copy(backgroundImagePath = committedPath)
        } else style
        if (committedPath != style.backgroundImagePath && widgetId == selectedWidgetId) {
            // 路径已变更，同步内存态，避免预览继续引用已被移走的临时文件
            updateCurrentStyle(finalStyle)
        }
        if (widgetId != -1) {
            ReminderWidgetProvider.saveWidgetStyle(context, widgetId, finalStyle)
        }
        // 防抖写库：同一配置的连续编辑只保留末次（取消一个正在等待中的任务重新排期）；
        // **不同配置之间不能互相取消**——快速切到另一个组件再改动时，上一个组件的
        // 末次编辑说不定还没等到 400ms，被取消就永远丢了
        if (pendingPersistConfigId == target.id) {
            persistJob?.cancel()
        }
        // 用进程级 scope 而非 lifecycleScope：Activity 销毁（返回键/最近任务划掉）
        // 正常会取消 lifecycleScope 的 delay(400)，与防抖机制的本意背道而驰；
        // 组件实际尺寸变化提示仍在这里做
        pendingPersistConfigId = target.id
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
        activity = activity,
        onActivatedChange = { isActivatedState = it },
        onProDialogDismiss = { showProDialog = false },
        onRequestOpenPro = { showProDialog = true },
        showStatusDialog = showStatusDialog,
        activationRecord = activationRecord,
        onStatusDismiss = { showStatusDialog = false },
        legalDoc = showLegalDoc,
        onLegalDismiss = { showLegalDoc = null }
    )

    WidgetSizeHintDialog(show = sizeHint != null, hint = sizeHint) { sizeHint = null }

    // 抽屉毛玻璃：背景源在 MainScreenBackground（极光/海滩图），
    // 面板本体在这里做实时模糊采样。
    val hazeState = remember { HazeState() }
    // 软件渲染下 Haze 会退回 RenderScript，而 Robolectric 的软件画布拿不到输入 Surface（NPE）。
    // 测试环境没有真实模糊能力，直接关掉 blur、走 Haze 自带的 tint 回退。
    val composeView = LocalView.current
    SideEffect {
        hazeState.blurEnabled =
            composeView.isHardwareAccelerated && Build.FINGERPRINT != "robolectric"
    }

    // 「我的」页是全屏页面（不是弹窗）：作为同一窗口里的上层页面盖在主界面之上，
    // 从右侧滑入；协议/登录/付费墙仍是独立 Dialog 窗口，天然盖在它上面。
    Box(modifier = Modifier.fillMaxSize()) {
        MainScreenBackground(heroHeight = heroHeight, hazeState = hazeState) {
            // 顶部区域（顶栏 + 预览轮播 + 组件标签）不在抽屉里，也纳入右滑返回：
            // 预览轮播还能往右翻时让轮播自己翻；单个组件/已在第一个预览时右滑返回。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .swipeBackToPage(
                        pagerState = pagerState,
                        onSwipeBack = {
                            scope.launch { subTabPagerState.animateToPageOnUserInput(0) }
                        },
                        requirePagerMovement = false
                    )
            ) {
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
                    onOpenPro = { showProDialog = true },
                    onOpenStatus = { showStatusDialog = true },
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = onToggleDarkTheme
                )
                WidgetPreviewPager(
                    pagerState = pagerState,
                    appWidgetIds = appWidgetIds,
                    widgetPages = widgetPages,
                    widgetConfigs = widgetConfigs,
                    selectedWidgetId = selectedWidgetId,
                    currentStyle = currentStyle,
                    textContentState = textContentState,
                    previewBoxHeightDp = previewBoxHeightDp,
                    previewHeightForPage = ::previewHeightForPage,
                    onEditClick = { scope.launch { subTabPagerState.animateToPageOnUserInput(1) } }
                )
                // 组件标签：位于预览卡片与下方抽屉之间的空白区域，随 Pager 翻页同步
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppText(
                        text = "小组件#${pagerState.currentPage + 1} 实时设计·仅为预览·以桌面组件显示为准",
                        role = AppTextRole.label,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.30f), AppShape.pill)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // 下 2/3 面板容器 (苹果风格大抽屉)
            Card(
                colors = CardDefaults.cardColors(
                    // 面板本体透明，材质（白色半透明底 + 背景实时模糊）由 hazeEffect 绘制
                    containerColor = Color.Transparent
                ),
                shape = AppShape.sheet,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // 先裁剪再模糊：haze 的模糊层默认是矩形，不裁的话会从抽屉圆角
                    // 外面露出一圈方角（用户反馈的「矩形渲染层」）
                    .clip(AppShape.sheet)
                    .hazeEffect(
                        state = hazeState,
                        // 日间白 45% / 夜间深底 55% tint（比 HazeMaterials.thin 的 60% 更透一档，用户确认）：
                        // 面板顶边透出模糊的海滩图带，玻璃质感由它提供；噪点保持 0
                        style = HazeStyle(
                            backgroundColor = drawerGlassBg,
                            tint = HazeTint(drawerGlassTint),
                            blurRadius = 24.dp,
                            noiseFactor = 0f,
                        ),
                    ) {
                        // 模糊输入按 0.5× 采样：模糊前的像素量降到 1/4，Haze 会把模糊半径
                        // 按同一比例换算，观感基本不变。抽屉是静态毛玻璃，不值得为它全分辨率采样。
                        inputScale = HazeInputScale.Fixed(0.5f)
                        // 模糊层的边缘默认是矩形：不指定形状的话，磨砂会从抽屉圆角
                        // 外面露出方形的一块（用户反馈的「矩形渲染」）。与抽屉同圆角。
                        blurredEdgeTreatment = BlurredEdgeTreatment(AppShape.sheet)
                    }
                    .onGloballyPositioned { coordinates ->
                        // 取抽屉上沿的绝对位置交给背景图收边；只在变化时写回，避免无谓重组
                        val top = coordinates.positionInRoot().y.roundToInt()
                        if (top != drawerTopPx) drawerTopPx = top
                    },
                // 不给任何 elevation（Card 默认还有 1dp，必须显式归零）：
                // 用户要求去掉描边、高光、落影，面板与背景的边界只由毛玻璃 tint 区分
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                // 顶缘不画白色「唇线」高光，也不画深灰外描边与落影（用户要求去掉），
                // 原先托住唇线的 4% 内阴影一并移除。
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 7.dp)
                ) {
                    // 抽屉顶栏（把手 + 分段页签）不在 pager 内：右滑返回单独接一层观察者。
                    // 只包住顶栏，不包 pager，避免和内容层的观察者重复触发。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .swipeBackChrome {
                                scope.launch { subTabPagerState.animateToPageOnUserInput(0) }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(40.dp)
                                .height(4.dp)
                                .clip(AppShape.handle)
                                .background(sheetHandle)
                        )

                        Spacer(modifier = Modifier.height(AppSpacing.controlGap))

                        // 苹果风分段控制器 Segmented Control (全动态测量)
                        SubTabSwitcher(
                            pagerState = subTabPagerState,
                            onSelectTab = { scope.launch { subTabPagerState.animateToPageOnUserInput(it) } }
                        )

                        Spacer(modifier = Modifier.height(AppSpacing.sectionGap))
                    }

                    // 内容区：左右滑动即可切换「跃然纸上 / 个性定制」。
                    // beyondViewportPageCount=1 让两页常驻组合，保住各自的滚动位置与内部状态
                    HorizontalPager(
                        state = subTabPagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // 只观察、不消费：给半页兜底吸附提供“手指是否还在 pager 上”
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val pressed = awaitPointerEvent(PointerEventPass.Initial)
                                            .changes.any { it.pressed }
                                        if (pressed != pagerTouchDown) pagerTouchDown = pressed
                                    }
                                }
                            },
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
                                isActivated = isActivatedState,
                                appWidgetIds = appWidgetIds,
                                selectedWidgetId = selectedWidgetId,
                                selectedReminderId = selectedReminder?.id ?: -1L,
                                selectedStyle = currentStyle,
                                selectedContent = textContentState,
                                onStyleStateChange = { updateCurrentStyle(it) },
                                onContentStateChange = { textContentState = it },
                                onSelectPreset = { wId, rId, newStyle ->
                                    updateCurrentStyle(newStyle)
                                    onStyleChange(wId, rId, textContentState, newStyle)
                                },
                                onStyleChange = { wId, rId, text, newStyle ->
                                    onStyleChange(wId, rId, text, newStyle)
                                },
                                onApplyToDesktop = { wId, rId, text, newStyle ->
                                    onApplyToDesktop(wId, rId, text, newStyle)
                                },
                                onOpenDoc = { showLegalDoc = it },
                                // 页面级右滑返回：内容区与抽屉顶栏共用同一入口
                                onSwipeBack = {
                                    scope.launch { subTabPagerState.animateToPageOnUserInput(0) }
                                },
                                pagerState = subTabPagerState,
                                initiallyTutorialExpanded = startTutorialExpanded
                            )
                        }
                    }
                }
            }
        }
    }
}
