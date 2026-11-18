package com.juge.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.WidgetStyle
import com.juge.app.render.BgImageRenderer
import com.juge.app.ui.adjust.withCroppedBackgroundImage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundImageInteractionTest {

    @Test
    fun `裁切后的背景图按完整画面铺满组件`() {
        val style = WidgetStyle.DEFAULT.copy(
            backgroundImagePath = "/old.png",
            bgImageScaleMode = ImageScaleMode.CENTER_FIT,
        )

        val updated = style.withCroppedBackgroundImage("/new.png")

        assertEquals("/new.png", updated.backgroundImagePath)
        assertEquals(ImageScaleMode.STRETCH, updated.bgImageScaleMode)
    }

    @Test
    fun `旧裁切图保存为平铺时也不会按原始像素重复`() {
        val source = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            val canvas = Canvas(this)
            canvas.drawRect(50f, 0f, 100f, 25f, android.graphics.Paint().apply { color = Color.GREEN })
            canvas.drawRect(0f, 25f, 50f, 50f, android.graphics.Paint().apply { color = Color.BLUE })
            canvas.drawRect(50f, 25f, 100f, 50f, android.graphics.Paint().apply { color = Color.YELLOW })
        }
        val output = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val style = WidgetStyle.DEFAULT.copy(
            backgroundImagePath = "/data/user/0/com.juge.app/files/bg_images/old.jpg",
            bgImageScaleMode = ImageScaleMode.TILE,
        )

        try {
            BgImageRenderer.drawBgImage(
                canvas = Canvas(output),
                bitmap = source,
                rectF = RectF(0f, 0f, 200f, 100f),
                style = style,
            )

            // 源图四个象限应各自放大到输出的四分之一，而不是每 100×50px 重复一次。
            assertEquals(Color.GREEN, output.getPixel(125, 25))
            assertEquals(Color.BLUE, output.getPixel(25, 75))
            assertEquals(Color.YELLOW, output.getPixel(175, 75))
        } finally {
            source.recycle()
            output.recycle()
        }
    }

    @Test
    fun `裁切平移被限制在图片覆盖范围内`() {
        val clamped = CropImageHelper.clampCropOffset(
            offset = Offset(500f, 500f),
            bitmapWidth = 400f,
            bitmapHeight = 100f,
            viewWidthPx = 200f,
            viewHeightPx = 100f,
            scale = 2f,
        )

        assertEquals(Offset(300f, 50f), clamped)
    }

    @Test
    fun `缩放后仍按新尺寸限制平移`() {
        val clamped = CropImageHelper.clampCropOffset(
            offset = Offset(-500f, 500f),
            bitmapWidth = 400f,
            bitmapHeight = 200f,
            viewWidthPx = 200f,
            viewHeightPx = 100f,
            scale = 2f,
        )

        assertEquals(Offset(-100f, 50f), clamped)
    }
}
