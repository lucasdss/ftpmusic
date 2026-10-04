package com.lucasdss.ftpmusic.app.playback

/**
 * Pure gate for Bluetooth A2DP resume (ADR-0072). Unit-testable; no Android deps
 * beyond primitives.
 */
enum class BtResumeMode {
    /** Resume on any A2DP audio sink connect. */
    ANY,

    /** Resume only for allowlisted bonded MACs. */
    SELECTED,
    ;

    companion object {
        fun fromStorage(raw: String?): BtResumeMode = when (raw?.trim()?.lowercase()) {
            "any" -> ANY
            else -> SELECTED
        }

        fun toStorage(mode: BtResumeMode): String = when (mode) {
            ANY -> "any"
            SELECTED -> "selected"
        }
    }
}

object BtResumePolicy {
    const val DEBOUNCE_MS = 5_000L

    fun shouldResume(
        enabled: Boolean,
        mode: BtResumeMode,
        allowlistedMacs: Set<String>,
        deviceMac: String?,
        casting: Boolean,
        nowMs: Long,
        lastAcceptedAtMsByMac: MutableMap<String, Long>,
        debounceMs: Long = DEBOUNCE_MS,
    ): Boolean {
        if (!enabled) return false
        if (casting) return false
        val mac = normalizeMac(deviceMac) ?: return false
        when (mode) {
            BtResumeMode.ANY -> Unit

            BtResumeMode.SELECTED -> {
                if (allowlistedMacs.isEmpty()) return false
                if (mac !in allowlistedMacs) return false
            }
        }
        val last = lastAcceptedAtMsByMac[mac]
        if (last != null && nowMs - last < debounceMs) return false
        lastAcceptedAtMsByMac[mac] = nowMs
        return true
    }

    fun normalizeMac(raw: String?): String? {
        val trimmed = raw?.trim()?.uppercase() ?: return null
        if (trimmed.isEmpty() || trimmed == "00:00:00:00:00:00") return null
        return trimmed
    }

    fun parseMacAllowlist(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            val inner = trimmed.removePrefix("[").removeSuffix("]")
            if (inner.isBlank()) return emptySet()
            return inner.split(',')
                .mapNotNull { token ->
                    normalizeMac(token.trim().trim('"').trim('\''))
                }
                .toSet()
        }
        return trimmed.split(',', '|')
            .mapNotNull { normalizeMac(it.trim().trim('"')) }
            .toSet()
    }

    fun encodeMacAllowlist(macs: Collection<String>): String {
        val normalized = macs.mapNotNull { normalizeMac(it) }.distinct().sorted()
        return normalized.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
    }
}
