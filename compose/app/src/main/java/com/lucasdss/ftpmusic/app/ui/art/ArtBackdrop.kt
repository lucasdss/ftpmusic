package com.lucasdss.ftpmusic.app.ui.art

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lucasdss.ftpmusic.app.data.cover.ArtPaletteStore
import com.lucasdss.ftpmusic.app.data.cover.ArtSeedExtractor
import com.lucasdss.ftpmusic.app.data.cover.ResolvedCover
import com.lucasdss.ftpmusic.app.data.cover.ResolvedCoverFactory
import com.lucasdss.ftpmusic.app.di.ServerConfigState
import com.lucasdss.ftpmusic.app.ui.Background
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.cancellation.CancellationException

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ArtPaletteStoreEntryPoint {
    fun artPaletteStore(): ArtPaletteStore
}

/**
 * Screen-anchored Spotify-style gradient behind detail content.
 * Stops: 0% bgTop → ~45% bgMid → ~75% Background.
 */
@Composable
fun ArtBackdrop(
    scheme: CoverScheme,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val duration = if (reducedMotion) 0 else 250
    val top by animateColorAsState(scheme.bgTop, tween(duration), label = "artTop")
    val mid by animateColorAsState(scheme.bgMid, tween(duration), label = "artMid")
    val bottom by animateColorAsState(scheme.bgBottom, tween(duration), label = "artBottom")
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to top,
                            0.45f to mid,
                            0.75f to bottom,
                            1.0f to bottom,
                        ),
                    ),
                )
            },
        content = content,
    )
}

/**
 * Resolve [CoverScheme] for a [ResolvedCover] (Coil extract / lettermark / Room).
 */
@Composable
fun rememberCoverScheme(
    cover: ResolvedCover?,
    serverScope: String = "default",
    tileImageModels: List<Any> = emptyList(),
): CoverScheme {
    val context = LocalContext.current
    val store = remember(context) {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                ArtPaletteStoreEntryPoint::class.java,
            ).artPaletteStore()
        }.getOrNull()
    }
    var scheme by remember(cover?.artKey, cover?.sourceVersion, cover?.imageModel) {
        mutableStateOf(initialScheme(cover))
    }

    LaunchedEffect(cover?.artKey, cover?.sourceVersion, cover?.imageModel, tileImageModels) {
        val c = cover
        if (c == null) {
            scheme = CoverScheme.neutral()
            return@LaunchedEffect
        }

        // Instant lettermark seed (no bitmap / no Room required).
        if (c.imageModel == null && c.lettermarkName != null) {
            val argb = ArtSeedExtractor.lettermarkSeedArgb(c.lettermarkName)
            ArtSeedExtractor.putMemory(c.artKey, c.sourceVersion, argb)
            scheme = CoverScheme.fromSeed(Color(argb))
            return@LaunchedEffect
        }

        // Missing art → neutral (wait for imageModel if still resolving).
        if (c.imageModel == null && tileImageModels.isEmpty()) {
            scheme = CoverScheme.neutral()
            return@LaunchedEffect
        }

        ArtSeedExtractor.peekMemory(c.artKey, c.sourceVersion)?.let { hit ->
            if (!hit.softFailure) {
                scheme = CoverScheme.fromSeed(hit.color)
                return@LaunchedEffect
            }
        }

        runCatching {
            store?.get(serverScope, c.artKey, c.sourceVersion)
        }.onSuccess { argb ->
            if (argb != null) {
                ArtSeedExtractor.putMemory(c.artKey, c.sourceVersion, argb)
                scheme = CoverScheme.fromSeed(Color(argb))
                return@LaunchedEffect
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
        }

        val extracted = when {
            tileImageModels.size > 1 -> ArtSeedExtractor.extractMontageAverage(
                context = context,
                tileModels = tileImageModels,
                artKey = c.artKey,
                sourceVersion = c.sourceVersion,
            )
            else -> ArtSeedExtractor.extractFromModel(
                context = context,
                imageModel = c.imageModel ?: tileImageModels.firstOrNull(),
                artKey = c.artKey,
                sourceVersion = c.sourceVersion,
            )
        }
        // Persist only usable seeds — intentional null stays memory-only so Room
        // get(null) cannot be confused with a cache miss (ADR 0157).
        if (!extracted.softFailure && extracted.seedArgb != null) {
            runCatching {
                store?.put(serverScope, c.artKey, c.sourceVersion, extracted.seedArgb)
            }.onFailure { e ->
                if (e is CancellationException) throw e
            }
        }
        scheme = CoverScheme.fromSeed(extracted.color)
    }
    return scheme
}

private fun initialScheme(cover: ResolvedCover?): CoverScheme {
    if (cover == null) return CoverScheme.neutral()
    if (cover.imageModel == null && cover.lettermarkName != null) {
        return CoverScheme.fromSeed(Color(ArtSeedExtractor.lettermarkSeedArgb(cover.lettermarkName)))
    }
    ArtSeedExtractor.peekMemory(cover.artKey, cover.sourceVersion)?.let { hit ->
        if (!hit.softFailure) return CoverScheme.fromSeed(hit.color)
    }
    return CoverScheme.neutral()
}

/** Convenience for album/artist heroes with a single URL. */
@Composable
fun rememberCoverSchemeFromUrl(
    scope: String,
    artId: String?,
    imageUrl: String?,
    serverScope: String? = null,
): CoverScheme {
    val scopeKey = serverScope ?: ServerConfigState.value.url.ifBlank { "default" }
    val cover = remember(scope, artId, imageUrl) {
        ResolvedCoverFactory.fromImageUrl(scope, artId, imageUrl)
    }
    return rememberCoverScheme(cover, scopeKey)
}

/** Hue helper exposed for tests / lettermark parity. */
fun lettermarkCoverScheme(name: String): CoverScheme {
    val argb = ArtSeedExtractor.lettermarkSeedArgb(name)
    return CoverScheme.fromSeed(Color(argb))
}

/** Neutral backdrop color for loading/error shells. */
val ArtBackdropFallback: Color = Background
