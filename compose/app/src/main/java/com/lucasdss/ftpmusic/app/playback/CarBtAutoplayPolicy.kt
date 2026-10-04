package com.lucasdss.ftpmusic.app.playback

/**
 * Pure gate for car-Bluetooth resume (ADR-0071). Unit-testable; no Android deps
 * beyond primitives.
 */
object CarBtAutoplayPolicy {
    const val DEBOUNCE_MS = 5_000L

    /**
     * @param enabled Settings toggle
     * @param allowlistedMacs Normalized (uppercase) MACs from SecureStorage
     * @param deviceMac Raw MAC from the BT intent (may be mixed case)
     * @param casting Skip when Cast owns the session
     * @param nowMs Clock for debounce
     * @param lastAcceptedAtMsByMac Mutable debounce map (MAC → last accept millis)
     */
    fun shouldResume(
        enabled: Boolean,
        allowlistedMacs: Set<String>,
        deviceMac: String?,
        casting: Boolean,
        nowMs: Long,
        lastAcceptedAtMsByMac: MutableMap<String, Long>,
        debounceMs: Long = DEBOUNCE_MS,
    ): Boolean {
        if (!enabled) return false
        if (casting) return false
        if (allowlistedMacs.isEmpty()) return false
        val mac = normalizeMac(deviceMac) ?: return false
        if (mac !in allowlistedMacs) return false
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
        // JSON array preferred: ["AA:BB:...","CC:DD:..."]
        if (trimmed.startsWith("[")) {
            val inner = trimmed.removePrefix("[").removeSuffix("]")
            if (inner.isBlank()) return emptySet()
            return inner.split(',')
                .mapNotNull { token ->
                    normalizeMac(token.trim().trim('"').trim('\''))
                }
                .toSet()
        }
        // Fallback: comma / || separated
        return trimmed.split(',', '|')
            .mapNotNull { normalizeMac(it.trim().trim('"')) }
            .toSet()
    }

    fun encodeMacAllowlist(macs: Collection<String>): String {
        val normalized = macs.mapNotNull { normalizeMac(it) }.distinct().sorted()
        return normalized.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
    }
}
