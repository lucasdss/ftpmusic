package com.lucasdss.ftpmusic.app.data.cover

import java.io.File

/**
 * Identity + version for the art the UI actually shows, shared by Coil render
 * and palette extraction so the gradient cannot drift from the cover.
 */
data class ResolvedCover(
    val resolved: CollectionCoverResolver.Resolved,
    /** Stable cache key scoped by caller (include server when known). */
    val artKey: String,
    /** Invalidates when bytes/pack/montage members change. */
    val sourceVersion: String,
    /**
     * Coil model for the primary displayed image, or null for lettermark /
     * missing (seed comes from name hue or neutral).
     */
    val imageModel: Any?,
    /** Ordered cover-art ids for montage average (detail only). */
    val montageCoverArtIds: List<String> = emptyList(),
    val lettermarkName: String? = null,
) {
    val isLettermark: Boolean get() = resolved is CollectionCoverResolver.Resolved.Lettermark
    val isMissing: Boolean get() = imageModel == null && lettermarkName == null
}

object ResolvedCoverFactory {

    fun fromResolver(
        resolved: CollectionCoverResolver.Resolved,
        scope: String,
        localModelUrl: String? = null,
        bundledAssetUri: String? = null,
        navidromeOrDerivedUrl: String? = null,
    ): ResolvedCover {
        return when (resolved) {
            is CollectionCoverResolver.Resolved.LocalFile -> {
                val file = File(resolved.absolutePath)
                val version = "${file.lastModified()}:${file.length()}"
                ResolvedCover(
                    resolved = resolved,
                    artKey = "$scope|local|${resolved.absolutePath}",
                    sourceVersion = version,
                    imageModel = localModelUrl ?: "file://${resolved.absolutePath}",
                )
            }
            is CollectionCoverResolver.Resolved.Navidrome -> ResolvedCover(
                resolved = resolved,
                artKey = "$scope|nav|${resolved.coverArtId}",
                sourceVersion = resolved.coverArtId,
                imageModel = navidromeOrDerivedUrl,
            )
            is CollectionCoverResolver.Resolved.Derived -> {
                val ids = resolved.coverArtIds.filter { it.isNotBlank() }
                ResolvedCover(
                    resolved = resolved,
                    artKey = "$scope|derived|${ids.joinToString(",")}",
                    sourceVersion = ids.joinToString("|"),
                    imageModel = navidromeOrDerivedUrl,
                    montageCoverArtIds = ids.take(4),
                )
            }
            is CollectionCoverResolver.Resolved.Bundled -> ResolvedCover(
                resolved = resolved,
                artKey = "$scope|bundled|${resolved.packId}|${resolved.assetPath}",
                sourceVersion = "${resolved.packId}:${resolved.assetPath}",
                imageModel = bundledAssetUri ?: resolved.assetPath,
            )
            is CollectionCoverResolver.Resolved.Lettermark -> ResolvedCover(
                resolved = resolved,
                artKey = "$scope|letter|${resolved.name.lowercase()}",
                sourceVersion = "lettermark:v1",
                imageModel = null,
                lettermarkName = resolved.name,
            )
        }
    }

    /**
     * Album / artist hero from a single preferred URL.
     * [sourceVersion] must never embed credential-bearing URLs (ADR 0157) —
     * use art id or a non-secret content stamp only.
     */
    fun fromImageUrl(
        scope: String,
        artId: String?,
        imageUrl: String?,
        contentStamp: String? = null,
    ): ResolvedCover {
        val key = artId?.takeIf { it.isNotBlank() }
            ?: contentStamp?.takeIf { it.isNotBlank() }
            ?: "missing"
        if (imageUrl.isNullOrBlank()) {
            return ResolvedCover(
                resolved = CollectionCoverResolver.Resolved.Lettermark("?"),
                artKey = "$scope|missing|$key",
                sourceVersion = "missing",
                imageModel = null,
                lettermarkName = null,
            )
        }
        val version = contentStamp?.takeIf { it.isNotBlank() }
            ?: artId?.takeIf { it.isNotBlank() }
            ?: "url:${imageUrl.hashCode()}"
        return ResolvedCover(
            resolved = CollectionCoverResolver.Resolved.Navidrome(artId ?: key),
            artKey = "$scope|url|$key",
            sourceVersion = version,
            imageModel = imageUrl,
        )
    }
}
