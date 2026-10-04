package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.security.SecureStorage

/** Read/write BT resume prefs with migration from KEY_CAR_BT_* (ADR-0072). */
object BtResumeStorage {
    fun isEnabled(storage: SecureStorage): Boolean {
        val modern = storage.get(SecureStorage.KEY_BT_RESUME_ENABLED)?.toBooleanStrictOrNull()
        if (modern != null) return modern
        return storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED)?.toBooleanStrictOrNull() == true
    }

    fun setEnabled(storage: SecureStorage, enabled: Boolean) {
        storage.put(SecureStorage.KEY_BT_RESUME_ENABLED, enabled.toString())
    }

    fun mode(storage: SecureStorage): BtResumeMode =
        BtResumeMode.fromStorage(storage.get(SecureStorage.KEY_BT_RESUME_MODE))

    fun setMode(storage: SecureStorage, mode: BtResumeMode) {
        storage.put(SecureStorage.KEY_BT_RESUME_MODE, BtResumeMode.toStorage(mode))
    }

    fun allowlist(storage: SecureStorage): Set<String> {
        val modern = storage.get(SecureStorage.KEY_BT_DEVICE_MACS)
        if (!modern.isNullOrBlank()) return BtResumePolicy.parseMacAllowlist(modern)
        return BtResumePolicy.parseMacAllowlist(storage.get(SecureStorage.KEY_CAR_BT_DEVICE_MACS))
    }

    fun setAllowlist(storage: SecureStorage, macs: Collection<String>) {
        storage.put(SecureStorage.KEY_BT_DEVICE_MACS, BtResumePolicy.encodeMacAllowlist(macs))
    }
}
