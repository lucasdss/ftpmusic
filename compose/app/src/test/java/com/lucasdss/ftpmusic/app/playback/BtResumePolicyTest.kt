package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BtResumePolicyTest {

    @Test
    fun `normalizeMac uppercases and rejects blank`() {
        assertEquals("AA:BB:CC:DD:EE:FF", BtResumePolicy.normalizeMac("aa:bb:cc:dd:ee:ff"))
        assertNull(BtResumePolicy.normalizeMac(null))
        assertNull(BtResumePolicy.normalizeMac("00:00:00:00:00:00"))
    }

    @Test
    fun `mode any ignores empty allowlist`() {
        val debounce = mutableMapOf<String, Long>()
        assertTrue(
            BtResumePolicy.shouldResume(
                enabled = true,
                mode = BtResumeMode.ANY,
                allowlistedMacs = emptySet(),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 10_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
    }

    @Test
    fun `mode any allows null mac with synthetic debounce key`() {
        val debounce = mutableMapOf<String, Long>()
        assertTrue(
            BtResumePolicy.shouldResume(
                enabled = true,
                mode = BtResumeMode.ANY,
                allowlistedMacs = emptySet(),
                deviceMac = null,
                casting = false,
                nowMs = 10_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertTrue(debounce.containsKey(BtResumePolicy.ANY_UNKNOWN_MAC_KEY))
        assertFalse(
            BtResumePolicy.shouldResume(
                enabled = true,
                mode = BtResumeMode.SELECTED,
                allowlistedMacs = setOf("AA:BB:CC:DD:EE:FF"),
                deviceMac = null,
                casting = false,
                nowMs = 20_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
    }

    @Test
    fun `mode selected requires allowlist hit`() {
        val debounce = mutableMapOf<String, Long>()
        assertFalse(
            BtResumePolicy.shouldResume(
                enabled = true,
                mode = BtResumeMode.SELECTED,
                allowlistedMacs = emptySet(),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 10_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertTrue(
            BtResumePolicy.shouldResume(
                enabled = true,
                mode = BtResumeMode.SELECTED,
                allowlistedMacs = setOf("AA:BB:CC:DD:EE:FF"),
                deviceMac = "aa:bb:cc:dd:ee:ff",
                casting = false,
                nowMs = 10_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
    }

    @Test
    fun `debounces and skips casting`() {
        val debounce = mutableMapOf<String, Long>()
        assertFalse(
            BtResumePolicy.shouldResume(
                enabled = true,
                mode = BtResumeMode.ANY,
                allowlistedMacs = emptySet(),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = true,
                nowMs = 10_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertTrue(
            BtResumePolicy.shouldResume(
                enabled = true,
                mode = BtResumeMode.ANY,
                allowlistedMacs = emptySet(),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 10_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertFalse(
            BtResumePolicy.shouldResume(
                enabled = true,
                mode = BtResumeMode.ANY,
                allowlistedMacs = emptySet(),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 12_000,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
    }

    @Test
    fun `encode and parse allowlist`() {
        val encoded = BtResumePolicy.encodeMacAllowlist(
            listOf("bb:bb:bb:bb:bb:bb", "aa:aa:aa:aa:aa:aa"),
        )
        assertEquals("""["AA:AA:AA:AA:AA:AA","BB:BB:BB:BB:BB:BB"]""", encoded)
        assertEquals(
            setOf("AA:AA:AA:AA:AA:AA", "BB:BB:BB:BB:BB:BB"),
            BtResumePolicy.parseMacAllowlist(encoded),
        )
    }

    @Test
    fun `BtResumeMode storage roundtrip`() {
        assertEquals(BtResumeMode.ANY, BtResumeMode.fromStorage("any"))
        assertEquals(BtResumeMode.SELECTED, BtResumeMode.fromStorage(null))
        assertEquals(BtResumeMode.SELECTED, BtResumeMode.fromStorage("other"))
        assertEquals("any", BtResumeMode.toStorage(BtResumeMode.ANY))
        assertEquals("selected", BtResumeMode.toStorage(BtResumeMode.SELECTED))
    }

    @Test
    fun `disabled and blank mac branches`() {
        val debounce = mutableMapOf<String, Long>()
        assertFalse(
            BtResumePolicy.shouldResume(
                enabled = false,
                mode = BtResumeMode.ANY,
                allowlistedMacs = emptySet(),
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 1,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertNull(BtResumePolicy.normalizeMac(""))
        assertNull(BtResumePolicy.normalizeMac("   "))
        assertTrue(
            BtResumePolicy.shouldResume(
                enabled = true,
                mode = BtResumeMode.ANY,
                allowlistedMacs = emptySet(),
                deviceMac = "",
                casting = false,
                nowMs = 1,
                lastAcceptedAtMsByMac = debounce,
            ),
        )
        assertFalse(
            BtResumePolicy.shouldResume(
                enabled = true,
                mode = BtResumeMode.ANY,
                allowlistedMacs = emptySet(),
                deviceMac = "",
                casting = false,
                nowMs = 2,
                lastAcceptedAtMsByMac = debounce,
                debounceMs = 5_000,
            ),
        )
    }

    @Test
    fun `parse allowlist pipe and plain forms`() {
        assertEquals(emptySet<String>(), BtResumePolicy.parseMacAllowlist(null))
        assertEquals(emptySet<String>(), BtResumePolicy.parseMacAllowlist("[]"))
        assertEquals(
            setOf("AA:AA:AA:AA:AA:AA", "BB:BB:BB:BB:BB:BB"),
            BtResumePolicy.parseMacAllowlist("aa:aa:aa:aa:aa:aa|bb:bb:bb:bb:bb:bb"),
        )
        assertEquals(
            setOf("AA:AA:AA:AA:AA:AA"),
            BtResumePolicy.parseMacAllowlist("""["aa:aa:aa:aa:aa:aa"]"""),
        )
    }
}
