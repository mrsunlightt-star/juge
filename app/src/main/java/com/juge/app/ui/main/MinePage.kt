package com.juge.app.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.juge.app.BuildConfig
import com.juge.app.account.AccountActions
import com.juge.app.account.AccountStore
import com.juge.app.data.TrialManager
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.AppDangerButton
import com.juge.app.ui.AppDialog
import com.juge.app.ui.AppDialogSurface
import com.juge.app.ui.AppDialogTitle
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppSecondaryButton
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppSectionLabel
import com.juge.app.ui.AppText
import com.juge.app.ui.AppTextButton
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.darkBg
import com.juge.app.ui.theme.premiumGoldDeep
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.successGreen
import com.juge.app.ui.theme.textTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** 激活时间只用于「我的」页展示：本地时区、中文习惯、精确到分钟 */
private const val ACTIVATION_TIME_PATTERN = "yyyy年M月d日 HH:mm"

/**
 * 激活时间格式化（纯函数，便于单测）：毫秒时间戳 → 「2026年10月30日 21:30」。
 *
 * 每次新建 [SimpleDateFormat]：共享实例不是线程安全的，一页只格式化一次，
 * 这点开销远小于留一个随时会踩的并发陷阱。
 */
internal fun formatActivationTime(activatedAt: Long): String =
    SimpleDateFormat(ACTIVATION_TIME_PATTERN, Locale.CHINA).format(Date(activatedAt))

/**
 * 「我的」页面：**已激活用户的账号与会员中心**。
 *
 * 为什么会有这个页面：未激活时顶栏是金色的「激活PRO ￥3.99」胶囊，点击直接进付费墙；
 * 激活之后这枚胶囊就没有意义了（用户已经买断），于是顶栏换成「我的」入口——
 * 会员状态、会员账户昵称、退出登录、注销账号这些常规账户信息都收在这里。
 *
 * 形态是**全屏页面**（不是弹窗）：与 App 其它页面同一套语言——`darkBg` 页面底 +
 * `AppSectionCard` 白卡 + 令牌字号；从右侧滑入「下一页」，返回键或左上角返回退出。
 * 协议入口**不在这里重复**：App 各页底部（LegalFooter）已经是协议的唯一入口。
 *
 * 登录表单不在这里重复实现：未登录时给一个「登录 / 注册」按钮，打开
 * [com.juge.app.account.AccountDialog]；退出登录与注销账号两个动作通过 [AccountActions]
 * 与账号弹窗共用同一份实现。
 *
 * 注销账号是**独立按钮**：点它只弹二次确认弹窗，不在页面内联展开——
 * 破坏性动作必须在独立的一步里被确认，而不是滑到页面底部顺手点到。
 *
 * @param account 当前登录会话快照；null 表示未登录（正常状态，不拦截任何操作）
 * @param onOpenLogin 打开账号登录弹窗
 * @param onOpenPro 会员状态失效时（账号同步撤销了 PRO）就地重新拉起付费墙
 * @param onSessionChanged 登录态变化后通知调用方重读 [AccountStore]（顶栏/付费墙的账号名要跟着变）
 */
@Composable
fun MinePage(
    show: Boolean,
    isActivated: Boolean,
    account: AccountStore.Snapshot?,
    trialManager: TrialManager,
    onOpenLogin: () -> Unit,
    onOpenPro: () -> Unit,
    onSessionChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    // 注销是破坏性操作：页面上的「注销账号」只负责打开独立的确认弹窗
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 激活记录（时间 + 支付方式）只在页面打开时读一次 prefs，不在组合期反复读
    val activation = remember(show, isActivated) {
        if (show && isActivated) trialManager.activationRecord() else null
    }

    // 关闭页面时清掉上一次的临时态：确认弹窗/错误提示若留着，下次打开会直接站在
    // 一个用户没有发起过的「确认注销」上
    LaunchedEffect(show) {
        if (!show) {
            showDeleteConfirm = false
            message = ""
        }
    }

    // 系统返回键与左上角返回同一路径；处理中不允许退出，避免动作结果无处落地
    BackHandler(enabled = show) {
        if (!busy) onDismiss()
    }

    val logout: () -> Unit = {
        if (!busy) {
            busy = true
            message = ""
            scope.launch {
                AccountActions.logout(context)
                busy = false
                onSessionChanged()
            }
        }
    }

    val deleteAccount: () -> Unit = {
        if (!busy) {
            busy = true
            message = ""
            scope.launch {
                AccountActions.deleteAccount(context).fold(
                    onSuccess = {
                        busy = false
                        showDeleteConfirm = false
                        onSessionChanged()
                        Toast.makeText(
                            context,
                            "账号已注销，本机已解锁的风格不受影响",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onFailure = { error ->
                        busy = false
                        message = error.message ?: "注销失败，请稍后重试"
                    },
                )
            }
        }
    }

    // 从右侧滑入/滑出：与 App 其它「下一页」同一档时长（≤ EmphasizedMs），不叠加第二套动画
    AnimatedVisibility(
        visible = show,
        enter = slideInHorizontally(
            // 位移走弹簧（设计规范：位移类动效一律弹簧），只有淡入淡出走 tween
            animationSpec = AppMotion.emphasized(),
            initialOffsetX = { it },
        ) + fadeIn(tween(AppMotion.FastMs)),
        exit = slideOutHorizontally(
            animationSpec = AppMotion.emphasized(),
            targetOffsetX = { it },
        ) + fadeOut(tween(AppMotion.FastMs)),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                // 页面和主界面在同一个窗口里：空区域（卡片以外的留白）也必须被本页接住，
                // 否则点击会穿透到底下的主界面（长按金句卡、翻页手势等）。不消费事件，
                // 只登记一个 pointer input 让命中测试止步于本页。
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent()
                    }
                },
            // 与 App 其它页面同一张页面底：白卡靠「比页面亮 + 软阴影」立起来
            color = darkBg,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                MinePageHeader(onBack = { if (!busy) onDismiss() })

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = AppSpacing.screen)
                        .padding(bottom = AppSpacing.sectionGap),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap),
                ) {
                    // —— 账号身份卡：会员账户昵称（优先昵称，没设才回退登录名） ——
                    AppSectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    // 与主按钮同一个填充色：selectBlue 配白字是本项目有意保留的对比度例外；
                                    // 不用 accentBlue 做渐变——暗色模式下它被提亮成浅天蓝，白字会糊成一片
                                    .background(selectBlue),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (account == null) {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                } else {
                                    AppText(
                                        text = account.displayName.take(1).uppercase(),
                                        // 16sp 墨色字级 + 覆写白色：46dp 圆里 14sp 会显得空
                                        role = AppTextRole.title,
                                        color = Color.White,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(AppSpacing.itemGap))
                            Column {
                                AppText(
                                    text = account?.displayName ?: "未登录",
                                    role = AppTextRole.title,
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.tight))
                                AppText(
                                    text = if (account == null) {
                                        "登录后，已购的 PRO 可以跨设备找回"
                                    } else {
                                        "会员账户 · @${account.username}"
                                    },
                                    role = AppTextRole.caption,
                                )
                            }
                        }
                        if (account == null) {
                            AppPrimaryButton(
                                text = "登录 / 注册",
                                onClick = { if (!busy) onOpenLogin() },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // —— 会员状态卡 ——
                    AppSectionLabel(text = "会员状态")
                    AppSectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isActivated) Icons.Filled.Verified else Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = if (isActivated) premiumGoldDeep else textTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.iconGap))
                            AppText(
                                text = if (isActivated) "PRO 永久会员" else "尚未激活 PRO",
                                role = AppTextRole.sectionTitleInk,
                                modifier = Modifier.weight(1f),
                            )
                            AppText(
                                text = if (isActivated) "已激活" else "待激活",
                                role = AppTextRole.label,
                                color = if (isActivated) successGreen else textTertiary,
                            )
                        }

                        if (isActivated) {
                            MineInfoRow(
                                label = "有效期",
                                value = "永久有效 · 一次买断",
                            )
                            MineInfoRow(
                                label = "激活时间",
                                value = activation?.let { formatActivationTime(it.activatedAt) }
                                    ?: "旧版本激活，未记录",
                            )
                            MineInfoRow(
                                label = "支付方式",
                                value = activation?.payMethod ?: "未记录",
                            )
                            AppText(
                                text = "已解锁全部内置风格与自定义背景图，后续新增风格免费更新。",
                                role = AppTextRole.caption,
                            )
                        } else {
                            AppText(
                                text = "激活后可解锁全部内置风格与自定义背景图，一次买断永久有效。",
                                role = AppTextRole.bodySecondary,
                            )
                            AppPrimaryButton(
                                text = "支付宝支付 ${ProPurchase.PRICE_TEXT}",
                                onClick = { if (!busy) onOpenPro() },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // —— 账号操作：退出登录 / 注销账号，各自一条独立按钮 ——
                    if (account != null) {
                        AppSectionLabel(text = "账号操作")
                        AppSectionCard {
                            AppSecondaryButton(
                                text = if (busy && !showDeleteConfirm) "处理中…" else "退出登录",
                                onClick = logout,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppText(
                                text = "退出登录不会收回已解锁的风格。",
                                role = AppTextRole.micro,
                            )
                            // 独立按钮：只打开二次确认弹窗，不在页面里内联展开确认区
                            AppSecondaryButton(
                                text = "注销账号",
                                onClick = {
                                    if (!busy) {
                                        message = ""
                                        showDeleteConfirm = true
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                contentColor = dangerRed,
                            )
                        }
                    }

                    AppText(
                        text = "句阁 v${BuildConfig.VERSION_NAME} · 一次买断 · 永久有效",
                        role = AppTextRole.micro,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    // —— 注销二次确认：独立弹窗；页面本身不再内联展开确认区 ——
    AppDialog(
        visible = showDeleteConfirm,
        onDismissRequest = { if (!busy) showDeleteConfirm = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AppDialogSurface {
            AppDialogTitle("注销账号")
            AppText(
                text = "注销后将永久删除服务端账号与订单绑定信息，且无法恢复、无法再通过本账号找回 PRO。" +
                    "本机已解锁的风格不受影响。",
                role = AppTextRole.caption,
                color = dangerRed,
            )
            if (message.isNotEmpty()) {
                AppText(
                    text = message,
                    role = AppTextRole.caption,
                    color = dangerRed,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.controlGap, Alignment.End),
            ) {
                AppTextButton(
                    text = "取消",
                    onClick = { if (!busy) showDeleteConfirm = false },
                    enabled = !busy,
                    color = textTertiary,
                )
                AppDangerButton(
                    text = if (busy) "注销中…" else "确认注销",
                    onClick = deleteAccount,
                    enabled = !busy,
                )
            }
        }
    }
}

/** 页面头部：左上角返回 + 居中标题，标题字级与其它子页面一致（18sp 弹窗标题同档） */
@Composable
private fun MinePageHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.controlGap, vertical = AppSpacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.size(24.dp),
            )
        }
        AppText(
            text = "我的",
            role = AppTextRole.dialogTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        // 右侧留出与返回键同宽的占位，标题才是真正居中
        Spacer(modifier = Modifier.size(48.dp))
    }
}

/** 「我的」页信息行：左标签（次级色）+ 右值（强调墨色），行高随字号自适应 */
@Composable
private fun MineInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标签吃剩余宽度、值按内容宽：长值（如激活时间）不会把标签挤出屏幕
        AppText(
            text = label,
            role = AppTextRole.bodySecondary,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(AppSpacing.itemGap))
        AppText(text = value, role = AppTextRole.bodyStrong)
    }
}
