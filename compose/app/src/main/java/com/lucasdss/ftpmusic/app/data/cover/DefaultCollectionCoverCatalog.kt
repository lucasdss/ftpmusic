package com.lucasdss.ftpmusic.app.data.cover

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads bundled style packs + subject catalog from
 * `assets/collection_defaults/` (ADR 0151).
 */
@Singleton
class DefaultCollectionCoverCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Pack(
        val id: String,
        val label: String,
        val isDefault: Boolean,
    )

    data class ResolvedAsset(
        val packId: String,
        val assetPath: String,
    )

    private val packsRef = AtomicReference<List<Pack>?>(null)
    private val genreSlugRef = AtomicReference<Map<String, String>?>(null)
    private val mixSlugRef = AtomicReference<Map<String, String>?>(null)
    private val playlistSlugRef = AtomicReference<Map<String, String>?>(null)

    fun packs(): List<Pack> = packsRef.get() ?: loadPacks().also { packsRef.set(it) }

    fun defaultPackId(): String =
        packs().firstOrNull { it.isDefault }?.id
            ?: packs().firstOrNull()?.id
            ?: "anime"

    fun resolveGenre(packId: String, genreName: String): ResolvedAsset? {
        val slug = genreSlug()[normalize(genreName)] ?: "default"
        return assetIfExists(packId, "genres/$slug.webp")
            ?: assetIfExists(packId, "genres/default.webp")
    }

    fun resolveMix(packId: String, mixName: String): ResolvedAsset? {
        val slug = mixSlug()[normalize(mixName)]
            ?: genreSlug()[normalize(mixName.removeSuffix(" mix"))]
            ?: hashSlug(mixName)
        return assetIfExists(packId, "mixes/${slug}_mix.webp")
            ?: assetIfExists(packId, "mixes/$slug.webp")
            ?: assetIfExists(packId, "mixes/default.webp")
    }

    fun resolvePlaylist(packId: String, playlistName: String): ResolvedAsset? {
        val slug = playlistSlug()[normalize(playlistName)] ?: return assetIfExists(packId, "playlists/default.webp")
        return assetIfExists(packId, "playlists/$slug.webp")
            ?: assetIfExists(packId, "playlists/default.webp")
    }

    fun assetUri(assetPath: String): String = "file:///android_asset/$assetPath"

    private fun assetIfExists(packId: String, relative: String): ResolvedAsset? {
        val path = "collection_defaults/$packId/$relative"
        return try {
            context.assets.open(path).use { /* exists */ }
            ResolvedAsset(packId, path)
        } catch (_: Exception) {
            null
        }
    }

    private fun genreSlug(): Map<String, String> =
        genreSlugRef.get() ?: loadCatalogMaps().first.also { genreSlugRef.set(it) }

    private fun mixSlug(): Map<String, String> =
        mixSlugRef.get() ?: loadCatalogMaps().second.also { mixSlugRef.set(it) }

    private fun playlistSlug(): Map<String, String> =
        playlistSlugRef.get() ?: loadCatalogMaps().third.also { playlistSlugRef.set(it) }

    private fun loadPacks(): List<Pack> {
        val json = readAssetJson("collection_defaults/packs.json") ?: return listOf(
            Pack("anime", "Anime", true),
        )
        val arr = json.optJSONArray("packs") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                add(
                    Pack(
                        id = id,
                        label = o.optString("label", id),
                        isDefault = o.optBoolean("default", false),
                    ),
                )
            }
        }
    }

    private fun loadCatalogMaps(): Triple<Map<String, String>, Map<String, String>, Map<String, String>> {
        val json = readAssetJson("collection_defaults/catalog.json")
            ?: return Triple(emptyMap(), emptyMap(), emptyMap())
        fun mapFrom(key: String): Map<String, String> {
            val obj = json.optJSONObject(key) ?: return emptyMap()
            val out = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k)
                if (v.isNotBlank()) out[normalize(k)] = v
            }
            return out
        }
        return Triple(mapFrom("genres"), mapFrom("mixes"), mapFrom("playlists"))
    }

    private fun readAssetJson(path: String): JSONObject? = try {
        context.assets.open(path).bufferedReader().use { JSONObject(it.readText()) }
    } catch (_: Exception) {
        null
    }

    private fun normalize(name: String): String =
        name.trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")

    private fun hashSlug(name: String): String {
        val h = name.lowercase().hashCode().and(0x7fffffff)
        return "h%04x".format(h % 0x10000)
    }
}
