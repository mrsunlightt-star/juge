package com.juge.app.ui.main

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.ReminderWidgetProvider
import com.juge.app.data.Category
import com.juge.app.data.DbHelper
import com.juge.app.data.Reminder
import com.juge.app.data.TrialManager
import com.juge.app.data.WidgetConfig
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.theme.selectBlue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                            onReloaded(rems, cats, configs)
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

            val badgeBg = if (isActivated) {
                Brush.horizontalGradient(listOf(selectBlue, selectBlue))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(badgeBg)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), RoundedCornerShape(20.dp))
                    .clickable { onOpenPro() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 扁平线性图标替代实体 emoji：已激活用认证徽章，未激活用Premium徽章
                Icon(
                    imageVector = if (isActivated) Icons.Filled.Verified else Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isActivated) "PRO" else "激活PRO ${ProPurchase.PRICE_TEXT}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
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
