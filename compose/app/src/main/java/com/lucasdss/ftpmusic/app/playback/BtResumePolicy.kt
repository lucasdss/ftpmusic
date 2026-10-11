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

    /** Debounce key when ANY mode cannot read the device MAC (ADR-0088). */
    const val ANY_UNKNOWN_MAC_KEY = "*"

    /** Debounce key for SELECTED + unreadable MAC Resume notif (ADR-0101 / ADR-0121). */
    const val SELECTED_NULL_MAC_KEY = "selected_null_mac"

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
        val normalized = normalizeMac(deviceMac)
        val mac = when {
            normalized != null -> normalized

            // ADR-0088: ANY still arms when CONNECT perm denies address read.
            mode == BtResumeMode.ANY -> ANY_UNKNOWN_MAC_KEY

            else -> return false
        }
        when (mode) {
            BtResumeMode.ANY -> Unit

            BtResumeMode.SELECTED -> {
                if (allowlistedMacs.isEmpty()) return false
                if (mac !in allowlistedMacs) return false
            }
        }
        // ADR-0121: warm path often stamps "*", ACL later has real MAC — collapse
        // both into one connect window in ANY mode.
        if (isDebounced(mac, nowMs, lastAcceptedAtMsByMac, debounceMs)) return false
        if (mode == BtResumeMode.ANY &&
            mac != ANY_UNKNOWN_MAC_KEY &&
            isDebounced(ANY_UNKNOWN_MAC_KEY, nowMs, lastAcceptedAtMsByMac, debounceMs)
        ) {
            return false
        }
        if (mode == BtResumeMode.ANY &&
            mac == ANY_UNKNOWN_MAC_KEY &&
            lastAcceptedAtMsByMac.any { (key, at) ->
                key != ANY_UNKNOWN_MAC_KEY &&
                    key != SELECTED_NULL_MAC_KEY &&
                    nowMs - at < debounceMs
            }
        ) {
            return false
        }
        lastAcceptedAtMsByMac[mac] = nowMs
        if (mode == BtResumeMode.ANY) {
            lastAcceptedAtMsByMac[ANY_UNKNOWN_MAC_KEY] = nowMs
        }
        return true
    }

    /** True when [key] was accepted inside the debounce window. */
    fun isDebounced(
        key: String,
        nowMs: Long,
        lastAcceptedAtMsByMac: MutableMap<String, Long>,
        debounceMs: Long = DEBOUNCE_MS,
    ): Boolean {
        val last = lastAcceptedAtMsByMac[key] ?: return false
        return nowMs - last < debounceMs
    }

    /** Stamp [key] if outside window; return true when the stamp succeeded (not debounced). */
    fun acceptDebounced(
        key: String,
        nowMs: Long,
        lastAcceptedAtMsByMac: MutableMap<String, Long>,
        debounceMs: Long = DEBOUNCE_MS,
    ): Boolean {
        if (isDebounced(key, nowMs, lastAcceptedAtMsByMac, debounceMs)) return false
        lastAcceptedAtMsByMac[key] = nowMs
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
