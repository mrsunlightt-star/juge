package com.juge.app

import androidx.compose.ui.geometry.Offset
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.adjust.withCroppedBackgroundImage
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundImageInteractionTest {

    @Test
    fun `裁切后的背景图默认使用平铺`() {
        val style = WidgetStyle.DEFAULT.copy(
            backgroundImagePath = "/old.png",
            bgImageScaleMode = ImageScaleMode.CENTER_FIT,
        )

        val updated = style.withCroppedBackgroundImage("/new.png")

        assertEquals("/new.png", updated.backgroundImagePath)
        assertEquals(ImageScaleMode.TILE, updated.bgImageScaleMode)
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
