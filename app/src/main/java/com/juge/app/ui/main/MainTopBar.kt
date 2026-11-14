package com.juge.app.ui.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.juge.app.ReminderWidgetProvider
import com.juge.app.data.Category
import com.juge.app.data.DbHelper
import com.juge.app.data.Reminder
import com.juge.app.data.TrialManager
import com.juge.app.data.WidgetConfig
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppShadows
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.LocalAppDarkTheme
import com.juge.app.ui.theme.brandInkSoft
import com.juge.app.ui.theme.premiumGold
import com.juge.app.ui.theme.premiumGoldEnd
import com.juge.app.ui.theme.premiumGoldInk
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.surfaceRaised
import com.juge.app.ui.theme.textWhite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 顶部英雄图上的白字阴影：亮色天空与深色夜空都能保持可读 */
private val HeroTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1.5f),
    blurRadius = 7f,
)

/**
 * 顶部导航栏：品牌标题 + 刷新按钮 + 夜色开关 + 会员入口 + 开发开关。
 *
 * 会员入口按激活状态分流（产品要求，2026-10）：
 * - **未激活**：保持金色的「激活PRO ￥3.99」胶囊，点击直接进付费墙——这是待激活用户
 *   在 App 顶端唯一且最醒目的转化入口；
 * - **已激活**：胶囊换成「我的」图标（与刷新/日夜同款 38dp 白圆），点开账户中心——
 *   买断之后 PRO 徽章不再提供任何动作，用户真正需要的是会员状态与账号操作。
 *
 * 三颗圆钮（刷新 / 日夜 / 我的）的底色与图标色跟随 App 主题：
 * - 日间：白圆 + 墨绿图标——英雄区是亮天空，白圆与白色卡片同一套明度语言；
 * - 夜间：`surfaceRaised` 暗色浮起面 + 霜白图标——英雄区整体压暗 52%~64%，
 *   再摆三块纯白圆就成了全屏最亮的东西，与「真正的暗黑背景」主题冲突。
 *
 * 从 MainAppScreen 原样搬出（第 3/3 步拆 MainActivity），只把内联闭包改成入参：
 * 刷新结果、激活状态变化、打开支付弹窗/账户中心四个动作交回调用方。
 */
@Composable
fun MainTopBar(
    isActivated: Boolean,
    dbHelper: DbHelper,
    trialManager: TrialManager,
    scope: CoroutineScope,
    onReloaded: (List<Reminder>, List<Category>, List<WidgetConfig>) -> Unit,
    onActivatedChange: (Boolean) -> Unit,
    onOpenPro: () -> Unit,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    var isRefreshing by remember { mutableStateOf(false) }
    // 顶栏圆钮的底色/图标色随主题：日间白圆墨绿（原有设计），夜间暗色浮起面 + 霜白
    val circleSurface = if (LocalAppDarkTheme.current) surfaceRaised else Color.White
    val circleTint = if (LocalAppDarkTheme.current) textWhite else brandInkSoft
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
                style = AppType.brand.copy(shadow = HeroTextShadow),
                color = Color.White
            )
            Text(
                text = "DeskQuotes",
                style = AppType.brandSub.copy(shadow = HeroTextShadow),
                color = Color.White.copy(alpha = 0.82f)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)
        ) {
            // 视觉仍是 38dp 白圆，热区借外层 48dp Box 补齐
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = !isRefreshing,
                        onClickLabel = "刷新数据",
                        role = Role.Button,
                    ) {
                        haptics.click()
                        isRefreshing = true
                        scope.launch {
                            try {
                                val (rems, cats, configs) = withContext(Dispatchers.IO) {
                                    Triple(
                                        dbHelper.getAllReminders(),
                                        dbHelper.getAllCategories(),
                                        dbHelper.getAllWidgetConfigs()
                                    )
                                }
                                onReloaded(rems, cats, configs)
                                ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                                Toast.makeText(context, "数据已同步刷新", Toast.LENGTH_SHORT).show()
                            } catch (t: Throwable) {
                                Toast.makeText(context, "刷新失败，请重试", Toast.LENGTH_SHORT).show()
                            } finally {
                                isRefreshing = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(
                            elevation = AppShadows.control,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = AppShadows.ambientShadow,
                            spotColor = AppShadows.spotShadow,
                        )
                        // 不画 1dp 描边（2026-10 改版）：38dp 白圆自身的填充 + 柔和阴影
                        // 已经能和英雄区的天空/压暗拉开边界，白描边在亮天空上本来也看不见。
                        .background(circleSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = circleTint,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新数据",
                            tint = circleTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 夜色开关：与刷新按钮同款 38dp 白圆 + 48dp 热区；英雄区夜间整体压暗，白圆日夜都成立
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        onClickLabel = if (isDarkTheme) "切换为日间模式" else "切换为夜间模式",
                        role = Role.Button,
                    ) {
                        haptics.tap()
                        onToggleDarkTheme()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(
                            elevation = AppShadows.control,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = AppShadows.ambientShadow,
                            spotColor = AppShadows.spotShadow,
                        )
                        // 同刷新圆：去掉白色 1dp 描边，只留白圆填充 + 阴影。
                        .background(circleSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (isDarkTheme) "切换为日间模式" else "切换为夜间模式",
                        tint = circleTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isActivated) {
                // 已激活：只留一枚静态 PRO 徽章。
                // 权益绑的是付款时的支付宝账号，本机没有账号可看、可退、可注销，
                // 因此不再提供「我的」页——把入口去掉，比留一个空页面更诚实。
                Row(
                    modifier = Modifier
                        .clip(AppShape.pill)
                        .background(Brush.horizontalGradient(listOf(premiumGold, premiumGoldEnd)), AppShape.pill)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = premiumGoldInk,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "PRO",
                        style = AppType.label,
                        color = premiumGoldInk
                    )
                }
            } else {
                // 未激活：保持原来的金色待激活胶囊（文案/配色/热区都不变，只有价格跟着定价走）
                val badgeBg = Brush.horizontalGradient(listOf(premiumGold, premiumGoldEnd))
                // 金色底用深棕字，既保留 PRO 的金色识别，又满足 4.5:1
                val badgeContent = premiumGoldInk

                // 胶囊视觉高约 28dp，外层 48dp 高度补足触控热区
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(AppShape.pill)
                        .clickable(
                            onClickLabel = "打开 PRO 激活",
                            role = Role.Button,
                        ) {
                            haptics.click()
                            onOpenPro()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(AppShape.pill)
                            .background(badgeBg)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 扁平线性图标替代实体 emoji：未激活用 Premium 徽章
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = null,
                            tint = badgeContent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "激活PRO ${ProPurchase.PRICE_TEXT}",
                            style = AppType.label,
                            color = badgeContent
                        )
                    }
                }
            }

            // ⚠️ 开发期临时开关：一键在「已激活 / 未激活」之间切换，用于验证付费墙与锁定态。
            // 只在 debug 构建组进 UI：注释里写的"发布前整块删除"靠人记得总会忘——
            // 它一旦随正式包发布，等于把付费墙拆了（顶部一枚开关即可全量解锁）。
            // 要恢复调试能力，构建变体选 debug 即可。
            if (com.juge.app.BuildConfig.DEBUG) {
                DevProToggle(
                    activated = isActivated,
                    onToggle = { checked ->
                        if (checked) {
                            trialManager.activate(TrialManager.PAY_METHOD_DEV)
                        } else {
                            trialManager.resetActivation()
                        }
                        onActivatedChange(checked)
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
    }
}
