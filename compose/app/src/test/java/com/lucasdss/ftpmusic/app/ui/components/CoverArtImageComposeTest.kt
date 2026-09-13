package com.lucasdss.ftpmusic.app.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric Compose tests for the error-fallback cover-art wrapper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class CoverArtImageComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the placeholder box when the url is null`() {
        composeRule.setContent {
            CoverArtImage(
                url = null,
                contentDescription = "cover",
                modifier = Modifier.size(64.dp),
            )
        }

        composeRule.onNodeWithContentDescription("cover").assertDoesNotExist()
    }

    @Test
    fun `composes an image node when a url is present`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.cacheDir, "cover-image-test.png")
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
            recycle()
        }

        composeRule.setContent {
            CoverArtImage(
                url = "file://${file.absolutePath}",
                contentDescription = "cover",
                modifier = Modifier.size(64.dp),
            )
        }

        composeRule.onNodeWithContentDescription("cover").assertExists()
    }
}
