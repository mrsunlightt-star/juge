package com.juge.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import com.juge.app.data.WidgetShape
import com.juge.app.render.PetFrameRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 三款萌宠花框是 4×4 组件，素材可视主体也必须铺满方形画布。
 *
 * 这里锁两层：素材透明外圈不能过大；正文安全区不能因为沿用旧透明边坐标又被缩窄。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PetFrameLayoutTest {

    private val drawableByShape = mapOf(
        WidgetShape.SPRING_ARRIVAL to R.drawable.spring_arrival,
        WidgetShape.SPACE_DOG to R.drawable.space_dog,
        WidgetShape.Q_PIRATE_CREW to R.drawable.q_pirate_crew,
    )

    @Test
    fun `三款花框素材的可视主体至少覆盖方版画布九成`() {
        val resources = RuntimeEnvironment.getApplication().resources
        for ((shape, drawableRes) in drawableByShape) {
            val bitmap = BitmapFactory.decodeResource(resources, drawableRes)
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(
                    pixels,
                    0,
                    bitmap.width,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                )
                var minX = bitmap.width
                var minY = bitmap.height
                var maxX = -1
                var maxY = -1
                for (y in 0 until bitmap.height) {
                    val row = y * bitmap.width
                    for (x in 0 until bitmap.width) {
                        if (Color.alpha(pixels[row + x]) <= 8) continue
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }

                assertTrue("$shape 素材没有任何可见像素", maxX >= minX && maxY >= minY)
                val visibleWidthRatio = (maxX - minX + 1).toFloat() / bitmap.width
                val visibleHeightRatio = (maxY - minY + 1).toFloat() / bitmap.height
                assertTrue(
                    "$shape 在 4×4 画布上的可视宽度只有 ${"%.1f".format(visibleWidthRatio * 100)}%",
                    visibleWidthRatio >= 0.90f,
                )
                assertTrue(
                    "$shape 在 4×4 画布上的可视高度只有 ${"%.1f".format(visibleHeightRatio * 100)}%",
                    visibleHeightRatio >= 0.90f,
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun `正文安全区保留其他四四方卡级别的可用宽度与高度`() {
        val outerRect = RectF(0f, 0f, 1000f, 1000f)
        for (shape in drawableByShape.keys) {
            val textRect = PetFrameRenderer.textRect(shape, outerRect)
            assertTrue(
                "$shape 正文宽度只有 ${"%.1f".format(textRect.width() / 10)}%",
                textRect.width() / outerRect.width() >= 0.59f,
            )
            assertTrue(
                "$shape 正文高度只有 ${"%.1f".format(textRect.height() / 10)}%",
                textRect.height() / outerRect.height() >= 0.52f,
            )
        }
    }

    @Test
    fun `Q萌海盗团正文区与承托层对齐画框窗口中心`() {
        val outerRect = RectF(0f, 0f, 1000f, 1000f)
        val textRect = PetFrameRenderer.textRect(WidgetShape.Q_PIRATE_CREW, outerRect)
        val backingRect = requireNotNull(
            PetFrameRenderer.textBackingRect(WidgetShape.Q_PIRATE_CREW, outerRect)
        )

        assertEquals("正文区应横向居中", 0.466f, textRect.centerX() / outerRect.width(), 0.002f)
        assertEquals("承托层应横向居中", 0.466f, backingRect.centerX() / outerRect.width(), 0.002f)
        assertTrue("正文区不得超出承托层", textRect.left >= backingRect.left)
        assertTrue("正文区不得超出承托层", textRect.top >= backingRect.top)
        assertTrue("正文区不得超出承托层", textRect.right <= backingRect.right)
        assertTrue("正文区不得超出承托层", textRect.bottom <= backingRect.bottom)
        assertTrue("正文区宽度不应继续缩窄", textRect.width() / outerRect.width() >= 0.62f)
        assertTrue("正文区高度不应继续缩窄", textRect.height() / outerRect.height() >= 0.56f)
        assertTrue("承托层应接近画框内沿", backingRect.width() / outerRect.width() >= 0.70f)
        assertTrue("承托层应接近画框内沿", backingRect.height() / outerRect.height() >= 0.64f)
    }
}
