package com.lucasdss.ftpmusic.app.ui.server

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ServerConnectScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(state: ConnectState) {
        val viewModel = mockk<ServerConnectViewModel>(relaxed = true)
        every { viewModel.state } returns MutableStateFlow(state)
        composeRule.setContent {
            ServerConnectScreen(viewModel = viewModel)
        }
    }

    @Test
    fun `HTTP server displays unencrypted credentials warning`() {
        render(
            ConnectState(
                serverUrl = "http://192.168.1.20",
                username = "user",
                password = "secret",
            ),
        )

        composeRule.onNodeWithText(
            "HTTP sends your music-server credentials without transport encryption. Use only on a trusted private network.",
        ).assertIsDisplayed()
    }

    @Test
    fun `connect remains disabled until all credentials exist`() {
        render(ConnectState(serverUrl = "https://music.example.com", username = "user"))

        composeRule.onNodeWithText("Connect").assertIsNotEnabled()
    }
}
