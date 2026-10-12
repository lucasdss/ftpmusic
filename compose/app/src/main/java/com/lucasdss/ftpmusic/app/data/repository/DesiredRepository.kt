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
 * Local Desired wishlist (ADR-0129). Cross-key merge: resolve by MBID, then
 * Deezer id, then normalized title+artist; persist strongest identity_key.
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
            // Entity type always in key (mb:ARTIST:… / dz:ALBUM:…) — avoids
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
     * Find an existing Desired row matching any known identity (MBID / Deezer / name).
     * Used by Discover + Artist/Album detail so Deezer-added rows resolve correctly.
     */
    suspend fun findMatchingDesired(
        entityType: String,
        title: String,
        artistName: String? = null,
        musicbrainzId: String? = null,
        deezerId: String? = null,
    ): DesiredItemEntity? {
        val type = entityType.uppercase()
        val mbid = musicbrainzId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val dz = deezerId?.trim()?.takeIf { it.isNotEmpty() }
        val candidates = LinkedHashSet<DesiredItemEntity>()
        if (mbid != null) {
            desiredItemDao.getByMusicbrainzId(type, mbid)?.let { candidates.add(it) }
            desiredItemDao.getByIdentityKey("mb:$type:$mbid")?.let { candidates.add(it) }
        }
        if (dz != null) {
            desiredItemDao.getByDeezerId(type, dz)?.let { candidates.add(it) }
            desiredItemDao.getByIdentityKey("dz:$type:$dz")?.let { candidates.add(it) }
        }
        val nmKey = identityKey(type, title, artistName, null, null)
        desiredItemDao.getByIdentityKey(nmKey)?.let { candidates.add(it) }
        // Title bridge: Deezer/MB rows may not share nm: identity_key with detail.
        if (title.isNotBlank()) {
            desiredItemDao.getByNormalizedTitleArtist(
                type,
                title.trim(),
                artistName?.trim().orEmpty(),
            ).forEach { candidates.add(it) }
        }
        return candidates.minByOrNull { strengthRank(it.identityKey) }
    }

    /**
     * Idempotent add with cross-key merge (name/Deezer → MBID upgrades to one row).
     * Preserves matched_local_id. Deletes weaker duplicate rows in one transaction.
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
        val cleanTitle = title.trim()
        val cleanArtist = artistName?.trim()?.takeIf { it.isNotEmpty() }
        val cleanAlbum = albumName?.trim()?.takeIf { it.isNotEmpty() }
        val cleanMbid = musicbrainzId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val cleanDz = deezerId?.trim()?.takeIf { it.isNotEmpty() }

        val matches = collectMatches(type, cleanTitle, cleanArtist, cleanMbid, cleanDz)
        val strongestKey = identityKey(type, cleanTitle, cleanArtist, cleanMbid, cleanDz)

        if (matches.isEmpty()) {
            val entity = DesiredItemEntity(
                id = UUID.randomUUID().toString(),
                identityKey = strongestKey,
                entityType = type,
                title = cleanTitle,
                artistName = cleanArtist,
                albumName = cleanAlbum,
                musicbrainzId = cleanMbid,
                deezerId = cleanDz,
                source = source,
                desiredAt = System.currentTimeMillis(),
                note = note,
            )
            desiredItemDao.mergeReplace(entity, emptyList())
            return entity
        }

        val keepId = matches.minBy { it.desiredAt }.id
        val mergedMbid = cleanMbid ?: matches.firstNotNullOfOrNull { it.musicbrainzId }
        val mergedDz = cleanDz ?: matches.firstNotNullOfOrNull { it.deezerId }
        val finalKey = identityKey(
            type,
            cleanTitle.ifBlank { matches.first().title },
            cleanArtist ?: matches.firstNotNullOfOrNull { it.artistName },
            mergedMbid,
            mergedDz,
        )
        val winner = DesiredItemEntity(
            id = keepId,
            identityKey = finalKey,
            entityType = type,
            title = cleanTitle.ifBlank { matches.first().title },
            artistName = cleanArtist ?: matches.firstNotNullOfOrNull { it.artistName },
            albumName = cleanAlbum ?: matches.firstNotNullOfOrNull { it.albumName },
            musicbrainzId = mergedMbid,
            deezerId = mergedDz,
            source = matches.firstOrNull { it.source != "manual" }?.source ?: source,
            desiredAt = matches.minOf { it.desiredAt },
            matchedLocalId = matches.firstNotNullOfOrNull { it.matchedLocalId },
            note = note ?: matches.firstNotNullOfOrNull { it.note },
        )
        val losers = matches.map { it.id }.filter { it != keepId }
        desiredItemDao.mergeReplace(winner, losers)
        return desiredItemDao.getById(keepId)
            ?: desiredItemDao.getByIdentityKey(finalKey)
            ?: winner
    }

    suspend fun removeDesired(id: String) {
        desiredItemDao.deleteById(id)
    }

    suspend fun removeByIdentityKey(identityKey: String) {
        desiredItemDao.deleteByIdentityKey(identityKey)
    }

    /** Remove any row matching the given identity facets (detail / Discover toggle). */
    suspend fun removeMatching(
        entityType: String,
        title: String,
        artistName: String? = null,
        musicbrainzId: String? = null,
        deezerId: String? = null,
    ) {
        val match = findMatchingDesired(entityType, title, artistName, musicbrainzId, deezerId)
        if (match != null) {
            desiredItemDao.deleteById(match.id)
        }
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

    suspend fun snapshotShareText(): String = buildShareText(desiredItemDao.getAll())

    fun buildShareText(items: List<DesiredItemEntity>): String {
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

    private suspend fun collectMatches(
        type: String,
        title: String,
        artistName: String?,
        mbid: String?,
        deezerId: String?,
    ): List<DesiredItemEntity> {
        val found = LinkedHashMap<String, DesiredItemEntity>()
        fun add(item: DesiredItemEntity?) {
            if (item != null) found[item.id] = item
        }
        if (mbid != null) {
            add(desiredItemDao.getByMusicbrainzId(type, mbid))
            add(desiredItemDao.getByIdentityKey("mb:$type:$mbid"))
        }
        if (deezerId != null) {
            add(desiredItemDao.getByDeezerId(type, deezerId))
            add(desiredItemDao.getByIdentityKey("dz:$type:$deezerId"))
        }
        add(desiredItemDao.getByIdentityKey(identityKey(type, title, artistName, null, null)))
        // Also catch incoming strongest key if already present.
        add(desiredItemDao.getByIdentityKey(identityKey(type, title, artistName, mbid, deezerId)))
        if (title.isNotBlank()) {
            desiredItemDao.getByNormalizedTitleArtist(
                type,
                title.trim(),
                artistName?.trim().orEmpty(),
            ).forEach { add(it) }
        }
        return found.values.toList()
    }

    private fun strengthRank(identityKey: String): Int = when {
        identityKey.startsWith("mb:") -> 0
        identityKey.startsWith("dz:") -> 1
        else -> 2
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
        val artist = item.artistName?.takeIf { it.isNotBlank() } ?: return null
        return try {
            metadataDao.getAlbumByExactName(item.title, artist)?.id
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
        val artist = item.artistName?.takeIf { it.isNotBlank() } ?: return null
        return try {
            trackDao.getTrackByExactTitleArtist(item.title, artist)?.id
        } catch (_: Exception) {
            null
        }
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
