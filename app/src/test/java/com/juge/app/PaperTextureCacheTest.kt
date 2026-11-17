package com.juge.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.juge.app.render.PaperRenderer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 纸张纹理录制缓存的回归测试。
 *
 * 纹理的随机种子是固定常量（TEXTURE_SEED），同参数逐像素相同，却被每次重画
 * 重新创建 100~3800 个 Path / 圆点。桌面缩放时连续触发重画，这笔开销会成倍叠加。
 * 这里锁住两件事：同参数只录制一次、不同参数各自成条目（不会串图）。
 *
 * 用 Picture 而非 Bitmap 缓存的原因见 PaperRenderer 的注释：逐像素结果必须
 * 与拆分前完全一致，否则渲染指纹基线会红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaperTextureCacheTest {

    private fun draw(type: String, width: Int, height: Int, densityScale: Float = 1f) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            PaperRenderer.drawPaperTexture(
                Canvas(bitmap),
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                type,
                densityScale,
            )
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun sameParametersOnlyRecordOnce() {
        PaperRenderer.clearTextureCacheForTest()
        draw("PAPER", 120, 80)
        assertEquals(1, PaperRenderer.textureCacheSizeForTest())

        draw("PAPER", 120, 80)
        assertEquals(
            "同参数第二次绘制必须重放缓存，不能再录制一遍上千个 Path",
            1,
            PaperRenderer.textureCacheSizeForTest(),
        )
    }

    @Test
    fun differentParametersGetSeparateEntries() {
        PaperRenderer.clearTextureCacheForTest()
        draw("PAPER", 120, 80)
        draw("GRAIN", 120, 80)
        draw("PAPER", 121, 80)
        draw("PAPER", 120, 80, densityScale = 1.5f)
        assertEquals(
            "纹理类型 / 尺寸 / 密度都参与 key，任何一项不同都不能复用同一张录制结果",
            4,
            PaperRenderer.textureCacheSizeForTest(),
        )
    }
}
