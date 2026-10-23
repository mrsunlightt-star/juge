package com.juge.app.ui.main

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
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
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.brandInkSoft
import com.juge.app.ui.theme.premiumGold
import com.juge.app.ui.theme.premiumGoldEnd
import com.juge.app.ui.theme.premiumGoldInk
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textPrimary
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
 * 顶部导航栏：品牌标题 + 刷新按钮 + PRO 徽章 + 开发开关。
 *
 * 从 MainAppScreen 原样搬出（第 3/3 步拆 MainActivity），只把内联闭包改成入参：
 * 刷新结果、激活状态变化、打开支付弹窗三个动作交回调用方。
 */
@Composable
fun MainTopBar(
    isActivated: Boolean,
    dbHelper: DbHelper,
    trialManager: TrialManager,
    scope: CoroutineScope,
    onReloaded: (List<Reminder>, List<Category>, List<WidgetConfig>) -> Unit,
    onActivatedChange: (Boolean) -> Unit,
    onOpenPro: () -> Unit
) {
    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
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
                        .shadow(2.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = brandInkSoft,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新数据",
                            tint = brandInkSoft,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            val badgeBg = if (isActivated) {
                Brush.horizontalGradient(listOf(selectBlue, selectBlue))
            } else {
                Brush.horizontalGradient(listOf(premiumGold, premiumGoldEnd))
            }
            // 金色底用深棕字，既保留 PRO 的金色识别，又满足 4.5:1
            val badgeContent = if (isActivated) textPrimary else premiumGoldInk

            // 胶囊视觉高约 28dp，外层 48dp 高度补足触控热区
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(AppShape.pill)
                    .clickable(
                        onClickLabel = "打开 PRO 激活",
                        role = Role.Button,
                    ) { onOpenPro() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(AppShape.pill)
                        .background(badgeBg)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), AppShape.pill)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 扁平线性图标替代实体 emoji：已激活用认证徽章，未激活用Premium徽章
                    Icon(
                        imageVector = if (isActivated) Icons.Filled.Verified else Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = badgeContent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isActivated) "PRO" else "激活PRO ${ProPurchase.PRICE_TEXT}",
                        style = AppType.label,
                        color = badgeContent
                    )
                }
            }

            // ⚠️ 开发期临时开关：一键在「已激活 / 未激活」之间切换，用于验证付费墙与锁定态。
            // 发布前必须整块删除——留着等于把付费墙拆了。
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
