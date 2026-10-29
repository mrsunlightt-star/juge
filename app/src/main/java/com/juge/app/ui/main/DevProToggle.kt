package com.juge.app.ui.main

import androidx.compose.foundation.background
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
import com.juge.app.ui.AppText
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.disabledGray
import com.juge.app.ui.theme.premiumGoldDeep
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.successGreen

/**
 * ⚠️ 开发期临时控件：一键切换 PRO 状态。
 *
 * 直接读写 TrialManager 的真实存储，所以切到 PRO 后付费墙、预设锁定态、
 * 桌面组件徽章的表现与真实购买完全一致，便于逐项验证。
 * 发布前删除本函数及顶部栏中的调用点。
 *
 * 胶囊不画描边（2026-10 改版：结构性描边一律去掉）：底色用 surfaceSubtle
 * 从英雄区上「浮」起一档，金色只留在文字与开关状态上表达。
 */
@Composable
fun DevProToggle(activated: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(AppShape.pill)
            .background(surfaceSubtle)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            text = "开发",
            role = AppTextRole.micro,
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
