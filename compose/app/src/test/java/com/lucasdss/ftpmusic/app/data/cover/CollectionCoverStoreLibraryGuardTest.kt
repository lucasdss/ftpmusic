package com.lucasdss.ftpmusic.app.data.cover

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionCoverStoreLibraryGuardTest {

    @Test
    fun `isLibraryRelative detects library paths`() {
        assertTrue(isLibrary("library/abc.jpg"))
        assertTrue(isLibrary("library"))
        assertFalse(isLibrary("mix_1.jpg"))
        assertFalse(isLibrary("pl_xyz.jpg"))
        assertFalse(isLibrary(null))
        assertFalse(isLibrary(""))
        assertFalse(isLibrary("libraryish/x.jpg"))
    }

    private fun isLibrary(relative: String?): Boolean {
        val rel = relative?.trim().orEmpty()
        if (rel.isEmpty()) return false
        return rel == UserCoverLibrary.LIBRARY_DIR ||
            rel.startsWith("${UserCoverLibrary.LIBRARY_DIR}/")
    }
}
