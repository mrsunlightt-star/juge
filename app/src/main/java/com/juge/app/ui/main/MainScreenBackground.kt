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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juge.app.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.LocalAppDarkTheme
import com.juge.app.ui.theme.auroraMint
import com.juge.app.ui.theme.auroraMist
import com.juge.app.ui.theme.auroraSky
import com.juge.app.ui.theme.darkBg
import com.juge.app.ui.theme.gridLine

/**
 * 主界面底板：顶部「阳光海滩」图 + 薄荷极光渐变，内部套 Scaffold 与纵向 Column。
 * 方格网只保留在抽屉上沿以上（被顶图盖住，仅首帧兜底），不再透过抽屉毛玻璃显形。
 *
 * 顶部预览区（顶栏 + 组件预览 + 提示行）铺一张明亮的海滩插画，图像顶部是蓝天，
 * 因此顶栏文字保持白色并加一层暗压与文字阴影；页面其余部分保留原来的
 * 薄荷极光渐变，底部抽屉照旧压在图像下缘。抽屉是毛玻璃，透出来的是纯极光色，
 * 不再叠方格网——网格线会从卡片缝隙里透成一层方格纹理。
 *
 * [heroHeight] 是顶部图片的高度，由调用方测量抽屉上沿后传入，图片下缘与抽屉上沿对齐；
 * 测到之前可先传估算值，测量完成后会自动贴合。
 * [heroImageRes] / [heroAlignment] 用来换图时调整构图：`sunny_beach` 已按运行时可见区域
 * 预裁成右对齐构图（遮阳伞与沙滩在右），运行时只剩极小的等比裁切余量；
 * 换成主体在左侧的素材时，把 [heroAlignment] 改成 `Alignment.TopStart` 或 `TopCenter`。
 *
 * 图片底部不再渐隐到 [darkBg]：那样画面会一路"溶"进抽屉的填充色，接缝两侧色值完全相同，
 * 边界也就消失了。现在改为在最后约 10% 的高度把画面压暗，图片下缘因此比画面整体更深、
 * 抽屉比它更亮，接缝处重新出现明暗落差；这段压暗同时充当抽屉上沿的投影带。
 * 图片下缘以下铺平坦的页面底色：斜向极光渐变的浅尾巴在半透明面板后会产生
 * 抖动颗粒，垫平后玻璃后面恢复干净（面板的「透」由顶边模糊的图片带提供）。
 */
@Composable
fun MainScreenBackground(
    heroHeight: Dp = 380.dp,
    heroImageRes: Int = R.drawable.sunny_beach,
    heroAlignment: Alignment = Alignment.TopEnd,
    hazeState: HazeState,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = darkBg
    ) {
        // 极光/网格/底色令牌在组合期解析成局部量：drawBehind 是绘制期 lambda，
        // 不能直接调用 @Composable 取色。
        val auroraStart = auroraMint // 鲜明深薄荷 (Rich Vibrant Mint)
        val auroraMid = auroraSky // 晴空澄澈天蓝 (Sky Cyan)
        val auroraFade = auroraMist.copy(alpha = 0.50f) // 晨露浅青
        val pageBg = darkBg
        val grid = gridLine
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景层单独作为 hazeSource：它必须是抽屉的兄弟而不是祖先。
            // Haze 只采样「zIndex 低于最近祖先 source」的区域；source 作为祖先时
            // 它自己的区域会被过滤掉，抽屉的 hazeEffect 会静默失效（不模糊、也不上 tint）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .drawBehind {
                        // 1. 鲜明浓郁的薄荷极光渐变 (从生机深薄荷绿渐变至晴空天蓝)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    auroraStart,
                                    auroraMid,
                                    auroraFade,
                                    pageBg  // 渐入页面底色
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height * 0.44f)
                            )
                        )

                        // 2. 网站同款精细方格网 (Subtle Clean Grid)
                        //    只画在抽屉上沿以上：这一段被顶部海滩图完全盖住，
                        //    仅在首帧/无图时作为兜底露出来。
                        val stroke = 1.dp.toPx()
                        val heroPx = heroHeight.toPx().coerceIn(0f, size.height)
                        val gridSize = 24.dp.toPx()

                        var x = 0f
                        while (x < size.width) {
                            drawLine(grid, Offset(x, 0f), Offset(x, heroPx), stroke)
                            x += gridSize
                        }
                        var y = 0f
                        while (y < heroPx) {
                            drawLine(grid, Offset(0f, y), Offset(size.width, y), stroke)
                            y += gridSize
                        }

                        // 3. 抽屉上沿以下铺回**平坦的页面底色**：斜向极光渐变在屏幕左下
                        //    还剩一截很浅的尾巴（mist→底色只差一两个色阶），8-bit 量化的
                        //    抖动点透过半透明面板会显成一层密密麻麻的颗粒（用户反馈）。
                        //    平坦底色没有渐变可抖，玻璃后面恢复干净；玻璃的「透」由
                        //        顶边那截模糊的海滩图（HeroBackdrop 多画的一个圆角带）
                        //    和更低的 tint 提供，不靠背景色洗。
                        drawRect(
                            color = pageBg,
                            topLeft = Offset(0f, heroPx),
                            size = Size(size.width, size.height - heroPx)
                        )
                    }
            ) {
                HeroBackdrop(
                    height = heroHeight,
                    imageRes = heroImageRes,
                    alignment = heroAlignment,
                )
            }

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
 * - 素材已按运行时可见区域预裁（只保留右侧遮阳伞与沙滩，左侧海浪已裁掉），
 *   运行时 `TopEnd` + `Crop` 只做极小的等比裁切，构图不再依赖运行期裁切；
 * - 顶部加一层暗压、保证白色顶栏文字在亮蓝天空上也能读；
 * - 图片高度取「绘制盒高度」与「可见 hero × [HERO_IMAGE_OVERSCAN]」的较大值：
 *   前者保证圆角缺口不漏底，后者只在特别高的 hero 上保留一点构图余量；
 * - 绘制区比可见 hero 多出一个抽屉圆角高度：抽屉顶部左右圆角切出的缺口必须露出背景图。
 *   缺口若露出页面底色，它会和面板几乎同色，整块面板就被读成直角（真机反馈）。
 *   多出来的部分被面板本体盖住，只有两个圆角缺口能看见；
 * - 图片本身也至少画满这个扩展后的绘制盒：图片若在缺口下缘提前结束，剩下的几 dp 会露出
 *   浅蓝方格底，在圆角处形成一条横穿缺口的直线截断（真机反馈）；
 * - 从抽屉上沿往下，图片（连同底部压暗）渐隐到透明：图片下缘的硬边即使正好落在缺口底部，
 *   也会透过毛玻璃显成一条横线，并在圆角最下端与圆弧相交。渐隐只发生在抽屉上沿以下，
 *   接缝处的明暗落差保留；
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
    val density = LocalDensity.current
    val visibleHeightPx = with(density) { height.toPx() }
    val fadeHeightPx = with(density) { AppShape.sheetCornerRadius.toPx() }
    // 绘制盒比可见 hero 多一个抽屉圆角：圆角缺口露出的就是这张图。
    val boxHeight = height + AppShape.sheetCornerRadius
    // 图片必须盖满整个绘制盒。只按「可见高度 × 倍率」画，在常见屏幕上会比盒高少几个 dp，
    // 缺口最下缘就露出页面底色，圆角处出现一条直线截断。按盒高等比放大的额外裁切不到 2%，
    // 远不如缺口露底显眼。
    val imageHeight = maxOf(height * HERO_IMAGE_OVERSCAN, boxHeight)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 多画一个抽屉圆角的高度：圆角缺口必须露出背景图，露页面底色就会被读成直角。
            // 多出来的部分被面板本体盖住，只有左右两个圆角缺口可见。
            .height(boxHeight)
            .clipToBounds(),
    ) {
        // 画面与底部压暗先合成到同一个离屏层，再整体向下渐隐。
        // 渐隐从抽屉上沿开始，接缝处的色差不受影响。
        // requiredHeight 用来撑开离屏层：外层盒高度固定，普通 height 会被父约束收敛回原高，
        // 多绘制的余量根本不会发生（画面也就不会下移）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(imageHeight)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            1f to Color.Transparent,
                            startY = visibleHeightPx,
                            endY = visibleHeightPx + fadeHeightPx,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
            // 夜间模式同一张海滩图整体加深压暗：顶栏白字本就带阴影，压暗后依然可读；
            // 画面变夜色海滩，同时与深色抽屉玻璃的接缝落差也保持住
            val heroDimStops = if (LocalAppDarkTheme.current) {
                listOf(
                    0.00f to Color.Black.copy(alpha = 0.64f),
                    0.16f to Color.Black.copy(alpha = 0.52f),
                    0.32f to Color.Black.copy(alpha = 0.40f),
                    0.90f to Color.Black.copy(alpha = 0.40f),
                    1.00f to Color.Black.copy(alpha = 0.48f),
                )
            } else {
                listOf(
                    0.00f to Color.Black.copy(alpha = 0.38f),
                    0.16f to Color.Black.copy(alpha = 0.20f),
                    0.32f to Color.Transparent,
                    0.90f to Color.Transparent,
                    1.00f to Color.Black.copy(alpha = 0.20f),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            *heroDimStops.toTypedArray(),
                            // 渐变以可见 hero 为终点，下方多画的圆角带继续沿用最深压暗色。
                            // 若按扩展后的总高做渐变，面板上沿只走到约 92%，压暗会被稀释掉。
                            startY = 0f,
                            endY = visibleHeightPx,
                        )
                    ),
            )
        }
    }
}

/** 图片相对 hero 区多画的高度倍率：底部约 7% 的高光沙带被裁掉，画面视觉上下移 */
private const val HERO_IMAGE_OVERSCAN = 1.07f
