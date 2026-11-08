package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverResolver
import com.lucasdss.ftpmusic.app.ui.textHeadingL
import com.lucasdss.ftpmusic.app.ui.textHeadingS

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
    /** Pack-first for GENRE / MIX / PLAYLIST (ADR 0151 amendment). */
    skipDerived: Boolean = true,
    dominantGenre: String? = null,
) {
    val resolvedCover = rememberResolvedCollectionCover(
        name = name,
        fixedCoverKind = fixedCoverKind,
        fixedCoverValue = fixedCoverValue,
        derivedCoverArtIds = derivedCoverArtIds,
        serverCoverArtId = serverCoverArtId,
        primaryArtist = primaryArtist,
        primaryAlbum = primaryAlbum,
        decodeSizePx = decodeSize.value.toInt().coerceAtLeast(100),
        subject = subject,
        stylePackId = stylePackId,
        skipDerived = skipDerived,
        dominantGenre = dominantGenre,
    )

    Box(
        modifier = modifier.background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center,
    ) {
        when (val r = resolvedCover.resolved) {
            is CollectionCoverResolver.Resolved.LocalFile,
            is CollectionCoverResolver.Resolved.Navidrome,
            is CollectionCoverResolver.Resolved.Derived,
            is CollectionCoverResolver.Resolved.Bundled,
            -> {
                val model = resolvedCover.imageModel
                if (model != null) {
                    CoverArtImage(
                        url = model.toString(),
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
    val root = java.io.File(context.applicationContext.filesDir, com.lucasdss.ftpmusic.app.data.cover.CollectionCoverStore.DIR)
    val file = java.io.File(root, relative)
    return if (file.isFile && file.length() > 0L) file.absolutePath else null
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface CoverStylePackEntryPoint {
    fun coverStylePackPreference(): com.lucasdss.ftpmusic.app.data.cover.CoverStylePackPreference
}
