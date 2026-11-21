package com.juge.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import com.juge.app.render.PetFrameRenderer
import com.juge.app.render.traits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 中华田园犬 / 熊猫宝宝是 4×2 横版萌宠花框。
 *
 * 这里锁素材画布、透明底、形状开关与正文安全区，避免后续误把边框裁圆角、
 * 或把默认尺寸改回方版。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidePetFrameLayoutTest {

    private val drawableByShape = mapOf(
        WidgetShape.CHINESE_RURAL_DOG to R.drawable.chinese_rural_dog,
        WidgetShape.PANDA_BABY to R.drawable.panda_baby,
    )

    @Test
    fun `大眼猫咪保持二比一画布且四角透明`() {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = RuntimeEnvironment.getApplication().resources
            .openRawResource(R.drawable.big_eye_cat)
            .use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("大眼猫咪素材无法解码")
        try {
            assertEquals(2176, bitmap.width)
            assertEquals(1088, bitmap.height)
            for (corner in intArrayOf(
                bitmap.getPixel(0, 0),
                bitmap.getPixel(bitmap.width - 1, 0),
                bitmap.getPixel(0, bitmap.height - 1),
                bitmap.getPixel(bitmap.width - 1, bitmap.height - 1),
            )) {
                assertTrue("大眼猫咪素材四角必须透明", Color.alpha(corner) <= 8)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `大眼猫咪禁用底色与圆角且正文避开展示装饰`() {
        val shape = WidgetShape.BIG_EYE_CAT
        val traits = shape.traits()
        assertTrue("大眼猫咪应跳过整卡底色与投影", traits.transparentCard)
        assertTrue("大眼猫咪必须由渲染层强制直角", traits.forcesSquareCorners)
        assertFalse("大眼猫咪不应支持背景色", WidgetStyle.supportsBackgroundColor(shape))

        val outerRect = RectF(0f, 0f, 2000f, 1000f)
        val textRect = PetFrameRenderer.textRect(shape, outerRect)
        assertTrue("正文左侧离毛绒边框太近", textRect.left / outerRect.width() >= 0.18f)
        assertTrue("正文右侧进入猫咪区域", textRect.right / outerRect.width() <= 0.54f)
        assertTrue("正文顶部离边框太近", textRect.top / outerRect.height() >= 0.16f)
        assertTrue("正文底部离边框太近", textRect.bottom / outerRect.height() <= 0.83f)
        assertTrue("正文宽度不足", textRect.width() / outerRect.width() >= 0.32f)
        assertTrue("正文高度不足", textRect.height() / outerRect.height() >= 0.62f)
    }

    @Test
    fun `横版萌宠素材保持二比一画布且四角透明`() {
        val resources = RuntimeEnvironment.getApplication().resources
        for ((shape, drawableRes) in drawableByShape) {
            val options = BitmapFactory.Options().apply { inScaled = false }
            val bitmap = resources.openRawResource(drawableRes).use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("$shape 素材无法解码")
            try {
                assertEquals("$shape 素材宽度变了", 2176, bitmap.width)
                assertEquals("$shape 素材高度变了", 1088, bitmap.height)
                assertTrue(
                    "$shape 素材不再是 2:1",
                    abs(bitmap.width.toFloat() / bitmap.height - 2f) < 0.001f,
                )

                val corners = intArrayOf(
                    bitmap.getPixel(0, 0),
                    bitmap.getPixel(bitmap.width - 1, 0),
                    bitmap.getPixel(0, bitmap.height - 1),
                    bitmap.getPixel(bitmap.width - 1, bitmap.height - 1),
                )
                for (corner in corners) {
                    assertTrue("$shape 素材四角必须透明", Color.alpha(corner) <= 8)
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun `横版萌宠花框禁用底色与圆角并保留正文窗口`() {
        val outerRect = RectF(0f, 0f, 2000f, 1000f)
        for (shape in drawableByShape.keys) {
            val traits = shape.traits()
            assertTrue("$shape 应跳过整卡底色与投影", traits.transparentCard)
            assertTrue("$shape 必须由渲染层强制直角", traits.forcesSquareCorners)
            assertFalse("$shape 不应支持背景色", WidgetStyle.supportsBackgroundColor(shape))

            val textRect = PetFrameRenderer.textRect(shape, outerRect)
            assertTrue("$shape 正文左侧离边框太近", textRect.left / outerRect.width() >= 0.09f)
            assertTrue("$shape 正文右侧离边框太近", textRect.right / outerRect.width() <= 0.91f)
            assertTrue("$shape 正文顶部离边框太近", textRect.top / outerRect.height() >= 0.30f)
            assertTrue("$shape 正文底部离边框太近", textRect.bottom / outerRect.height() <= 0.86f)
            assertTrue("$shape 正文宽度不足", textRect.width() / outerRect.width() >= 0.78f)
            assertTrue("$shape 正文高度不足", textRect.height() / outerRect.height() >= 0.54f)
        }
    }
}
