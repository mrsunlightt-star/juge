package com.juge.app

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

/**
 * 组件选择器预览的回归测试。
 *
 * 背景：两个入口的 `android:previewLayout` 曾指向运行时的 `widget_layout`——那里面只有一个
 * 没有 `src` 的 ImageView，内容全靠 `setImageViewBitmap` 在运行时塞。而 `previewLayout` 会被桌面
 * **静态 inflate**，没有任何运行时绑定，于是 Android 12+（优先用 previewLayout）的选择器里
 * 显示成一块空白；`previewImage` 只在 12 以下生效。
 *
 * 这里锁定三条：
 * 1. 两个入口都声明了 `previewImage`（老桌面兜底）；
 * 2. `previewLayout` 不能是运行时的 `initialLayout`（它天然没有静态内容）；
 * 3. 预览布局静态 inflate 后，至少有一个 ImageView 带着真实图源。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
// 预览素材是 WebP：Robolectric 默认图形模式解不了，会一律返回 10×10 占位图，
// 图源尺寸断言就测不到真数据。NATIVE 模式走真实 Skia 解码。
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPreviewLayoutTest {

    private data class ProviderAttrs(
        val initialLayout: Int,
        val previewLayout: Int,
        val previewImage: Int,
    )

    private fun readProviderAttrs(providerXmlRes: Int): ProviderAttrs {
        val resources = RuntimeEnvironment.getApplication().resources
        val parser = resources.getXml(providerXmlRes)
        var initialLayout = 0
        var previewLayout = 0
        var previewImage = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                initialLayout = parser.getAttributeResourceValue(ANDROID_NS, "initialLayout", 0)
                previewLayout = parser.getAttributeResourceValue(ANDROID_NS, "previewLayout", 0)
                previewImage = parser.getAttributeResourceValue(ANDROID_NS, "previewImage", 0)
            }
            event = parser.next()
        }
        return ProviderAttrs(initialLayout, previewLayout, previewImage)
    }

    private fun collectImageViews(view: View, out: MutableList<ImageView> = mutableListOf()): List<ImageView> {
        if (view is ImageView) out += view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectImageViews(view.getChildAt(i), out)
        }
        return out
    }

    private fun assertPreviewHasArtwork(providerXmlRes: Int, entry: String) {
        val context = RuntimeEnvironment.getApplication()
        val attrs = readProviderAttrs(providerXmlRes)

        assertNotEquals("$entry 缺少 previewImage，Android 12 以下的选择器没有图可显示", 0, attrs.previewImage)
        assertNotEquals("$entry 缺少 previewLayout", 0, attrs.previewLayout)
        assertNotEquals(
            "$entry 的 previewLayout 指回了运行时布局 initialLayout——它会静态 inflate，" +
                "而内容要等 setImageViewBitmap 才有，选择器里就是空白",
            attrs.initialLayout,
            attrs.previewLayout,
        )

        val preview = android.view.LayoutInflater.from(context).inflate(attrs.previewLayout, null)
        val withArtwork = collectImageViews(preview).filter { it.drawable != null }
        assertTrue(
            "$entry 的预览布局静态 inflate 后没有任何带图源的 ImageView，选择器会显示空白",
            withArtwork.isNotEmpty(),
        )
        withArtwork.forEach { image ->
            val drawable = requireNotNull(image.drawable)
            assertTrue(
                "$entry 的预览图源尺寸为 0（${drawable.intrinsicWidth}x${drawable.intrinsicHeight}）",
                drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0,
            )
        }
    }

    @Test
    fun `4×4 入口的预览布局自带静态图源`() {
        assertPreviewHasArtwork(R.xml.widget_info, "4×4 入口")
    }

    @Test
    fun `4×2 入口的预览布局自带静态图源`() {
        assertPreviewHasArtwork(R.xml.widget_info_compact, "4×2 入口")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
