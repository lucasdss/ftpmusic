package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarBtAutoplayPolicyTest {

    @Test
    fun `normalizeMac uppercases and rejects blank`() {
        assertEquals("AA:BB:CC:DD:EE:FF", CarBtAutoplayPolicy.normalizeMac("aa:bb:cc:dd:ee:ff"))
        assertNull(CarBtAutoplayPolicy.normalizeMac(null))
        assertNull(CarBtAutoplayPolicy.normalizeMac("  "))
        assertNull(CarBtAutoplayPolicy.normalizeMac("00:00:00:00:00:00"))
    }

    @Test
    fun `parseMacAllowlist reads JSON array`() {
        val set = CarBtAutoplayPolicy.parseMacAllowlist(
            """["aa:bb:cc:dd:ee:ff","11:22:33:44:55:66"]""",
        )
        assertEquals(
            setOf("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"),
            set,
        )
    }

    @Test
    fun `parseMacAllowlist empty and fallback`() {
        assertTrue(CarBtAutoplayPolicy.parseMacAllowlist(null).isEmpty())
        assertTrue(CarBtAutoplayPolicy.parseMacAllowlist("[]").isEmpty())
        assertEquals(
            setOf("AA:BB:CC:DD:EE:FF"),
            CarBtAutoplayPolicy.parseMacAllowlist("aa:bb:cc:dd:ee:ff"),
        )
    }

    @Test
    fun `encodeMacAllowlist sorts and quotes`() {
        val encoded = CarBtAutoplayPolicy.encodeMacAllowlist(
            listOf("bb:bb:bb:bb:bb:bb", "aa:aa:aa:aa:aa:aa"),
        )
        assertEquals("""["AA:AA:AA:AA:AA:AA","BB:BB:BB:BB:BB:BB"]""", encoded)
    }

    @Test
    fun `shouldResume requires enabled allowlist and not casting`() {
        val debounce = mutableMapOf<String, Long>()
        assertFalse(
            CarBtAutoplayPolicy.shouldResume(
                enabled = false,
                allowlistedMacs = setOf("AA:BB:CC:DD:EE:FF"),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 1000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertFalse(
            CarBtAutoplayPolicy.shouldResume(
                enabled = true,
                allowlistedMacs = emptySet(),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 1000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertFalse(
            CarBtAutoplayPolicy.shouldResume(
                enabled = true,
                allowlistedMacs = setOf("AA:BB:CC:DD:EE:FF"),
                deviceMac = "11:22:33:44:55:66",
                casting = false,
                nowMs = 1000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertFalse(
            CarBtAutoplayPolicy.shouldResume(
                enabled = true,
                allowlistedMacs = setOf("AA:BB:CC:DD:EE:FF"),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = true,
                nowMs = 1000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertTrue(
            CarBtAutoplayPolicy.shouldResume(
                enabled = true,
                allowlistedMacs = setOf("AA:BB:CC:DD:EE:FF"),
                deviceMac = "aa:bb:cc:dd:ee:ff",
                casting = false,
                nowMs = 1000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
    }

    @Test
    fun `shouldResume debounces duplicate connects`() {
        val debounce = mutableMapOf<String, Long>()
        assertTrue(
            CarBtAutoplayPolicy.shouldResume(
                enabled = true,
                allowlistedMacs = setOf("AA:BB:CC:DD:EE:FF"),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 10_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertFalse(
            CarBtAutoplayPolicy.shouldResume(
                enabled = true,
                allowlistedMacs = setOf("AA:BB:CC:DD:EE:FF"),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 12_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertTrue(
            CarBtAutoplayPolicy.shouldResume(
                enabled = true,
                allowlistedMacs = setOf("AA:BB:CC:DD:EE:FF"),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 16_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
    }
}
