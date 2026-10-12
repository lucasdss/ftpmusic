package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.DesiredItemDao
import com.lucasdss.ftpmusic.app.data.db.DesiredItemEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local Desired wishlist (ADR-0129). Idempotent upsert by identity key:
 * MBID → Deezer id → normalized title+artist.
 */
@Singleton
class DesiredRepository @Inject constructor(
    private val desiredItemDao: DesiredItemDao,
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
) {
    companion object {
        const val TYPE_ARTIST = "ARTIST"
        const val TYPE_ALBUM = "ALBUM"
        const val TYPE_TRACK = "TRACK"

        fun identityKey(
            entityType: String,
            title: String,
            artistName: String?,
            musicbrainzId: String?,
            deezerId: String?,
        ): String {
            val type = entityType.uppercase()
            // Entity type is always in the key (mb:ARTIST:… / dz:ALBUM:…) — avoids
            // Deezer numeric id collisions across artist/album/track namespaces.
            val mbid = musicbrainzId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (mbid != null) return "mb:$type:$mbid"
            val dz = deezerId?.trim()?.takeIf { it.isNotEmpty() }
            if (dz != null) return "dz:$type:$dz"
            val t = normalize(title)
            val a = normalize(artistName.orEmpty())
            return "nm:$type:$t|$a"
        }

        fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    fun observeAll(): Flow<List<DesiredItemEntity>> = desiredItemDao.observeAll()

    fun observeSearch(rawQuery: String): Flow<List<DesiredItemEntity>> {
        val q = escapeLike(rawQuery.trim())
        return desiredItemDao.observeSearch(q)
    }

    suspend fun search(rawQuery: String): List<DesiredItemEntity> {
        val q = escapeLike(rawQuery.trim())
        return desiredItemDao.search(q)
    }

    suspend fun getAll(): List<DesiredItemEntity> = desiredItemDao.getAll()

    /**
     * Idempotent add. Merges external ids into existing row when identity matches.
     * @return persisted entity (existing or new).
     */
    suspend fun addDesired(
        entityType: String,
        title: String,
        artistName: String? = null,
        albumName: String? = null,
        musicbrainzId: String? = null,
        deezerId: String? = null,
        source: String = "manual",
        note: String? = null,
    ): DesiredItemEntity {
        val type = entityType.uppercase()
        val key = identityKey(type, title, artistName, musicbrainzId, deezerId)
        val existing = desiredItemDao.getByIdentityKey(key)
        if (existing != null) {
            val merged = existing.copy(
                title = title.ifBlank { existing.title },
                artistName = artistName?.takeIf { it.isNotBlank() } ?: existing.artistName,
                albumName = albumName?.takeIf { it.isNotBlank() } ?: existing.albumName,
                musicbrainzId = musicbrainzId?.takeIf { it.isNotBlank() } ?: existing.musicbrainzId,
                deezerId = deezerId?.takeIf { it.isNotBlank() } ?: existing.deezerId,
                source = if (existing.source == "manual") source else existing.source,
                note = note ?: existing.note,
            )
            // Prefer stronger identity if we gained an MBID after a name-key insert.
            val strongerKey = identityKey(
                type,
                merged.title,
                merged.artistName,
                merged.musicbrainzId,
                merged.deezerId,
            )
            if (strongerKey != existing.identityKey) {
                desiredItemDao.deleteById(existing.id)
                val upgraded = merged.copy(id = existing.id, identityKey = strongerKey)
                desiredItemDao.upsert(upgraded)
                return upgraded
            }
            desiredItemDao.upsert(merged)
            return merged
        }
        val entity = DesiredItemEntity(
            id = UUID.randomUUID().toString(),
            identityKey = key,
            entityType = type,
            title = title.trim(),
            artistName = artistName?.trim()?.takeIf { it.isNotEmpty() },
            albumName = albumName?.trim()?.takeIf { it.isNotEmpty() },
            musicbrainzId = musicbrainzId?.trim()?.takeIf { it.isNotEmpty() },
            deezerId = deezerId?.trim()?.takeIf { it.isNotEmpty() },
            source = source,
            desiredAt = System.currentTimeMillis(),
            note = note,
        )
        desiredItemDao.upsert(entity)
        return entity
    }

    suspend fun removeDesired(id: String) {
        desiredItemDao.deleteById(id)
    }

    suspend fun removeByIdentityKey(identityKey: String) {
        desiredItemDao.deleteByIdentityKey(identityKey)
    }

    /** Match Desired rows to local library; set matched_local_id (keep row). */
    suspend fun reconcileAgainstLibrary() {
        val items = desiredItemDao.getAll()
        for (item in items) {
            val match = when (item.entityType) {
                TYPE_ARTIST -> matchArtist(item)
                TYPE_ALBUM -> matchAlbum(item)
                TYPE_TRACK -> matchTrack(item)
                else -> null
            }
            if (match != item.matchedLocalId) {
                desiredItemDao.setMatchedLocalId(item.id, match)
            }
        }
    }

    fun observeDesiredIdentityKeys(): Flow<Set<String>> =
        desiredItemDao.observeAll().map { list -> list.map { it.identityKey }.toSet() }

    fun snapshotShareText(items: List<DesiredItemEntity> = emptyList()): String {
        // Caller may pass preloaded list; empty → sync load happens in suspend wrapper.
        return buildShareText(items)
    }

    suspend fun snapshotShareText(): String = buildShareText(desiredItemDao.getAll())

    private fun buildShareText(items: List<DesiredItemEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("ftpmusic desired list")
        sb.appendLine("count=${items.size}")
        sb.appendLine("capturedAt=${Instant.now()}")
        sb.appendLine("---")
        for (item in items.sortedByDescending { it.desiredAt }) {
            val title = sanitizeShareField(item.title)
            val artist = item.artistName?.takeIf { it.isNotBlank() }
                ?.let { " — ${sanitizeShareField(it)}" }.orEmpty()
            val ids = buildList {
                add("source=${item.source}")
                item.musicbrainzId?.let { add("mbid=$it") }
                item.deezerId?.let { add("deezer=$it") }
                item.matchedLocalId?.let { add("inLibrary=$it") }
            }.joinToString(" ")
            sb.appendLine("[${item.entityType}] $title$artist ($ids)")
        }
        return sb.toString().trimEnd()
    }

    private suspend fun matchArtist(item: DesiredItemEntity): String? {
        item.musicbrainzId?.let { mbid ->
            try {
                metadataDao.getArtistByMbid(mbid)?.id?.let { return it }
            } catch (_: Exception) {
            }
        }
        return try {
            metadataDao.getArtistByExactName(item.title)?.id
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun matchAlbum(item: DesiredItemEntity): String? {
        item.musicbrainzId?.let { mbid ->
            try {
                metadataDao.getAlbumByMbid(mbid)?.id?.let { return it }
            } catch (_: Exception) {
            }
        }
        return try {
            metadataDao.getAlbumByExactName(item.title, item.artistName)?.id
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun matchTrack(item: DesiredItemEntity): String? {
        item.musicbrainzId?.let { mbid ->
            try {
                trackDao.getTrackByMbid(mbid)?.id?.let { return it }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun escapeLike(raw: String): String {
        if (raw.isEmpty()) return ""
        return raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    private fun sanitizeShareField(raw: String): String = raw.replace('\n', ' ').replace('\r', ' ').trim()
}
