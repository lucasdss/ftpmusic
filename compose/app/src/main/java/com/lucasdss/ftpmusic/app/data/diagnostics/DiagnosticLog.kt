package com.lucasdss.ftpmusic.app.data.diagnostics

import android.content.Context
import android.os.Build
import com.lucasdss.ftpmusic.app.BuildConfig
import java.time.Instant
import java.util.ArrayDeque

/**
 * In-process ring buffer for Play testing diagnostics (ADR-0048).
 * Survives R8 Log stripping — testers export via Settings → Share diagnostics.
 */
object DiagnosticLog {
    private const val CAP = 500
    private val lock = Any()
    private val lines = ArrayDeque<String>(CAP + 1)

    fun d(tag: String, msg: String) = append("D", tag, msg, null)

    fun w(tag: String, msg: String, t: Throwable? = null) = append("W", tag, msg, t)

    fun e(tag: String, msg: String, t: Throwable? = null) = append("E", tag, msg, t)

    fun clear() {
        synchronized(lock) { lines.clear() }
    }

    fun lineCount(): Int = synchronized(lock) { lines.size }

    /**
     * Full export for Share. [offline]/[reachable], and optional sync prefs
     * timestamps keep the header useful without PII.
     */
    fun snapshot(
        context: Context,
        offline: Boolean,
        reachable: Boolean,
        lastFullSyncMs: Long = 0L,
        lastDeltaSyncMs: Long = 0L,
    ): String {
        val header = buildString {
            appendLine("ftpmusic diagnostics")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL}")
            appendLine("offline=$offline reachable=$reachable")
            appendLine("lastFullSyncMs=$lastFullSyncMs lastDeltaSyncMs=$lastDeltaSyncMs")
            appendLine("capturedAt=${Instant.now()}")
            appendLine("---")
        }
        val body = synchronized(lock) { lines.joinToString("\n") }
        return header + body
    }

    private fun append(level: String, tag: String, msg: String, t: Throwable?) {
        val ts = Instant.now().toString()
        val suffix = if (t != null) " | ${t.javaClass.simpleName}: ${t.message}" else ""
        val line = "$ts | $level | $tag | $msg$suffix"
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > CAP) lines.removeFirst()
        }
        if (BuildConfig.DEBUG) {
            when (level) {
                "E" -> android.util.Log.e(tag, msg, t)
                "W" -> android.util.Log.w(tag, msg, t)
                else -> android.util.Log.d(tag, msg)
            }
        }
    }
}
