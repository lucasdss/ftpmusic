package com.lucasdss.ftpmusic.app.ui.components

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The server banner must surface unreachable-server and stale-loopback-proxy
 * config, be suppressed in intentional offline mode, and be dismissible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class ServerErrorBannerComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        ReachabilityStateHolder.onApiSuccess() // restore optimistic default
        DynamicBaseUrl.url = ""
    }

    @Test
    fun `unreachable server shows banner`() {
        ReachabilityStateHolder.onApiFailure()
        composeRule.setContent {
            ServerErrorBanner(configWarning = false, isOffline = false, onOpenServerSettings = {})
        }
        composeRule.onNodeWithText("Can't reach your server — check Server settings.").assertExists()
    }

    @Test
    fun `reachable server without config warning shows nothing`() {
        composeRule.setContent {
            ServerErrorBanner(configWarning = false, isOffline = false, onOpenServerSettings = {})
        }
        composeRule.onNodeWithText("Can't reach your server — check Server settings.").assertDoesNotExist()
    }

    @Test
    fun `loopback config shows proxy warning even when reachable flag is true`() {
        DynamicBaseUrl.url = "http://127.0.0.1:9999"
        composeRule.setContent {
            ServerErrorBanner(configWarning = true, isOffline = false, onOpenServerSettings = {})
        }
        composeRule.onNodeWithText(
            "Your server setting points to \"http://127.0.0.1:9999\" — a device-local address from an old proxy setup. Enter your real server URL.",
            substring = true,
        ).assertExists()
    }

    @Test
    fun `offline mode suppresses the banner`() {
        ReachabilityStateHolder.onApiFailure()
        composeRule.setContent {
            ServerErrorBanner(configWarning = true, isOffline = true, onOpenServerSettings = {})
        }
        composeRule.onNodeWithText("Can't reach your server — check Server settings.").assertDoesNotExist()
    }

    @Test
    fun `fix button invokes callback`() {
        ReachabilityStateHolder.onApiFailure()
        var fixed = false
        composeRule.setContent {
            ServerErrorBanner(configWarning = false, isOffline = false, onOpenServerSettings = { fixed = true })
        }
        composeRule.onNodeWithText("Fix").performClick()
        composeRule.waitForIdle()
        assert(fixed)
    }

    @Test
    fun `dismiss hides the banner`() {
        ReachabilityStateHolder.onApiFailure()
        composeRule.setContent {
            ServerErrorBanner(configWarning = false, isOffline = false, onOpenServerSettings = {})
        }
        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Can't reach your server — check Server settings.").assertDoesNotExist()
    }
}
