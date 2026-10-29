package com.juge.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.juge.app.data.DbHelper
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetStyle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CropImageHelperTest {
    private lateinit var context: Context
    private lateinit var db: DbHelper

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val field = DbHelper::class.java.getDeclaredField("instance").apply { isAccessible = true }
        (field.get(null) as? DbHelper)?.close()
        field.set(null, null)
        db = DbHelper.getInstance(context)
    }

    @Test
    fun `有效图片在读取尺寸后继续解码`() {
        val file = image(32, 24)
        val decoded = CropImageHelper.loadBitmapFromUri(context, Uri.fromFile(file))
        assertNotNull(decoded)
        assertEquals(32, decoded!!.width)
        assertEquals(24, decoded.height)
        decoded.recycle()
    }

    @Test
    fun `大图按尺寸限制降采样`() {
        val decoded = CropImageHelper.loadBitmapFromUri(context, Uri.fromFile(image(4096, 128)))
        assertNotNull(decoded)
        assertEquals(2048, decoded!!.width)
        assertEquals(64, decoded.height)
        decoded.recycle()
    }

    @Test
    fun `损坏和不存在的图片返回空值`() {
        val invalid = File(context.cacheDir, "invalid.png").apply { writeText("not an image") }
        assertNull(CropImageHelper.loadBitmapFromUri(context, Uri.fromFile(invalid)))
        assertNull(CropImageHelper.loadBitmapFromUri(context, Uri.fromFile(File(context.cacheDir, "absent.png"))))
    }

    @Test
    fun `组件样式中转义后的路径仍视为引用`() {
        val path = File(context.filesDir, "bg_images/shared.png").absolutePath
        val json = JSONObject().put("backgroundImagePath", path).toString()
        assertTrue(json.contains("\\/"))
        context.getSharedPreferences(ReminderWidgetProvider.PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
            .edit().putString("widget_style_42", json).commit()
        assertTrue(CropImageHelper.isBackgroundFileStillReferenced(context, path, null))
        assertFalse(CropImageHelper.isBackgroundFileStillReferenced(context, "$path.other", null))
    }

    @Test
    fun `配置引用允许排除自身但不能排除其他配置`() {
        val path = File(context.filesDir, "bg_images/shared.png").absolutePath
        val config = WidgetConfig(name = "引用", content = "", styleJson = WidgetStyle.DEFAULT.copy(backgroundImagePath = path).toJsonString())
        val first = db.insertWidgetConfig(config)
        assertTrue(CropImageHelper.isBackgroundFileStillReferenced(context, path, null))
        assertFalse(CropImageHelper.isBackgroundFileStillReferenced(context, path, first))
        db.insertWidgetConfig(config.copy(name = "另一个引用"))
        assertTrue(CropImageHelper.isBackgroundFileStillReferenced(context, path, first))
    }

    @Test
    fun `组件样式损坏时保守保留图片`() {
        context.getSharedPreferences(ReminderWidgetProvider.PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
            .edit().putString("widget_style_42", "invalid json").commit()
        assertTrue(CropImageHelper.isBackgroundFileStillReferenced(context, "/bg_images/keep.png", null))
    }

    private fun image(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return File(context.cacheDir, "test-${width}x$height.png").also { file ->
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
