package com.lucasdss.ftpmusic.app.data.diagnostics

import android.content.Context
import android.os.Build
import com.lucasdss.ftpmusic.app.BuildConfig
import com.lucasdss.ftpmusic.app.playback.BtResumeEvents
import java.time.Instant
import java.util.ArrayDeque

/**
 * In-process ring buffer for Play testing diagnostics (ADR-0048, ADR-0060).
 * Survives R8 Log stripping — testers export via Settings → Share diagnostics.
 * Strict fields only: ids/counts/flags — no titles, URLs, or Cast friendlyName.
 * Exception/msg scrub: URLs → `<url>`, truncate to [MAX_FIELD_LEN].
 */
object DiagnosticLog {
    private const val CAP = 1000
    internal const val MAX_FIELD_LEN = 200
    private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val lock = Any()
    private val lines = ArrayDeque<String>(CAP + 1)
    private val lineTimes = ArrayDeque<Long>(CAP + 1)
    private var ringSinceMsValue: Long = 0L
    private var clearCountValue: Int = 0

    fun d(tag: String, msg: String) = append("D", tag, msg, null)

    fun w(tag: String, msg: String, t: Throwable? = null) = append("W", tag, msg, t)

    fun e(tag: String, msg: String, t: Throwable? = null) = append("E", tag, msg, t)

    fun clear() {
        synchronized(lock) {
            lines.clear()
            lineTimes.clear()
            ringSinceMsValue = 0L
            clearCountValue += 1
        }
    }

    fun lineCount(): Int = synchronized(lock) { lines.size }

    /** Epoch ms of the oldest retained line (0 = buffer empty). */
    fun ringSinceMs(): Long = synchronized(lock) { ringSinceMsValue }

    /** Number of [clear] calls in this process. */
    fun clearCount(): Int = synchronized(lock) { clearCountValue }

    /**
     * Full export for Share. [offline], [reachable], sync prefs timestamps, and
     * optional library counts keep the header useful without PII.
     */
    fun snapshot(
        context: Context,
        offline: Boolean,
        reachable: Boolean,
        lastFullSyncMs: Long = 0L,
        lastDeltaSyncMs: Long = 0L,
        albums: Int = -1,
        artists: Int = -1,
        tracks: Int = -1,
        syncIntervalHours: Int = -1,
        homeRecentFetchedMs: Long = -1L,
        homeRecentTtlMin: Int = -1,
        homeRecentIdsCount: Int = -1,
        lastWmAttemptMs: Long = -1L,
        lastWmSkipReason: String = "",
        mode: String = "",
        skipReason: String = "",
        corpus: Int = -1,
        songSum: Int = -1,
        albumTracksAlbumsZero: Int = -1,
        homeRecentResult: String = "",
        wmSummary: String = "",
    ): String {
        val header = buildString {
            appendLine("ftpmusic diagnostics")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL}")
            appendLine("offline=$offline reachable=$reachable")
            appendLine("lastFullSyncMs=$lastFullSyncMs lastDeltaSyncMs=$lastDeltaSyncMs")
            if (syncIntervalHours >= 0) {
                appendLine("syncIntervalHours=$syncIntervalHours")
            }
            if (homeRecentFetchedMs >= 0L || homeRecentTtlMin >= 0 || homeRecentIdsCount >= 0) {
                appendLine(
                    "homeRecentFetchedMs=${homeRecentFetchedMs.coerceAtLeast(0L)} " +
                        "homeRecentTtlMin=${homeRecentTtlMin.coerceAtLeast(0)} " +
                        "homeRecentIdsCount=${homeRecentIdsCount.coerceAtLeast(0)} " +
                        "homeRecentResult=${homeRecentResult.ifEmpty { "unknown" }}",
                )
            }
            if (lastWmAttemptMs >= 0L || lastWmSkipReason.isNotEmpty()) {
                // F6: `0` is indistinguishable from "never ran" on a device whose
                // prefs already hold other sync stamps.
                appendLine(
                    "lastWmAttemptMs=${if (lastWmAttemptMs <= 0L) "never" else lastWmAttemptMs} " +
                        "lastWmSkipReason=${lastWmSkipReason.ifEmpty { "-" }}",
                )
            }
            if (wmSummary.isNotEmpty()) appendLine("workManager $wmSummary")
            appendLine(
                "syncMode mode=${mode.ifEmpty { "-" }} skip=${skipReason.ifEmpty { "-" }}",
            )
            if (albums >= 0 || artists >= 0 || tracks >= 0 || corpus >= 0) {
                // F4 / D5: three different SoTs, labelled; ghost-row count exposed.
                appendLine(
                    "libraryCounts albums=$albums artists=$artists tracksLedger=$tracks " +
                        "corpus=$corpus songSum=$songSum albumTracksAlbumsZero=$albumTracksAlbumsZero",
                )
            }
            val ringSince = ringSinceMsValue
            appendLine(
                "ringSince=${if (ringSince <= 0L) "none" else Instant.ofEpochMilli(ringSince)} " +
                    "clearCount=$clearCountValue",
            )
            appendLine("capturedAt=${Instant.now()}")
            append(BtResumeEvents.snapshotSection())
            appendLine("---")
        }
        val bodyLines = synchronized(lock) { lines.toList() }
        return header + bodyLines.joinToString("\n")
    }

    private fun append(level: String, tag: String, msg: String, t: Throwable?) {
        val nowMs = System.currentTimeMillis()
        val ts = Instant.ofEpochMilli(nowMs).toString()
        val safeMsg = sanitize(msg)
        val suffix = if (t != null) {
            val tMsg = t.message?.let { sanitize(it) }
            if (tMsg.isNullOrEmpty()) " | ${t.javaClass.simpleName}" else " | ${t.javaClass.simpleName}: $tMsg"
        } else {
            ""
        }
        val line = "$ts | $level | $tag | $safeMsg$suffix"
        synchronized(lock) {
            lines.addLast(line)
            lineTimes.addLast(nowMs)
            while (lines.size > CAP) {
                lines.removeFirst()
                lineTimes.removeFirst()
            }
            ringSinceMsValue = lineTimes.firstOrNull() ?: 0L
        }
        if (BuildConfig.DEBUG) {
            when (level) {
                "E" -> android.util.Log.e(tag, msg, t)
                "W" -> android.util.Log.w(tag, msg, t)
                else -> android.util.Log.d(tag, msg)
            }
        }
    }

    /** Redact http(s) URLs + bound field length for Intent/EXTRA_TEXT safety. */
    internal fun sanitize(text: String): String {
        val scrubbed = URL_REGEX.replace(text, "<url>")
        return if (scrubbed.length <= MAX_FIELD_LEN) scrubbed else scrubbed.take(MAX_FIELD_LEN)
    }
}
