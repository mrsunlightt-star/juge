package com.juge.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.juge.app.ui.AppText
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppShadows
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.surfaceRaised
import com.juge.app.ui.theme.panelTrack
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textWhite

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
    val haptics = rememberAppHaptics()
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

        // 视觉底板：42dp 高，居中；上下各 3dp 留给触控热区。
        // 整条控制是「凹槽轨道 + 滑块」：轨道用 panelTrack（比面板/卡片深一档），
        // 滑块用 surfaceRaised（日间白、夜间比卡片再亮一档），层次完全由明度台阶表达。
        // 旧版靠 1dp borderBlue 给白色轨道勾边（面板是近白毛玻璃，纯白胶囊会融进去）；
        // 2026-10 改版去掉结构性描边后，改用「轨道比面板深」把控件嵌进面板，
        // 白色滑块再靠 shadow 浮在轨道上——不画线也能读出「这是一个控件」。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 3.dp)
                .background(panelTrack, AppShape.pill)
        )

        // 背景滑块：36dp 高，落在轨道内侧。选白色而不是强调色，是因为「轨道凹槽 + 白滑块」
        // 就是 iOS 分段控制的明度层次；选中态靠滑块本身 + 文字变墨色表达。
        Box(
            modifier = Modifier
                .offset(x = selectedOffset, y = 6.dp)
                .width(indicatorWidth)
                .height(36.dp)
                .shadow(
                    elevation = AppShadows.control,
                    shape = AppShape.chip,
                    clip = false,
                    ambientColor = AppShadows.ambientShadow,
                    spotColor = AppShadows.spotShadow,
                )
                .background(surfaceRaised, AppShape.chip)
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
                // 文字/图标颜色直接跟滑块位移插值：滑块是 cardBg（白），
                // 选中侧用墨色 textWhite 才读得出来，未选中侧保持 textGray。
                val tabTint = lerp(textGray, textWhite, selectedFraction)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = pagerState.currentPage == index,
                            // 不画 Material 波纹：全宽 selectable 的矩形波纹与胶囊滑块割裂，
                            // 触感反馈（haptics.tap）+ 滑块弹簧位移就是按下反馈（iOS 分段控制同款）。
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (pagerState.currentPage != index) haptics.tap()
                                onSelectTab(index)
                            },
                            role = Role.Tab,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tabTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        AppText(
                            text = label,
                            role = AppTextRole.chip,
                            color = tabTint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
