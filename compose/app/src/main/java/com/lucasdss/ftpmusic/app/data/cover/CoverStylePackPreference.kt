package com.lucasdss.ftpmusic.app.data.cover

import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive active style-pack id (ADR 0151).
 * Seeded synchronously so [CollectionCoverArt] has no lettermark flash on cold start.
 */
@Singleton
class CoverStylePackPreference @Inject constructor(
    private val storage: SecureStorage,
    private val catalog: DefaultCollectionCoverCatalog,
) {
    private val _packId = MutableStateFlow(readNormalized())
    val packId: StateFlow<String> = _packId.asStateFlow()

    fun current(): String = _packId.value

    fun set(packId: String) {
        val normalized = normalize(packId)
        if (storage.get(SecureStorage.KEY_COVER_STYLE_PACK) != normalized) {
            storage.put(SecureStorage.KEY_COVER_STYLE_PACK, normalized)
        }
        _packId.value = normalized
    }

    fun normalize(packId: String): String {
        val id = packId.trim()
        val known = catalog.packs().map { it.id }.toSet()
        val fallback = catalog.defaultPackId()
        return if (id.isNotBlank() && id in known) id else fallback
    }

    private fun readNormalized(): String {
        val saved = storage.get(SecureStorage.KEY_COVER_STYLE_PACK).orEmpty()
        val normalized = normalize(saved.ifBlank { catalog.defaultPackId() })
        if (storage.get(SecureStorage.KEY_COVER_STYLE_PACK) != normalized) {
            storage.put(SecureStorage.KEY_COVER_STYLE_PACK, normalized)
        }
        return normalized
    }
}
