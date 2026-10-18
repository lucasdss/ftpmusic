package com.lucasdss.ftpmusic.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class SubsonicAuthHelperRequireOkTest {

    private val helper = SubsonicAuthHelper()

    @Test
    fun `requireOk accepts status ok`() {
        val response = mapOf(
            "subsonic-response" to mapOf("status" to "ok", "version" to "1.16.1"),
        )
        helper.requireOk(response)
    }

    @Test
    fun `requireOk throws with Subsonic error code and message`() {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "status" to "failed",
                "error" to mapOf("code" to 40, "message" to "Wrong username or password"),
            ),
        )
        val thrown = assertThrows(IllegalStateException::class.java) {
            helper.requireOk(response)
        }
        assertEquals("40: Wrong username or password", thrown.message)
    }

    @Test
    fun `requireOk throws Unknown error when failed without error object`() {
        val response = mapOf("subsonic-response" to mapOf("status" to "failed"))
        val thrown = assertThrows(IllegalStateException::class.java) {
            helper.requireOk(response)
        }
        assertEquals("Unknown error", thrown.message)
    }

    @Test
    fun `checkResponseStatus false when failed`() {
        val response = mapOf("subsonic-response" to mapOf("status" to "failed"))
        if (helper.checkResponseStatus(response)) fail("expected failed status")
    }
}
