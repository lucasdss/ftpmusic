package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverKind
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverResolver
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverStore
import com.lucasdss.ftpmusic.app.data.cover.DefaultCollectionCoverCatalog
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.ui.library.rememberPreferredCoverArt
import com.lucasdss.ftpmusic.app.ui.textHeadingL
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import java.io.File

enum class CollectionCoverSubject {
    MIX,
    PLAYLIST,
    GENRE,
}

/**
 * Shared collection cover presenter for Daily Mix / playlist / genre cards.
 * Fixed → derived (optional) → bundled style pack → lettermark (ADR 0151).
 */
@Composable
fun CollectionCoverArt(
    name: String,
    fixedCoverKind: String?,
    fixedCoverValue: String?,
    derivedCoverArtIds: List<String>,
    modifier: Modifier = Modifier,
    serverCoverArtId: String? = null,
    primaryArtist: String? = null,
    primaryAlbum: String? = null,
    decodeSize: Dp = 120.dp,
    contentDescription: String? = name,
    lettermarkLarge: Boolean = false,
    subject: CollectionCoverSubject = CollectionCoverSubject.MIX,
    stylePackId: String? = null,
    skipDerived: Boolean = subject == CollectionCoverSubject.GENRE,
) {
    val context = LocalContext.current
    val store = remember { CollectionCoverStore(context.applicationContext) }
    val catalog = remember { DefaultCollectionCoverCatalog(context.applicationContext) }
    val storage = remember { SecureStorage(context.applicationContext) }
    val activePack = stylePackId
        ?: storage.get(SecureStorage.KEY_COVER_STYLE_PACK)
        ?: catalog.defaultPackId()
    val bundled = remember(name, activePack, subject) {
        when (subject) {
            CollectionCoverSubject.GENRE -> catalog.resolveGenre(activePack, name)
            CollectionCoverSubject.MIX -> catalog.resolveMix(activePack, name)
            CollectionCoverSubject.PLAYLIST -> catalog.resolvePlaylist(activePack, name)
        }
    }
    val localPath = remember(fixedCoverKind, fixedCoverValue) {
        if (fixedCoverKind == CollectionCoverKind.LOCAL) {
            store.absolutePath(fixedCoverValue.orEmpty())
        } else {
            null
        }
    }
    // Bust Coil memory/disk cache when same relative path is overwritten (ADR 0125).
    val localModelUrl = remember(localPath) {
        val path = localPath ?: return@remember null
        val file = File(path)
        "file://$path?t=${file.lastModified()}&s=${file.length()}"
    }
    val resolved = remember(
        name,
        fixedCoverKind,
        fixedCoverValue,
        derivedCoverArtIds,
        serverCoverArtId,
        localPath,
        bundled?.assetPath,
        skipDerived,
    ) {
        CollectionCoverResolver.resolve(
            CollectionCoverResolver.Input(
                name = name,
                fixedKind = fixedCoverKind,
                fixedValue = fixedCoverValue,
                derivedCoverArtIds = derivedCoverArtIds,
                localAbsolutePath = localPath,
                serverCoverArtId = serverCoverArtId,
                bundledPackId = bundled?.packId,
                bundledAssetPath = bundled?.assetPath,
                skipDerived = skipDerived,
            ),
        )
    }
    val fallback = remember { CoverArtFallbackService.getInstance(context) }

    Box(
        modifier = modifier.background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center,
    ) {
        when (val r = resolved) {
            is CollectionCoverResolver.Resolved.LocalFile -> {
                CoverArtImage(
                    url = localModelUrl ?: r.absolutePath,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    decodeSize = decodeSize,
                )
            }

            is CollectionCoverResolver.Resolved.Navidrome -> {
                val url = rememberPreferredCoverArt(
                    coverArtId = r.coverArtId,
                    artist = primaryArtist,
                    album = primaryAlbum,
                    size = decodeSize.value.toInt().coerceAtLeast(100),
                    fallbackService = fallback,
                )
                if (url != null) {
                    CoverArtImage(
                        url = url,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        decodeSize = decodeSize,
                    )
                } else {
                    CollectionLettermark(
                        name = name,
                        modifier = Modifier.fillMaxSize(),
                        fontSize = if (lettermarkLarge) textHeadingL() else textHeadingS(),
                    )
                }
            }

            is CollectionCoverResolver.Resolved.Derived -> {
                val primaryId = r.primary
                val url = rememberPreferredCoverArt(
                    coverArtId = primaryId,
                    artist = primaryArtist,
                    album = primaryAlbum,
                    size = decodeSize.value.toInt().coerceAtLeast(100),
                    fallbackService = fallback,
                )
                if (url != null) {
                    CoverArtImage(
                        url = url,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        decodeSize = decodeSize,
                    )
                } else {
                    CollectionLettermark(
                        name = name,
                        modifier = Modifier.fillMaxSize(),
                        fontSize = if (lettermarkLarge) textHeadingL() else textHeadingS(),
                    )
                }
            }

            is CollectionCoverResolver.Resolved.Bundled -> {
                CoverArtImage(
                    url = catalog.assetUri(r.assetPath),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    decodeSize = decodeSize,
                )
            }

            is CollectionCoverResolver.Resolved.Lettermark -> {
                CollectionLettermark(
                    name = r.name,
                    modifier = Modifier.fillMaxSize(),
                    fontSize = if (lettermarkLarge) textHeadingL() else textHeadingS(),
                )
            }
        }
    }
}

/** Resolve local absolute path without Hilt (Compose/card sites). */
fun collectionLocalAbsolutePath(context: android.content.Context, relative: String?): String? {
    if (relative.isNullOrBlank()) return null
    val root = File(context.applicationContext.filesDir, CollectionCoverStore.DIR)
    val file = File(root, relative)
    return if (file.isFile && file.length() > 0L) file.absolutePath else null
}
