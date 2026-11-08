package com.lucasdss.ftpmusic.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerHapticsPolicyTest {
    @Test
    fun `enabled requires appearance vibrator and not reduce motion`() {
        assertTrue(PlayerHapticsPolicy.enabled(true, false, true))
        assertFalse(PlayerHapticsPolicy.enabled(false, false, true))
        assertFalse(PlayerHapticsPolicy.enabled(true, true, true))
        assertFalse(PlayerHapticsPolicy.enabled(true, false, false))
    }

    @Test
    fun `minute crossing fires selection`() {
        val kind = PlayerHapticsPolicy.minuteCrossingKind(0.1f, 0.5f, 180_000L)
        assertEquals(PlayerHapticsPolicy.Kind.Selection, kind)
    }

    @Test
    fun `same minute no tick`() {
        assertNull(PlayerHapticsPolicy.minuteCrossingKind(0.1f, 0.2f, 180_000L))
    }

    @Test
    fun `boundary heavy at ends`() {
        assertEquals(PlayerHapticsPolicy.Kind.ImpactHeavy, PlayerHapticsPolicy.boundaryKind(0f))
        assertEquals(PlayerHapticsPolicy.Kind.ImpactHeavy, PlayerHapticsPolicy.boundaryKind(1f))
        assertNull(PlayerHapticsPolicy.boundaryKind(0.5f))
    }

    @Test
    fun `shouldFire false when kind null`() {
        assertFalse(PlayerHapticsPolicy.shouldFire(true, false, true, null))
    }
}
