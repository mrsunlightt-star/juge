package com.juge.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.ui.theme.selectBlue

/**
 * 苹果风分段控制器：「跃然纸上 / 个性定制」。
 *
 * 从 MainAppScreen 原样搬出（第 3/3 步拆 MainActivity），指示器仍跟手 Pager 的
 * 实时位移；点击页签的翻页动作由 [onSelectTab] 交回调用方。
 */
@Composable
fun SubTabSwitcher(
    pagerState: PagerState,
    onSelectTab: (Int) -> Unit
) {
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
        val indicatorPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
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
                val tabTint = if (pagerState.currentPage == tabKeys.indexOf(key)) {
                    Color.White
                } else {
                    Color(0xFF64748B)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            onSelectTab(tabKeys.indexOf(key))
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
}
