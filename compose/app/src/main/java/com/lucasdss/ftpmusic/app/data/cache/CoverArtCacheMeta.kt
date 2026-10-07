package com.lucasdss.ftpmusic.app.data.cache

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Sidecar metadata for a cover-art disk file (`{image}.meta`).
 *
 * Soft entries (Navidrome placeholders / shared content hashes) revalidate
 * after [SOFT_TTL_MS]; real art after [REAL_TTL_MS]. See ADR-0090.
 */
data class CoverArtCacheMeta(
    val contentSha256: String,
    val etag: String? = null,
    val lastModified: String? = null,
    val fetchedAtMs: Long,
    val softPlaceholder: Boolean,
) {
    fun toJson(): String = JSONObject().apply {
        put("contentSha256", contentSha256)
        put("etag", etag ?: JSONObject.NULL)
        put("lastModified", lastModified ?: JSONObject.NULL)
        put("fetchedAtMs", fetchedAtMs)
        put("softPlaceholder", softPlaceholder)
    }.toString()

    companion object {
        const val SOFT_TTL_MS = 24L * 60 * 60 * 1000
        const val REAL_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val SHARED_HASH_SOFT_THRESHOLD = 3

        fun fromJson(raw: String): CoverArtCacheMeta? = try {
            val o = JSONObject(raw)
            CoverArtCacheMeta(
                contentSha256 = o.getString("contentSha256"),
                etag = if (o.isNull("etag")) null else o.optString("etag").takeIf { it.isNotBlank() },
                lastModified = if (o.isNull("lastModified")) {
                    null
                } else {
                    o.optString("lastModified").takeIf { it.isNotBlank() }
                },
                fetchedAtMs = o.getLong("fetchedAtMs"),
                softPlaceholder = o.optBoolean("softPlaceholder", false),
            )
        } catch (_: Exception) {
            null
        }

        fun metaFileFor(imageFile: File): File = File(imageFile.parentFile, "${imageFile.name}.meta")

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { b -> "%02x".format(b) }
        }

        fun read(imageFile: File): CoverArtCacheMeta? {
            val meta = metaFileFor(imageFile)
            if (!meta.exists()) return null
            return try {
                fromJson(meta.readText())
            } catch (_: Exception) {
                null
            }
        }

        fun write(imageFile: File, meta: CoverArtCacheMeta) {
            val target = metaFileFor(imageFile)
            val tmp = File(target.parentFile, "${target.name}.tmp")
            try {
                tmp.writeText(meta.toJson())
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) tmp.delete()
            } catch (_: Exception) {
                tmp.delete()
            }
        }

        fun delete(imageFile: File) {
            try {
                metaFileFor(imageFile).delete()
            } catch (_: Exception) {
            }
        }

        /** Fresh when meta exists and age is under soft/real TTL. Legacy (no meta) = stale. */
        fun isFresh(imageFile: File, nowMs: Long = System.currentTimeMillis()): Boolean {
            val meta = read(imageFile) ?: return false
            val ttl = if (meta.softPlaceholder) SOFT_TTL_MS else REAL_TTL_MS
            return nowMs - meta.fetchedAtMs < ttl
        }

        fun touchFetchedAt(imageFile: File, nowMs: Long = System.currentTimeMillis()): CoverArtCacheMeta? {
            val existing = read(imageFile) ?: return null
            val updated = existing.copy(fetchedAtMs = nowMs)
            write(imageFile, updated)
            return updated
        }
    }
}

/**
 * Known Navidrome placeholder content hashes. Tests may add fixtures via
 * [addKnownPlaceholderSha]; production relies primarily on the shared-hash
 * heuristic (≥3 distinct navidrome ids with the same SHA).
 */
object CoverArtPlaceholders {
    private val known = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun isKnownPlaceholder(sha256: String): Boolean = known.contains(sha256)

    fun addKnownPlaceholderSha(sha256: String) {
        known.add(sha256.lowercase())
    }

    fun clearKnownForTests() {
        known.clear()
    }
}
