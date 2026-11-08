package com.lucasdss.ftpmusic.app.data.cover

/**
 * Pure resolution for Daily Mix / playlist / genre collection art (ADR 0115 / 0151).
 *
 * Priority: fixed navidrome → fixed local file → derived cover ids → bundled pack → lettermark.
 * UI still runs preferred/fallback for network tiers; this picks id/path/bundled/lettermark.
 */
object CollectionCoverResolver {

    sealed class Resolved {
        data class Navidrome(val coverArtId: String) : Resolved()
        data class LocalFile(val absolutePath: String) : Resolved()
        data class Derived(val coverArtIds: List<String>) : Resolved() {
            val primary: String? get() = coverArtIds.firstOrNull()
        }
        data class Bundled(val packId: String, val assetPath: String) : Resolved()
        data class Lettermark(val name: String) : Resolved()
    }

    data class Input(
        val name: String,
        val fixedKind: String?,
        val fixedValue: String?,
        val derivedCoverArtIds: List<String> = emptyList(),
        /** Absolute path when fixedKind=local and file still exists. */
        val localAbsolutePath: String? = null,
        /** Server playlist coverArt when no fixed cover. */
        val serverCoverArtId: String? = null,
        /** Bundled default from active style pack (ADR 0151). */
        val bundledPackId: String? = null,
        val bundledAssetPath: String? = null,
        /** When true, skip derived/server and go fixed → bundled → lettermark (genres). */
        val skipDerived: Boolean = false,
    )

    /**
     * True when a fixed cover should win over montage/lettermark in list/detail UI.
     * LOCAL requires a resolvable file; BUNDLED needs a non-blank asset path;
     * missing file → treat as no fixed cover.
     */
    fun isUsableFixed(kind: String?, value: String?, localAbsolutePath: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return when (kind) {
            CollectionCoverKind.NAVIDROME -> true
            CollectionCoverKind.LOCAL -> !localAbsolutePath.isNullOrBlank()
            CollectionCoverKind.BUNDLED -> true
            // DERIVED is an opt-in collage flag, not a usable fixed asset.
            CollectionCoverKind.DERIVED -> false
            else -> false
        }
    }

    fun resolve(input: Input): Resolved {
        val kind = input.fixedKind
        val value = input.fixedValue?.takeIf { it.isNotBlank() }
        when {
            kind == CollectionCoverKind.NAVIDROME && value != null ->
                return Resolved.Navidrome(value)

            kind == CollectionCoverKind.LOCAL && !input.localAbsolutePath.isNullOrBlank() ->
                return Resolved.LocalFile(input.localAbsolutePath)

            kind == CollectionCoverKind.LOCAL && value != null -> {
                // File missing/corrupt — fall through.
            }

            kind == CollectionCoverKind.BUNDLED && value != null -> {
                val packId = value.substringAfter("collection_defaults/", missingDelimiterValue = "")
                    .substringBefore('/')
                    .ifBlank { "anime" }
                return Resolved.Bundled(packId, value)
            }

            kind == CollectionCoverKind.DERIVED -> {
                // Fall through to derived/montage (ignore skipDerived).
            }
        }
        val forceDerived = kind == CollectionCoverKind.DERIVED
        if (forceDerived || !input.skipDerived) {
            val derived = buildList {
                input.derivedCoverArtIds.forEach { id ->
                    if (id.isNotBlank() && id !in this) add(id)
                }
                val server = input.serverCoverArtId?.takeIf { it.isNotBlank() }
                if (server != null && server !in this) add(server)
            }
            if (derived.isNotEmpty()) return Resolved.Derived(derived)
        }
        val pack = input.bundledPackId?.takeIf { it.isNotBlank() }
        val asset = input.bundledAssetPath?.takeIf { it.isNotBlank() }
        if (pack != null && asset != null) {
            return Resolved.Bundled(pack, asset)
        }
        return Resolved.Lettermark(input.name.ifBlank { "?" })
    }

    /** Initials for lettermark: up to 2 chars from name words. */
    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts[0].first()}${parts[1].first()}".uppercase()
        }
    }

    /** Stable hue 0..359 from name hash — brand-adjacent teal/purple band. */
    fun hueFromName(name: String): Float {
        val h = name.lowercase().hashCode()
        // Map into 160..300 (teal → purple) for dark UI contrast.
        return 160f + (h.and(0x7fff) % 141)
    }
}
