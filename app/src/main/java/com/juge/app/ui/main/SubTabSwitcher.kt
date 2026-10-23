package com.juge.app.ui.main

import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textPrimary

/**
 * 苹果风分段控制器：「跃然纸上 / 个性定制」。
 *
 * 视觉底板仍是 42dp 高，但外层留 48dp：整块页签的上下 3dp 也属于点击热区，
 * 满足移动端最小触控目标。指示器跟手 Pager 的实时位移；点击翻页由 [onSelectTab] 交回调用方。
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
            .height(48.dp)
    ) {
        val width = maxWidth
        val indicatorWidth = (width - 6.dp) / 2

        // 指示器与文字颜色都直接读 pager 的实时位移：
        // 左滑到一半时两颗页签各占 50% 的选中色，不会出现“块已经滑过去、字还全灰”的割裂感。
        val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, 1f)
        val selectedOffset = 3.dp + indicatorWidth * position

        // 视觉底板：42dp 高，居中；上下各 3dp 留给触控热区
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 3.dp)
                .background(surfaceSubtle, AppShape.button)
        )

        // 背景滑块：36dp 高，落在底板内侧
        Box(
            modifier = Modifier
                .offset(x = selectedOffset, y = 6.dp)
                .width(indicatorWidth)
                .height(36.dp)
                .shadow(2.dp, AppShape.chip)
                .background(selectBlue, AppShape.chip)
        )

        // Tab 按钮：扁平线性图标 + 文字，整高 48dp 都是热区
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple("跃然纸上", Icons.AutoMirrored.Filled.MenuBook, 0),
                Triple("个性定制", Icons.Filled.Tune, 1)
            ).forEach { (label, icon, index) ->
                val selectedFraction =
                    (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)
                val tabTint = lerp(textGray, textPrimary, selectedFraction)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = pagerState.currentPage == index,
                            onClick = { onSelectTab(index) },
                            role = Role.Tab,
                        ),
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
                            style = AppType.chip.copy(fontWeight = FontWeight.Bold),
                            color = tabTint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
