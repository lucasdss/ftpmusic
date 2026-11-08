package com.lucasdss.ftpmusic.app.data.cover

/**
 * Defaults strip ordering: active pack first, then remaining packs (stable by id).
 */
object CollectionCoverPackOrder {
    data class PackThumb(val packId: String, val label: String, val assetPath: String)

    fun orderedThumbs(
        packs: List<DefaultCollectionCoverCatalog.Pack>,
        activePackId: String,
        resolveAsset: (packId: String) -> String?,
    ): List<PackThumb> {
        if (packs.isEmpty()) return emptyList()
        val active = packs.firstOrNull { it.id == activePackId } ?: packs.first()
        val rest = packs.filter { it.id != active.id }.sortedBy { it.id }
        return (listOf(active) + rest).mapNotNull { pack ->
            val path = resolveAsset(pack.id)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PackThumb(pack.id, pack.label, path)
        }
    }
}
