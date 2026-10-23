package com.juge.app.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juge.app.R
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.auroraMint
import com.juge.app.ui.theme.auroraMist
import com.juge.app.ui.theme.auroraSky
import com.juge.app.ui.theme.darkBg
import com.juge.app.ui.theme.gridLine

/**
 * 主界面底板：顶部「阳光海滩」图 + 薄荷极光渐变 + 方格网，内部套 Scaffold 与纵向 Column。
 *
 * 顶部预览区（顶栏 + 组件预览 + 提示行）铺一张明亮的海滩插画，图像顶部是蓝天，
 * 因此顶栏文字保持白色并加一层暗压与文字阴影；页面其余部分仍保留原来的
 * 薄荷极光渐变与方格网，底部抽屉照旧压在图像下缘。
 *
 * [heroHeight] 是顶部图片的高度，由调用方测量抽屉上沿后传入，图片下缘与抽屉上沿对齐；
 * 测到之前可先传估算值，测量完成后会自动贴合。
 * [heroImageRes] / [heroAlignment] 用来换图时调整构图：默认把画面右侧的遮阳伞留在
 * 预览卡右侧的可见区；换回月亮图时应改成 `Alignment.TopStart` 或 `TopCenter`。
 *
 * 图片底部不再渐隐到 [darkBg]：那样画面会一路"溶"进抽屉的填充色，接缝两侧色值完全相同，
 * 边界也就消失了。现在改为在最后约 10% 的高度把画面压暗，图片下缘因此比画面整体更深、
 * 抽屉比它更亮，接缝处重新出现明暗落差；这段压暗同时充当抽屉上沿的投影带。
 * 图片下缘以下统一铺 [darkBg]，抽屉顶部圆角露出的仍是页面底色。
 */
@Composable
fun MainScreenBackground(
    heroHeight: Dp = 380.dp,
    heroImageRes: Int = R.drawable.sunny_beach,
    heroAlignment: Alignment = Alignment.TopEnd,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = darkBg
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // 1. 鲜明浓郁的薄荷极光渐变 (从生机深薄荷绿渐变至晴空天蓝)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                auroraMint, // 鲜明深薄荷 (Rich Vibrant Mint)
                                auroraSky, // 晴空澄澈天蓝 (Sky Cyan)
                                auroraMist.copy(alpha = 0.50f), // 晨露浅青
                                darkBg  // 渐入页面底色
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height * 0.44f)
                        )
                    )

                    // 2. 网站同款精细方格网 (Subtle Clean Grid)
                    val gridSize = 24.dp.toPx()
                    val gridColor = gridLine
                    val stroke = 1.dp.toPx()

                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = stroke
                        )
                        x += gridSize
                    }

                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = stroke
                        )
                        y += gridSize
                    }

                    // 3. 抽屉上沿以下统一铺页面底色：图片下缘与抽屉之间的缝隙、
                    //    以及抽屉顶部圆角缺口露出的区域都从这里兜底，
                    //    不会再露出极光渐变或方格网。缺口上压的阴影在 HeroBackdrop 里。
                    val heroPx = heroHeight.toPx()
                    if (heroPx < size.height) {
                        drawRect(
                            color = darkBg,
                            topLeft = Offset(0f, heroPx),
                            size = Size(size.width, size.height - heroPx)
                        )
                    }
                }
        ) {
            HeroBackdrop(
                height = heroHeight,
                imageRes = heroImageRes,
                alignment = heroAlignment,
            )

            Scaffold(
                containerColor = Color.Transparent
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 顶部海滩图。
 *
 * - 从屏幕最顶端开始绘制，因此状态栏区域也是蓝天；
 * - 裁切默认右上对齐，保留右侧遮阳伞与沙滩，裁掉的是左侧海浪；
 * - 顶部加一层暗压、保证白色顶栏文字在亮蓝天空上也能读；
 * - 图片比 hero 区多画 [HERO_IMAGE_OVERSCAN] 高度：横图在接近方形的 hero 里高度本就没有
 *   余量，多出来的部分让画面整体下移一点点，底部最亮的高光沙带被裁出可见区，
 *   与下方抽屉的接缝不再顶着一条亮带；
 * - 绘制区比可见 hero 多出一个抽屉圆角高度：抽屉顶部左右圆角切出的缺口必须露出背景图。
 *   缺口若露出页面底色，它会和面板几乎同色，整块面板就被读成直角（真机反馈）。
 *   多出来的部分被面板本体盖住，只有两个圆角缺口能看见；
 * - 接缝两侧必须有色差：页面底色是浅冷灰，图片若淡出到它，边界就没了。现在最后约 10%
 *   改为向下压暗（最深处 20% 黑）：图片下缘比画面整体更深，抽屉的亮面才立得起来，
 *   这段压暗也正好替代抽屉自己的上沿投影。
 */
@Composable
private fun HeroBackdrop(
    height: Dp,
    imageRes: Int,
    alignment: Alignment,
) {
    val visibleHeightPx = with(LocalDensity.current) { height.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 多画一个抽屉圆角的高度：圆角缺口必须露出背景图，露页面底色就会被读成直角。
            // 多出来的部分被面板本体盖住，只有左右两个圆角缺口可见。
            .height(height + AppShape.sheetCornerRadius)
            .clipToBounds(),
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = alignment,
            modifier = Modifier
                .fillMaxWidth()
                // 必须用 requiredHeight：外层盒高度固定，普通 height 会被父约束收敛回原高，
                // 多绘制的余量根本不会发生（画面也就不会下移）。
                // 高度仍按**可见 hero** 计算，不能跟着扩展后的盒高走，
                // 否则整张图会在扩大渲染盒时再放大 ~8%，构图跟着变。
                .requiredHeight(height * HERO_IMAGE_OVERSCAN),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.38f),
                        0.16f to Color.Black.copy(alpha = 0.20f),
                        0.32f to Color.Transparent,
                        0.90f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.20f),
                        // 渐变以可见 hero 为终点，下方多画的圆角带继续沿用最深压暗色。
                        // 若按扩展后的总高做渐变，面板上沿只走到约 92%，压暗会被稀释掉。
                        startY = 0f,
                        endY = visibleHeightPx,
                    )
                ),
        )
    }
}

/** 图片相对 hero 区多画的高度倍率：底部约 7% 的高光沙带被裁掉，画面视觉上下移 */
private const val HERO_IMAGE_OVERSCAN = 1.07f
