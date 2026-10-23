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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juge.app.R
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
 * 图片底部用一段渐隐过渡到 [darkBg]，即使不同机型的状态栏/字体高度略有差异，
 * 也不会出现一条生硬的图片截断线；图片下缘以下统一铺 [darkBg]，
 * 抽屉顶部圆角露出的也是页面底色，不再残留极光蓝。
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
                    //    以及抽屉顶部圆角缺口露出的区域都是 darkBg，与抽屉连成一体，
                    //    不会再露出极光渐变或方格网。
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
 * - 底部渐隐到页面底色，和下方的米白抽屉自然衔接。
 */
@Composable
private fun HeroBackdrop(
    height: Dp,
    imageRes: Int,
    alignment: Alignment,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = alignment,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.38f),
                        0.16f to Color.Black.copy(alpha = 0.20f),
                        0.32f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1.00f to darkBg,
                    )
                ),
        )
    }
}
