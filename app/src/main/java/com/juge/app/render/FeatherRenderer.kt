package com.juge.app.render

import android.graphics.Path
import java.util.Random
import com.juge.app.WidgetCanvasRenderer.render

/**
 * 自 WidgetCanvasRenderer 拆分而来。成员由调用点 import，调用写法不变。
 */
internal object FeatherRenderer {

    fun drawFeatherLetterPath(path: Path, width: Float, height: Float, densityScale: Float) {
        val marginX = width * 0.06f
        val marginTop = height * 0.05f
        val marginBottom = height * 0.06f
        val left = marginX
        val right = width - marginX
        val top = marginTop
        val bottom = height - marginBottom
        val jitter = 2.0f * densityScale

        path.reset()
        val random = java.util.Random(2024L)
        path.moveTo(left, top)
        // 上边缘
        var x = left
        while (x < right) {
            x += 8f * densityScale
            if (x > right) x = right
            val jy = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(x, top + jy)
        }
        // 右边缘
        var y = top
        while (y < bottom) {
            y += 8f * densityScale
            if (y > bottom) y = bottom
            val jx = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(right + jx, y)
        }
        // 下边缘
        x = right
        while (x > left) {
            x -= 8f * densityScale
            if (x < left) x = left
            val jy = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(x, bottom + jy)
        }
        // 左边缘
        y = bottom
        while (y > top) {
            y -= 8f * densityScale
            if (y < top) y = top
            val jx = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(left + jx, y)
        }
        path.close()
    }

}
