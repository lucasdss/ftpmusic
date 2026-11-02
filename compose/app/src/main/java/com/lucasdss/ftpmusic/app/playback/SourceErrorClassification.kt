package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.PlaybackException
import com.lucasdss.ftpmusic.app.data.cache.CacheSpanState

/**
 * N11-2 (2026-11-01): a `Source error` is not self-describing.
 *
 * media3 collapses "the server deleted this track", "the network is down" and
 * "the local cache span is corrupt" into the same listener callback. Acting on
 * the local-cache branch for a remote failure destroys a usable span and hides
 * a server-side deletion; doing nothing loses the healing path. This file is the
 * pure classifier and the album-run detector, kept Android-free for unit tests.
 */
internal enum class SourceErrorKind {
    /** The bytes came from a local span that cannot be played — eviction is correct. */
    LOCAL_FAULT,

    /** The upstream answered and the item is gone (404/410, non-audio body). */
    REMOTE_MISSING,

    /** Transport-level failure; retry/skip but never evict or reconcile. */
    NETWORK,

    /** Not enough information; conservative: no evict, no album jump. */
    UNKNOWN,
}

internal const val MISSING_ALBUM_WINDOW_MS = 30_000L
internal const val MISSING_ALBUM_MAX_GAP = 2

/**
 * Pure mapper. [httpCode] is read from `HttpDataSource.InvalidResponseCodeException`
 * when present; [causeName] is the simple class name of the one-level cause.
 * [span] is only consulted for the codes where the local cache is the tie-breaker.
 */
internal fun classifySourceError(
    errorCode: Int,
    httpCode: Int?,
    causeName: String?,
    span: CacheSpanState,
): SourceErrorKind = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> SourceErrorKind.LOCAL_FAULT

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (httpCode) {
        404, 410 -> SourceErrorKind.REMOTE_MISSING
        401, 403 -> SourceErrorKind.UNKNOWN
        429 -> SourceErrorKind.NETWORK
        null -> SourceErrorKind.NETWORK
        in 500..599 -> SourceErrorKind.NETWORK
        else -> SourceErrorKind.NETWORK
    }

    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> SourceErrorKind.REMOTE_MISSING

    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    -> SourceErrorKind.NETWORK

    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
    -> SourceErrorKind.UNKNOWN

    PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> causeKind(causeName)

    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    -> spanKind(span)

    else -> SourceErrorKind.UNKNOWN
}

private fun causeKind(causeName: String?): SourceErrorKind = when (causeName) {
    null, "" -> SourceErrorKind.UNKNOWN

    "ConnectException",
    "SocketTimeoutException",
    "UnknownHostException",
    "SocketException",
    "InterruptedIOException",
    -> SourceErrorKind.NETWORK

    "FileNotFoundException" -> SourceErrorKind.LOCAL_FAULT

    else -> SourceErrorKind.UNKNOWN
}

/**
 * A local span that classifies as poison is the only case where the bytes were
 * demonstrably local. A playable span means the failure could still be remote
 * (the missing range was fetched upstream), so no eviction.
 */
private fun spanKind(span: CacheSpanState): SourceErrorKind = when (span) {
    CacheSpanState.UNPLAYABLE -> SourceErrorKind.LOCAL_FAULT
    CacheSpanState.NONE -> SourceErrorKind.REMOTE_MISSING
    CacheSpanState.PLAYABLE -> SourceErrorKind.UNKNOWN
}

/**
 * Remembers the first `REMOTE_MISSING` for an album so the second contiguous one
 * can collapse the run. The first failure always takes the ordinary one-item
 * skip: a single transient 404 must never skip a healthy album.
 */
internal class MissingAlbumRun(
    private val windowMs: Long = MISSING_ALBUM_WINDOW_MS,
    private val maxGap: Int = MISSING_ALBUM_MAX_GAP,
) {
    private var albumId: String? = null
    private var firstIndex: Int = -1
    private var lastIndex: Int = -1
    private var lastMs: Long = 0L

    /** @return true when this failure completes a contiguous run worth collapsing. */
    fun onRemoteMissing(albumId: String?, index: Int, nowMs: Long): Boolean {
        if (albumId.isNullOrEmpty() || index < 0) {
            clear()
            return false
        }
        val continues = this.albumId == albumId &&
            nowMs - lastMs <= windowMs &&
            index - lastIndex <= maxGap
        this.albumId = albumId
        firstIndex = if (continues) firstIndex else index
        lastIndex = index
        lastMs = nowMs
        if (!continues) return false
        // Consume the run so a third failure starts a new one.
        clear()
        return true
    }

    fun clear() {
        albumId = null
        firstIndex = -1
        lastIndex = -1
        lastMs = 0L
    }

    internal fun peekAlbumId(): String? = albumId
    internal fun peekFirstIndex(): Int = firstIndex
}

/**
 * Index of the first entry after [fromIndex] whose `albumId` differs, or
 * [albums].size when the run reaches the end. Only consecutive equal ids are
 * skipped — in a shuffled queue that degrades to one item, never another album.
 */
internal fun findNextAlbumIndex(albums: List<String?>, fromIndex: Int): Int {
    if (albums.isEmpty()) return 0
    val start = fromIndex.coerceIn(0, albums.lastIndex)
    val current = albums[start]
    var i = start + 1
    while (i < albums.size) {
        if (albums[i] != current) return i
        i++
    }
    return albums.size
}

/**
 * N1-C: consecutive auto-skips are bounded. The 2026-11-01 burst skipped 58
 * items in 3.95 s because the per-track counter resets on every transition and
 * the cross-track circuit was cleared by the programmatic seek. This class holds
 * the aggregate for the summary line and the bound that trips [PAUSE_BURST];
 * [disruptive] entries are the ones that count toward the bound (album jumps
 * and cached jumps are recoveries, not failed items).
 */
internal class AutoSkipBurst(
    private val threshold: Int = AUTO_SKIP_BURST_THRESHOLD,
    private val windowMs: Long = AUTO_SKIP_BURST_WINDOW_MS,
) {
    var count: Int = 0
        private set
    var firstIndex: Int = -1
        private set
    var lastIndex: Int = -1
        private set
    var firstMs: Long = 0L
        private set
    var lastMs: Long = 0L
        private set
    var evicted: Int = 0
        private set
    var itemsSkipped: Int = 0
        private set
    var disruptive: Int = 0
        private set

    fun record(index: Int, nowMs: Long, skipped: Int = 1, evictedDelta: Int = 0, disruptiveEntry: Boolean = true) {
        if (count > 0 && nowMs - firstMs > windowMs) clear()
        if (count == 0) {
            firstIndex = index
            firstMs = nowMs
        }
        count++
        itemsSkipped += skipped
        evicted += evictedDelta
        if (disruptiveEntry) disruptive++
        lastIndex = index
        lastMs = nowMs
    }

    fun atThreshold(): Boolean = disruptive >= threshold

    /** Builds the summary line and clears the run. Null when nothing to report. */
    fun drainSummary(): String? {
        if (count == 0) return null
        val duration = (lastMs - firstMs).coerceAtLeast(0L)
        val summary = "autoSkip burst count=$count items=$itemsSkipped firstIdx=$firstIndex " +
            "lastIdx=$lastIndex durationMs=$duration evicted=$evicted disruptive=$disruptive"
        clear()
        return summary
    }

    fun clear() {
        count = 0
        firstIndex = -1
        lastIndex = -1
        firstMs = 0L
        lastMs = 0L
        evicted = 0
        itemsSkipped = 0
        disruptive = 0
    }
}

internal const val AUTO_SKIP_BURST_THRESHOLD = 8
internal const val AUTO_SKIP_BURST_WINDOW_MS = 120_000L

/**
 * N1-C action matrix: the circuit/burst bound only overrides the per-track
 * action when it has actually tripped. Advancing runs pause (index survives);
 * a stuck run still stops.
 */
internal fun chooseErrorSkipAction(
    baseAction: ErrorSkipAction,
    circuitTripped: Boolean,
    advancing: Boolean,
): ErrorSkipAction = when {
    circuitTripped && advancing -> ErrorSkipAction.PAUSE_BURST
    circuitTripped -> ErrorSkipAction.CIRCUIT_STOP
    else -> baseAction
}
