package com.juge.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.juge.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CropImageDialogLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `裁切操作按钮位于取景区下方`() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "crop-layout.png")
        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        compose.setContent {
            MyApplicationTheme {
                CropImageHelper.ImageCropDialog(
                    show = true,
                    uri = Uri.fromFile(file),
                    onDismiss = {},
                    onCropSuccess = {},
                    targetWidth = 840,
                    targetHeight = 370,
                )
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("crop_preview").fetchSemanticsNodes().isNotEmpty()
        }

        val preview = compose.onNodeWithTag("crop_preview").fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithText("取消").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithText("保存并使用").fetchSemanticsNode().boundsInRoot

        assertTrue("取消按钮不应压在取景区上", cancel.top >= preview.bottom)
        assertTrue("保存按钮不应压在取景区上", save.top >= preview.bottom)
    }
}
