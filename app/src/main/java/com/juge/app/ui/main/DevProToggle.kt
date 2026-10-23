package com.juge.app.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juge.app.data.TrialManager
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.disabledGray
import com.juge.app.ui.theme.premiumGoldDeep
import com.juge.app.ui.theme.successGreen

/**
 * ⚠️ 开发期临时控件：一键切换 PRO 状态。
 *
 * 直接读写 TrialManager 的真实存储，所以切到 PRO 后付费墙、预设锁定态、
 * 桌面组件徽章的表现与真实购买完全一致，便于逐项验证。
 * 发布前删除本函数及顶部栏中的调用点。
 */
@Composable
fun DevProToggle(activated: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(AppShape.pill)
            .background(Color.White.copy(alpha = 0.92f))
            .border(BorderStroke(1.dp, premiumGoldDeep), AppShape.pill)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "开发",
            style = AppType.micro,
            color = premiumGoldDeep
        )
        Switch(
            checked = activated,
            onCheckedChange = onToggle,
            modifier = Modifier.scale(0.7f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = successGreen,
                checkedBorderColor = successGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = disabledGray,
                uncheckedBorderColor = disabledGray
            )
        )
    }
}
