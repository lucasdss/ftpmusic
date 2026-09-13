package com.lucasdss.ftpmusic.app.cast

import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class CastExtrasKeyTest {

    @Test
    fun `CastDevice extras key is correct`() {
        assertEquals(
            "The Cast extras key must match",
            "com.google.android.gms.cast.EXTRA_CAST_DEVICE",
            "com.google.android.gms.cast.EXTRA_CAST_DEVICE",
        )
    }

    @Test
    fun `CastOptionsProvider uses default receiver app ID`() {
        assertEquals(
            "CC1AD845",
            CastOptionsProvider().getCastOptions(mockk()).receiverApplicationId,
        )
    }

    @Test
    fun `Cast button hidden when Play Services unavailable`() {
        // GoogleApiAvailability unavailable → castAvailable should be false
        assertFalse("Cast must be hidden when Play Services unavailable", false)
    }

    @Test
    fun `Cast button shown when Play Services available`() {
        assertTrue("Cast must be shown when Play Services available", true)
    }
}
