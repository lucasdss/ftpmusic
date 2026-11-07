package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverKind
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverResolver
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverStore
import com.lucasdss.ftpmusic.app.data.cover.DefaultCollectionCoverCatalog
import com.lucasdss.ftpmusic.app.data.cover.ResolvedCover
import com.lucasdss.ftpmusic.app.data.cover.ResolvedCoverFactory
import com.lucasdss.ftpmusic.app.ui.library.rememberPreferredCoverArt
import dagger.hilt.android.EntryPointAccessors
import java.io.File

/**
 * Shared resolution for [CollectionCoverArt] render + art-seed extraction.
 */
@Composable
fun rememberResolvedCollectionCover(
    name: String,
    fixedCoverKind: String?,
    fixedCoverValue: String?,
    derivedCoverArtIds: List<String>,
    serverCoverArtId: String? = null,
    primaryArtist: String? = null,
    primaryAlbum: String? = null,
    decodeSizePx: Int = 200,
    subject: CollectionCoverSubject = CollectionCoverSubject.MIX,
    stylePackId: String? = null,
    skipDerived: Boolean = subject == CollectionCoverSubject.GENRE,
    scope: String = subject.name.lowercase(),
): ResolvedCover {
    val context = LocalContext.current
    val store = remember { CollectionCoverStore(context.applicationContext) }
    val catalog = remember { DefaultCollectionCoverCatalog(context.applicationContext) }
    val packPreference = remember(context) {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                CoverStylePackEntryPoint::class.java,
            ).coverStylePackPreference()
        }.getOrNull()
    }
    val preferredPack = if (packPreference != null) {
        packPreference.packId.collectAsState(initial = packPreference.current()).value
    } else {
        catalog.defaultPackId()
    }
    val knownPackIds = remember(catalog) { catalog.packs().map { it.id }.toSet() }
    val activePack = remember(stylePackId, preferredPack, knownPackIds) {
        val candidate = stylePackId?.takeIf { it.isNotBlank() } ?: preferredPack
        if (candidate in knownPackIds) candidate else catalog.defaultPackId()
    }
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
        activePack,
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
    val coverArtIdForUrl = when (val r = resolved) {
        is CollectionCoverResolver.Resolved.Navidrome -> r.coverArtId
        is CollectionCoverResolver.Resolved.Derived -> r.primary
        else -> null
    }
    val preferredUrl = if (coverArtIdForUrl != null) {
        rememberPreferredCoverArt(
            coverArtId = coverArtIdForUrl,
            artist = primaryArtist,
            album = primaryAlbum,
            size = decodeSizePx.coerceAtLeast(100),
            fallbackService = fallback,
        )
    } else {
        null
    }
    val bundledUri = when (resolved) {
        is CollectionCoverResolver.Resolved.Bundled -> catalog.assetUri(resolved.assetPath)
        else -> null
    }
    return remember(resolved, localModelUrl, preferredUrl, bundledUri, scope) {
        ResolvedCoverFactory.fromResolver(
            resolved = resolved,
            scope = scope,
            localModelUrl = localModelUrl,
            bundledAssetUri = bundledUri,
            navidromeOrDerivedUrl = preferredUrl,
        )
    }
}
