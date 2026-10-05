package com.juge.app.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.juge.app.ui.theme.darkBg

/**
 * 主界面底板：薄荷极光渐变 + 方格网，内部套 Scaffold 与纵向 Column。
 *
 * 从 MainAppScreen 原样搬出（第 3/3 步拆 MainActivity），只把原来的 Column 内容
 * 换成 [content] 槽位；绘制命令与颜色未做任何改动。
 */
@Composable
fun MainScreenBackground(content: @Composable ColumnScope.() -> Unit) {
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
                                Color(0xFF5EEAD4), // 鲜明深薄荷 (Rich Vibrant Mint)
                                Color(0xFF38BDF8), // 晴空澄澈天蓝 (Sky Cyan)
                                Color(0xFFBAE6FD).copy(alpha = 0.50f), // 晨露浅青
                                darkBg  // 渐入页面底色
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height * 0.44f)
                        )
                    )

                    // 2. 网站同款精细方格网 (Subtle Clean Grid)
                    val gridSize = 24.dp.toPx()
                    val gridColor = Color(0xFF0F766E).copy(alpha = 0.12f)
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
                }
        ) {
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
