package com.lucasdss.ftpmusic.app.playback

/**
 * ADR-0122: try/catch/finally helper for queue restore so a throw still releases
 * the latch and never escapes as an uncaught coroutine failure.
 */
internal object QueueRestoreSafely {
    suspend fun <T> run(
        onFailure: (Exception) -> Unit = {},
        finallyBlock: () -> Unit,
        block: suspend () -> T,
    ): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        onFailure(e)
        Result.failure(e)
    } finally {
        finallyBlock()
    }
}
