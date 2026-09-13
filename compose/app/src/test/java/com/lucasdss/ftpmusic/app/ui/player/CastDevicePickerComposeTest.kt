package com.lucasdss.ftpmusic.app.ui.player

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.android.gms.cast.CastDevice
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests for CastDevicePickerDialog (ModalBottomSheet). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class CastDevicePickerComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockDevice(name: String, model: String, id: String = "id-${name.hashCode()}") = mockk<CastDevice> {
        every { friendlyName } returns name
        every { modelName } returns model
        every { deviceId } returns id
    }

    @Test
    fun `device list renders device names`() {
        val devices = listOf(
            mockDevice("Mini Speaker", "Google Home Mini"),
            mockDevice("Soundbar", "Samsung Soundbar"),
        )
        composeRule.setContent {
            CastDevicePickerDialog(
                devices = devices,
                onDismiss = {},
                onDeviceSelected = {},
            )
        }
        composeRule.onNodeWithText("Mini Speaker").assertExists()
        composeRule.onNodeWithText("Soundbar").assertExists()
    }

    @Test
    fun `click device invokes onDeviceSelected`() {
        val device = mockDevice("Mini Speaker", "Google Home Mini")
        var selectedDevice: CastDevice? = null
        composeRule.setContent {
            CastDevicePickerDialog(
                devices = listOf(device),
                onDismiss = {},
                onDeviceSelected = { selectedDevice = it },
            )
        }
        composeRule.onNodeWithText("Mini Speaker").performClick()
        assertEquals(device, selectedDevice)
    }

    @Test
    fun `connected state shows label`() {
        val device = mockDevice("Mini Speaker", "Google Home Mini")
        composeRule.setContent {
            CastDevicePickerDialog(
                devices = listOf(device),
                isCasting = true,
                connectedDeviceName = "Mini Speaker",
                onDismiss = {},
                onDeviceSelected = {},
            )
        }
        composeRule.onNodeWithText("connected").assertExists()
    }

    @Test
    fun `disconnect button shown when casting`() {
        val device = mockDevice("Mini Speaker", "Google Home Mini")
        composeRule.setContent {
            CastDevicePickerDialog(
                devices = listOf(device),
                isCasting = true,
                connectedDeviceName = "Mini Speaker",
                onDismiss = {},
                onDeviceSelected = {},
                onDisconnect = {},
            )
        }
        composeRule.onNodeWithText("Disconnect").assertExists()
    }

    @Test
    fun `connected device click invokes onDisconnect`() {
        var disconnected = false
        var dismissed = false
        val device = mockDevice("Mini Speaker", "Google Home Mini")
        composeRule.setContent {
            CastDevicePickerDialog(
                devices = listOf(device),
                isCasting = true,
                connectedDeviceName = "Mini Speaker",
                onDismiss = { dismissed = true },
                onDeviceSelected = {},
                onDisconnect = { disconnected = true },
            )
        }
        composeRule.onNodeWithText("Mini Speaker").performClick()
        assertTrue(disconnected)
        assertTrue(dismissed)
    }

    @Test
    fun `cancel button invokes onDismiss`() {
        var dismissed = false
        composeRule.setContent {
            CastDevicePickerDialog(
                devices = listOf(mockDevice("Speaker", "Google Home Mini")),
                onDismiss = { dismissed = true },
                onDeviceSelected = {},
            )
        }
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }
}
