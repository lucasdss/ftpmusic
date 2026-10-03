package com.lucasdss.ftpmusic.app.ui
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.cast.framework.CastContext
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.LyricsCacheEntryPoint
import com.lucasdss.ftpmusic.app.di.MetadataEntryPoint
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import com.lucasdss.ftpmusic.app.di.WaveformDbEntryPoint
import com.lucasdss.ftpmusic.app.playback.PlaybackViewModel
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.album.AlbumDetailScreen
import com.lucasdss.ftpmusic.app.ui.artist.ArtistDetailScreen
import com.lucasdss.ftpmusic.app.ui.components.AppHeader
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.favorites.FavoritesScreen
import com.lucasdss.ftpmusic.app.ui.genre.GenreDetailScreen
import com.lucasdss.ftpmusic.app.ui.library.HomeScreen
import com.lucasdss.ftpmusic.app.ui.library.LibraryContent
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtColors
import com.lucasdss.ftpmusic.app.ui.library.rememberPreferredCoverArt
import com.lucasdss.ftpmusic.app.ui.player.CastButtonState
import com.lucasdss.ftpmusic.app.ui.player.CastDevicePickerDialog
import com.lucasdss.ftpmusic.app.ui.player.PlayerBar
import com.lucasdss.ftpmusic.app.ui.player.PlayerBarColors
import com.lucasdss.ftpmusic.app.ui.player.PlayerBarState
import com.lucasdss.ftpmusic.app.ui.search.SearchScreen
import com.lucasdss.ftpmusic.app.ui.server.ServerConnectScreen
import com.lucasdss.ftpmusic.app.ui.settings.SettingsScreen
import com.lucasdss.ftpmusic.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resolves the initial route after splash based on credentials and sync state.
 * Extracted for testability — unit tests verify each decision branch.
 */
object SplashRouter {
    fun resolveRoute(hasCredentials: Boolean, albumCount: Int): String = when {
        !hasCredentials -> "connect"
        albumCount == 0 -> syncingRoute("home")
        else -> "home"
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Suppress("ThrowsCount") // CancellationException rethrows in nested LaunchedEffects
@Composable
fun FtpmusicNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d("ftpmusic-ui", "route=$route")
    }

    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    // Activity-scoped so SettingsScreen toggle updates the bar live.
    val settingsViewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    )
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val hideNavLabels = settingsState.hideNavLabels
    // P1: strip the 200 ms position tick out of the metadata state so a tick
    // never recomposes this whole scope (every screen, bottom nav, player).
    // The flow transformation lives in the ViewModel (stateWithoutPosition);
    // position is collected separately inside the mini/full player subtrees.
    val playbackState by playbackViewModel.stateWithoutPosition
        .collectAsStateWithLifecycle(initialValue = com.lucasdss.ftpmusic.app.playback.PlaybackState())

    // Fallback cover art service — races iTunes + MusicBrainz when Navidrome has no art
    val appContext = LocalContext.current
    val coverArtFallback = remember { CoverArtFallbackService.getInstance(appContext) }

    // Auto-heal: if icon is blue but no device name, verify CastPlayer state.
    // With CastPlayer, onDeviceInfoChanged in MediaService drives authoritative state.
    LaunchedEffect(playbackState.isCasting, playbackState.castDeviceName) {
        if (playbackState.isCasting && playbackState.castDeviceName == null) {
            // Stale state — CastPlayer would have set deviceName via onDeviceInfoChanged.
            // If not, the session likely died without us noticing.
            android.util.Log.w("ftpmusic-cast", "[NavHost] isCasting=true but no deviceName — force-resetting")
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
                "ftpmusic-cast",
                "stale casting heal — force reset",
            )
            playbackViewModel.onCastDisconnected()
        }
    }

    // Refresh queue download status when queue size changes.
    // Continuous Play (journal → context) lives in MediaService — not here.
    // Surprise Me is one-shot only and must never auto-mutate the queue.
    LaunchedEffect(playbackState.queueSize) {
        playbackViewModel.refreshQueueDownloadStatus()
    }

    // Recover Cast session after process restart — CastPlayer in MediaService
    // detects existing sessions and fires onDeviceInfoChanged automatically.
    // This block is a safety net for edge cases where the UI starts before the service.
    LaunchedEffect(Unit) {
        try {
            delay(2_000L)
        } catch (_: Exception) {}
        if (!playbackState.isCasting) {
            // CastPlayer didn't auto-recover — no stale session to worry about
            android.util.Log.d("ftpmusic-cast", "[NavHost] No stale Cast session detected")
        }
    }

    // Resolve album cover art as fallback when track has no coverArt
    var albumCoverArtId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playbackState.albumId) {
        val albumId = playbackState.albumId
        if (albumId != null) {
            val metadataEntry = dagger.hilt.android.EntryPointAccessors.fromApplication(
                appContext,
                MetadataEntryPoint::class.java,
            )
            val album = withContext(Dispatchers.IO) {
                metadataEntry.cachedMetadataDao().getAlbumCoverArt(albumId)
            }
            albumCoverArtId = album
        } else {
            albumCoverArtId = null
        }
    }

    val effectiveCoverArtId = albumCoverArtId ?: playbackState.coverArtId
    val miniCoverUrl = rememberPreferredCoverArt(
        coverArtId = effectiveCoverArtId,
        artist = playbackState.artist,
        album = playbackState.album,
        size = 300,
        fallbackService = coverArtFallback,
    )
    val nowPlayingCoverUrl = rememberPreferredCoverArt(
        coverArtId = effectiveCoverArtId,
        artist = playbackState.artist,
        album = playbackState.album,
        size = 600,
        fallbackService = coverArtFallback,
    )
    // Background: cache Navidrome art to disk for offline/restart availability
    LaunchedEffect(effectiveCoverArtId, nowPlayingCoverUrl) {
        val artId = effectiveCoverArtId
        val url = nowPlayingCoverUrl
        if (artId != null && url != null && url.startsWith("http")) {
            coverArtFallback.cacheNavidromeArt(artId, url)
        }
    }
    val miniColors = rememberCoverArtColors(miniCoverUrl)
    val nowPlayingColors = rememberCoverArtColors(nowPlayingCoverUrl)
    val miniPlayerColors = PlayerBarColors(
        darkMuted = if (miniColors.hasColors) miniColors.darkMuted else null,
        vibrant = if (miniColors.hasColors) miniColors.vibrant else null,
    )
    val nowPlayingPlayerColors = PlayerBarColors(
        darkMuted = if (nowPlayingColors.hasColors) nowPlayingColors.darkMuted else null,
        vibrant = if (nowPlayingColors.hasColors) nowPlayingColors.vibrant else null,
    )

    val tabs = BottomNavItems
    // ADR-0062: selection ownership independent of flat detail routes (mix/, album/, …).
    var activeBottomTab by rememberSaveable { mutableStateOf("home") }
    val showBottomBar = currentRoute != "connect" // hide on login screen
    val isFullPlayer = currentRoute == "nowplaying"
    val isLoginScreen = currentRoute == "connect"

    // Persistent Cast discovery — callback stays alive across dialog open/close
    val context = navController.context

    DisposableEffect(Unit) {
        val router = androidx.mediarouter.media.MediaRouter.getInstance(context)
        val callback = object : androidx.mediarouter.media.MediaRouter.Callback() {
            override fun onRouteAdded(
                router: androidx.mediarouter.media.MediaRouter,
                route: androidx.mediarouter.media.MediaRouter.RouteInfo,
            ) {
                android.util.Log.d("ftpmusic-cast", "[NavHost] onRouteAdded: '${route.name}' id=${route.id.take(40)}")
                refreshCastRoutes(router) { d -> CastButtonState.discoveredDevices.value = d }
            }
            override fun onRouteRemoved(
                router: androidx.mediarouter.media.MediaRouter,
                route: androidx.mediarouter.media.MediaRouter.RouteInfo,
            ) {
                android.util.Log.d("ftpmusic-cast", "[NavHost] onRouteRemoved: '${route.name}'")
                refreshCastRoutes(router) { d -> CastButtonState.discoveredDevices.value = d }
            }
        }
        router.addCallback(
            androidx.mediarouter.media.MediaRouteSelector.Builder()
                .addControlCategory(androidx.mediarouter.media.MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .addControlCategory("com.google.android.gms.cast.CATEGORY_CAST")
                .build(),
            callback,
            androidx.mediarouter.media.MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or
                androidx.mediarouter.media.MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or
                androidx.mediarouter.media.MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS,
        )
        refreshCastRoutes(router) { d -> CastButtonState.discoveredDevices.value = d }

        // Poll routes every 2s for the first 30s — catches Cast SDK provider registration
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var pollCount = 0
        val pollRunnable = object : Runnable {
            override fun run() {
                if (pollCount >= 15) return // 15 polls × 2s = 30s max
                pollCount++
                android.util.Log.d("ftpmusic-cast", "[NavHost] poll #$pollCount: ${router.routes.size} routes")
                refreshCastRoutes(router) { d -> CastButtonState.discoveredDevices.value = d }
                handler.postDelayed(this, 2000L)
            }
        }
        handler.postDelayed(pollRunnable, 2000L)

        onDispose {
            handler.removeCallbacks(pollRunnable)
            router.removeCallback(callback)
        }
    }

    Scaffold(
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                // Unified player bar — adapts to isCasting automatically
                if (!isFullPlayer && !isLoginScreen) {
                    // P1: collect the position tick HERE (bottomBar scope), not
                    // in the NavHost scope — only this subtree recomposes 5 Hz.
                    val position by playbackViewModel.positionMs.collectAsStateWithLifecycle(0L)
                    // P3: stable callbacks so the mini player tree skips when
                    // only the position tick changed.
                    val miniOnPlayPause = remember(playbackViewModel) { { playbackViewModel.playPause() } }
                    val miniOnSkipPrev = remember(playbackViewModel) { { playbackViewModel.skipPrev() } }
                    val miniOnSkipNext = remember(playbackViewModel) { { playbackViewModel.skipNext() } }
                    val miniOnSeek =
                        remember(playbackViewModel) { { fraction: Float -> playbackViewModel.seekTo(fraction) } }
                    val miniOnClick = remember(navController) { { navController.navigate("nowplaying") } }
                    PlayerBar(
                        state = PlayerBarState(
                            title = playbackState.title,
                            artist = playbackState.artist,
                            isPlaying = playbackState.isPlaying,
                            coverArtUrl = miniCoverUrl,
                            isCasting = playbackState.isCasting,
                            castDeviceName = playbackState.castDeviceName,
                            colors = miniPlayerColors,
                        ),
                        position = position,
                        duration = playbackState.duration,
                        onPlayPause = miniOnPlayPause,
                        onSkipPrev = miniOnSkipPrev,
                        onSkipNext = miniOnSkipNext,
                        onSeek = miniOnSeek,
                        onClick = miniOnClick,
                        modifier = Modifier,
                    )
                }
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = Background,
                        tonalElevation = 0.dp,
                        modifier = Modifier.drawBehind {
                            drawLine(
                                color = Color.White.copy(alpha = 0.06f),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx(),
                            )
                        },
                    ) {
                        tabs.forEach { tab ->
                            // ADR-0062: highlight from activeBottomTab so Home stays
                            // selected on mix/album/… drill-downs (flat graph).
                            val selected = TabNavigationPolicy.isTabSelected(activeBottomTab, tab.route)
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (tab.route == "favorites" && selected) {
                                            Icons.Filled.Favorite
                                        } else {
                                            tab.selectedIcon
                                        },
                                        contentDescription = navTabIconContentDescription(
                                            hideNavLabels,
                                            tab.label,
                                        ),
                                        modifier = Modifier.size(adp(22f)),
                                    )
                                },
                                label = if (hideNavLabels) {
                                    null
                                } else {
                                    {
                                        FittingText(
                                            text = tab.label,
                                            fontSize = textLabelM(),
                                            minFontSize = textMicro(),
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            fillMaxWidth = true,
                                            // M3 centers the icon; Start-align in a full-width
                                            // label slot left-skews glyphs under the icon.
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                },
                                selected = selected,
                                onClick = {
                                    val popToRoot = TabNavigationPolicy.shouldPopToTabRoot(
                                        activeBottomTab = activeBottomTab,
                                        clickedTab = tab.route,
                                        currentRoute = currentRoute,
                                    )
                                    if (popToRoot) {
                                        // Try pattern route first; fall back if absent (ADR-0062).
                                        for (route in TabNavigationPolicy.tabRootPopFallbackRoutes(tab.route)) {
                                            if (navController.popBackStack(route, inclusive = false)) break
                                        }
                                        return@NavigationBarItem
                                    }
                                    if (selected &&
                                        currentRoute != null &&
                                        TabNavigationPolicy.isAtTabRoot(tab.route, currentRoute)
                                    ) {
                                        // Already at tab root — scroll/refresh stubs (ADR-0062).
                                        when (tab.route) {
                                            "home" -> { /* scroll-to-top handled by HomeScreen */ }
                                            "favorites" -> { /* refresh handled by FavoritesScreen */ }
                                            "search" -> { /* clear handled by SearchScreen */ }
                                        }
                                        return@NavigationBarItem
                                    }
                                    activeBottomTab = TabNavigationPolicy.resolveActiveTabAfterClick(
                                        activeBottomTab,
                                        tab.route,
                                    )
                                    navController.navigate(tab.route) {
                                        // Replace the current tab instead of pushing: pop up to the
                                        // real top-level root ("home") — the graph's start destination
                                        // ("splash") is removed after startup, so popping to it was a
                                        // no-op that accumulated duplicated tab history. save/restore
                                        // state preserves each tab's scroll + drill-down position.
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BrandTeal,
                                    selectedTextColor = BrandTeal,
                                    unselectedIconColor = NavUnselected,
                                    unselectedTextColor = NavUnselected,
                                    indicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            val route = navBackStackEntry?.destination?.route
            // AppHeader on primary tabs only — detail routes use their own back chrome
            // (avoids stacked logo + TopAppBar). See ADR-0054.
            val showHeader = showAppHeaderForRoute(route)

            if (showHeader) {
                AppHeader(
                    settingsSelected = currentRoute?.startsWith(SETTINGS_ROUTE) == true,
                    onSettingsClick = {
                        navController.navigateToSettings()
                    },
                )
            }

            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("splash") {
                    val context = LocalContext.current
                    val nav = navController
                    LaunchedEffect(Unit) {
                        // Self-heal hook: re-read storage (force) so a transient
                        // failure during Application.onCreate does not leave the
                        // session without a server config.
                        val configStore = com.lucasdss.ftpmusic.app.di.serverConfigStore(context)
                        val config = withContext(Dispatchers.IO) {
                            configStore.initialize(force = true)
                        }
                        val hasCredentials = config.isConfigured
                        if (com.lucasdss.ftpmusic.app.BuildConfig.IMAGE_DIAGNOSTICS) {
                            android.util.Log.w(
                                "ftpmusic-images",
                                "[diag] splash restore url=${config.url.isNotBlank()} " +
                                    "user=${config.username.isNotBlank()} hasCreds=$hasCredentials " +
                                    "t=${System.currentTimeMillis()}",
                            )
                        }
                        val metadataDao = dagger.hilt.android.EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            MetadataEntryPoint::class.java,
                        ).cachedMetadataDao()
                        val albumCount = withContext(Dispatchers.IO) { metadataDao.albumCount() }
                        val targetRoute = SplashRouter.resolveRoute(hasCredentials, albumCount)
                        nav.navigate(targetRoute) { popUpTo("splash") { inclusive = true } }
                    }
                    Box(
                        Modifier.fillMaxSize().background(Background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(com.lucasdss.ftpmusic.app.R.drawable.play_store_icon_512),
                                contentDescription = "FTP Music",
                                modifier = Modifier.size(120.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            CircularProgressIndicator(color = BrandTeal, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                composable("connect") {
                    val connectVm: com.lucasdss.ftpmusic.app.ui.library.LibraryViewModel = hiltViewModel()
                    ServerConnectScreen(onConnected = {
                        // First login — show sync progress then navigate home
                        navController.navigate(syncingRoute("home")) {
                            popUpTo("connect") { inclusive = true }
                        }
                    })
                }
                composable(
                    SYNCING_ROUTE_PATTERN,
                    arguments = listOf(navArgument("returnTo") { defaultValue = "home" }),
                ) { backStackEntry ->
                    val returnTo = backStackEntry.arguments?.getString("returnTo") ?: "home"
                    com.lucasdss.ftpmusic.app.ui.library.SyncingScreen(
                        userTriggered = returnTo != "home",
                        rebuildOnly = false,
                        onComplete = {
                            navController.navigate(returnTo) {
                                popUpTo(SYNCING_ROUTE_PATTERN) { inclusive = true }
                            }
                        },
                    )
                }
                composable("rebuildmix") {
                    com.lucasdss.ftpmusic.app.ui.library.SyncingScreen(
                        userTriggered = true,
                        rebuildOnly = true,
                        onComplete = {
                            navController.popBackStack()
                        },
                    )
                }
                composable("home") {
                    HomeScreen(
                        onAlbumClick = { navController.navigate("album/$it") },
                        onGenreClick = { genre ->
                            navController.navigate("genre/$genre")
                        },
                        onMixClick = { mixId ->
                            navController.navigate("mix/$mixId")
                        },
                        onPlaylistsClick = {
                            TabNavigationPolicy.resolveActiveTabForDestination("library?tab=playlists")
                                ?.let { activeBottomTab = it }
                            navController.navigate("library?tab=playlists") { launchSingleTop = true }
                        },
                        onPlaylistClick = { playlistId ->
                            navController.navigate("playlist/$playlistId")
                        },
                        onArtistClick = { artistId ->
                            navController.navigate("artist/$artistId")
                        },
                        onRadioStationClick = { station ->
                            playbackViewModel.playStream(station.streamUrl, station.name)
                        },
                        currentTrackId = playbackState.currentTrackId,
                        currentAlbumId = playbackState.albumId,
                        isPlaying = playbackState.isPlaying,
                        onOpenServerSettings = {
                            navController.navigateToSettings()
                        },
                    )
                }
                composable("genre/{genre}") { backStackEntry ->
                    val genre = backStackEntry.arguments?.getString("genre") ?: ""
                    GenreDetailScreen(
                        genre = genre,
                        onAlbumClick = { navController.navigate("album/$it") },
                        onArtistClick = { navController.navigate("artist/$it") },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("mix/{mixId}") { backStackEntry ->
                    val mixId = backStackEntry.arguments?.getString("mixId")?.toLongOrNull() ?: 0L
                    val vm: com.lucasdss.ftpmusic.app.ui.library.MixDetailViewModel = hiltViewModel()
                    val mixName by vm.mixName.collectAsStateWithLifecycle()
                    com.lucasdss.ftpmusic.app.ui.library.MixDetailScreen(
                        mixId = mixId,
                        mixName = mixName,
                        onBack = { navController.popBackStack() },
                        onRefresh = { vm.refreshMix(mixId) },
                        currentTrackId = playbackState.currentTrackId,
                        isPlaying = playbackState.isPlaying,
                    )
                }
                composable(
                    "library?tab={tab}",
                    arguments = listOf(navArgument("tab") { defaultValue = "albums" }),
                ) { backStackEntry ->
                    val tab = backStackEntry.arguments?.getString("tab") ?: "albums"
                    LibraryContent(
                        initialTab = tab,
                        onArtistClick = { navController.navigate("artist/$it") },
                        onAlbumClick = { navController.navigate("album/$it") },
                        onPlaylistClick = { navController.navigate("playlist/$it") },
                        onRadioStationClick = { station ->
                            playbackViewModel.playStream(station.streamUrl, station.name)
                        },
                        currentAlbumId = playbackState.albumId,
                        isPlaying = playbackState.isPlaying,
                        onOpenServerSettings = {
                            navController.navigateToSettings()
                        },
                    )
                }
                composable("favorites") {
                    FavoritesScreen(
                        currentTrackId = playbackState.currentTrackId,
                        currentAlbumId = playbackState.albumId,
                        isPlaying = playbackState.isPlaying,
                        onAlbumClick = { album -> navController.navigate("album/${album.id}") },
                        onArtistClick = { artist -> navController.navigate("artist/${artist.id}") },
                        onRadioStationClick = { station ->
                            playbackViewModel.playStream(station.streamUrl, station.name)
                        },
                    )
                }
                composable("search") {
                    SearchScreen(
                        initialQuery = "",
                        onArtistClick = { navController.navigate("artist/$it") },
                        onAlbumClick = { navController.navigate("album/$it") },
                        onGenreClick = { navController.navigate("genre/$it") },
                        onTrackClick = { track ->
                            val streamUrl = playbackViewModel.buildStreamUrl(track.id)
                            playbackViewModel.playSingleTrack(track, streamUrl)
                        },
                        onPlaylistClick = { navController.navigate("playlist/$it") },
                    )
                }
                composable("search/{query}") { backStackEntry ->
                    val query = backStackEntry.arguments?.getString("query") ?: ""
                    SearchScreen(
                        initialQuery = query,
                        onArtistClick = { navController.navigate("artist/$it") },
                        onAlbumClick = { navController.navigate("album/$it") },
                        onGenreClick = { navController.navigate("genre/$it") },
                        onTrackClick = { track ->
                            val streamUrl = playbackViewModel.buildStreamUrl(track.id)
                            playbackViewModel.playSingleTrack(track, streamUrl)
                        },
                        onPlaylistClick = { navController.navigate("playlist/$it") },
                    )
                }
                composable("album/{albumId}") { backStackEntry ->
                    AlbumDetailScreen(
                        albumId = backStackEntry.arguments?.getString("albumId") ?: "",
                        onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") },
                        onNavigateToArtist = { artistId -> navController.navigate("artist/$artistId") },
                        onBack = { navController.popBackStack() },
                        currentTrackId = playbackState.currentTrackId,
                        isPlaying = playbackState.isPlaying,
                    )
                }
                composable("playlist/{playlistId}") { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                    com.lucasdss.ftpmusic.app.ui.playlist.PlaylistDetailScreen(
                        playlistId = playlistId,
                        onBack = { navController.popBackStack() },
                        onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") },
                        onNavigateToArtist = { artistId -> navController.navigate("artist/$artistId") },
                        currentTrackId = playbackState.currentTrackId,
                        isPlaying = playbackState.isPlaying,
                    )
                }
                composable("artist/{artistId}") { backStackEntry ->
                    val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
                    ArtistDetailScreen(
                        artistId = artistId,
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                        onArtistClick = { id -> navController.navigate("artist/$id") },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onResyncLibrary = {
                            if (navController.currentBackStackEntry?.destination?.route?.startsWith("syncing") ==
                                true
                            ) {
                                return@SettingsScreen
                            }
                            navController.navigate(syncingRoute("settings"))
                        },
                        onRebuildMixes = { navController.navigate("rebuildmix") },
                        onCustomMixes = { navController.navigate("customMixes") },
                        onProfile = { navController.navigate("profile") },
                        onServerSettingsSaved = { /* saved, nothing to do */ },
                    )
                }
                composable("profile") {
                    com.lucasdss.ftpmusic.app.ui.settings.ProfileScreen(
                        onBack = { navController.popBackStack() },
                        onTrackClick = { track ->
                            val trackModel = com.lucasdss.ftpmusic.app.data.model.Track(
                                id = track.id,
                                title = track.title,
                                artist = track.artist,
                                album = null,
                                duration = track.durationSeconds,
                                coverArt = track.coverArtUrl,
                                suffix = track.suffix,
                                contentType = track.contentType,
                            )
                            val streamUrl = playbackViewModel.buildStreamUrl(track.id)
                            playbackViewModel.playSingleTrack(trackModel, streamUrl)
                            navController.navigate("nowplaying")
                        },
                    )
                }
                composable("customMixes") {
                    com.lucasdss.ftpmusic.app.ui.settings.CustomDailyMixesScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("nowplaying") {
                    // P1: position tick collected in THIS scope — the nowplaying
                    // subtree recomposes at 5 Hz, but NavHost and every other
                    // screen stay untouched.
                    val position by playbackViewModel.positionMs.collectAsStateWithLifecycle(0L)
                    var lyricsText by remember { mutableStateOf<String?>(null) }
                    var lyricLines by remember {
                        mutableStateOf<List<com.lucasdss.ftpmusic.app.ui.player.LyricLine>>(emptyList())
                    }
                    var lyricsLoading by remember { mutableStateOf(true) }
                    val appContext = LocalContext.current.applicationContext
                    var waveformBars by remember { mutableStateOf<List<Float>>(emptyList()) }
                    var showSleepTimerDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(playbackState.currentTrackId) {
                        val tid = playbackState.currentTrackId
                        if (tid == null) {
                            // Playback stopped — don't keep the previous track's waveform.
                            waveformBars = emptyList()
                            return@LaunchedEffect
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val entry = dagger.hilt.android.EntryPointAccessors.fromApplication(
                                    appContext,
                                    WaveformDbEntryPoint::class.java,
                                )
                                val bars = entry.waveformRepository().getOrGenerate(tid)
                                // Fast track skips cancel this effect; only apply if still current.
                                if (playbackState.currentTrackId == tid) {
                                    waveformBars = bars
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w(
                                    "ftpmusic-waveform",
                                    "Failed to load waveform for $tid: ${e.message}",
                                )
                                waveformBars = emptyList()
                            }
                        }
                    }

                    LaunchedEffect(playbackState.artist, playbackState.title, playbackState.currentTrackId) {
                        val artist = playbackState.artist
                        val title = playbackState.title
                        val trackId = playbackState.currentTrackId
                        if (artist.isNullOrEmpty() || title.isNullOrEmpty()) {
                            lyricsText = null
                            lyricLines = emptyList()
                            lyricsLoading = false
                            return@LaunchedEffect
                        }
                        lyricsLoading = true
                        lyricLines = emptyList()
                        lyricsText = null
                        val lyricsEntry = dagger.hilt.android.EntryPointAccessors.fromApplication(
                            appContext,
                            LyricsCacheEntryPoint::class.java,
                        )
                        val allowNetwork = com.lucasdss.ftpmusic.app.ui.player.LyricsFetcher
                            .shouldFetchLyricsOverNetwork(isOffline = playbackState.isOffline)
                        try {
                            val resolve = withContext(Dispatchers.IO) {
                                val dao = lyricsEntry.lyricsCacheDao()
                                trackId?.let {
                                    com.lucasdss.ftpmusic.app.ui.player.LyricsFetcher.resolveFromCache(it, dao)
                                }
                            }
                            if (resolve != null) {
                                lyricLines = resolve.display.lines
                                lyricsText = resolve.display.text
                                lyricsLoading = false
                                if (allowNetwork && resolve.needsBackgroundRefresh && trackId != null) {
                                    // Child of this effect — cancelled on track change (no detached scope).
                                    launch(Dispatchers.IO) {
                                        try {
                                            refreshLyricsInBackground(
                                                artist,
                                                title,
                                                trackId,
                                                lyricsEntry,
                                                appContext,
                                            )
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            // Silent — stale cache still shown
                                        }
                                    }
                                }
                                return@LaunchedEffect
                            }
                            if (!allowNetwork) {
                                lyricsLoading = false
                                return@LaunchedEffect
                            }
                            val display = withContext(Dispatchers.IO) {
                                fetchLyricsForTrack(artist, title, trackId, lyricsEntry, appContext)
                            }
                            lyricLines = display.lines
                            lyricsText = display.text
                            lyricsLoading = false
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("ftpmusic-lyrics", "Failed to fetch lyrics: ${e.message}")
                            lyricsText = null
                            lyricLines = emptyList()
                            lyricsLoading = false
                        }
                    }

                    // P3: stable callback identities. Fresh lambdas per 200 ms tick
                    // make every PlayerBar sub-composable non-skippable — the whole
                    // 1600-line player body re-executed at 5 Hz. Remembering them
                    // (keys = stable dependencies) keeps the tick inside the seek
                    // region only. State-read callbacks read vm.state.value live.
                    val onPlayPause = remember(playbackViewModel) { { playbackViewModel.playPause() } }
                    val onSkipPrev = remember(playbackViewModel) { { playbackViewModel.skipPrev() } }
                    val onSkipNext = remember(playbackViewModel) { { playbackViewModel.skipNext() } }
                    val onSeek =
                        remember(playbackViewModel) { { fraction: Float -> playbackViewModel.seekTo(fraction) } }
                    val onVolumeChange = remember(playbackViewModel) { { v: Float -> playbackViewModel.setVolume(v) } }
                    val onRepeatToggle = remember(playbackViewModel) { { playbackViewModel.toggleRepeat() } }
                    val onShuffleToggle = remember(playbackViewModel) { { playbackViewModel.toggleShuffle() } }
                    val onToggleLike = remember(playbackViewModel) { { playbackViewModel.toggleLike() } }
                    val onToggleDislike = remember(playbackViewModel) { { playbackViewModel.toggleDislike() } }
                    val onRate = remember(playbackViewModel) { { r: Int -> playbackViewModel.rateCurrent(r) } }
                    val onArtistClick = remember(playbackViewModel, navController) {
                        {
                            val aid = playbackViewModel.state.value.artistId
                            if (aid !=
                                null
                            ) {
                                navController.navigate("artist/$aid")
                            }
                        }
                    }
                    val onAlbumClick = remember(playbackViewModel, navController) {
                        {
                            val aid = playbackViewModel.state.value.albumId
                            if (aid !=
                                null
                            ) {
                                navController.navigate("album/$aid")
                            }
                        }
                    }
                    val onBack = remember(navController) {
                        {
                            navController.popBackStack()
                            Unit
                        }
                    }
                    val onClearQueue = remember(playbackViewModel) { { playbackViewModel.clearPriorityQueue() } }
                    val onRemoveFromQueue =
                        remember(playbackViewModel) { { index: Int -> playbackViewModel.removeFromQueue(index) } }
                    val onPlayQueueItem =
                        remember(playbackViewModel) { { index: Int -> playbackViewModel.playQueueItem(index) } }
                    val onSleepTimerClick = remember { { showSleepTimerDialog = true } }
                    val onCast = remember { { CastButtonState.showDialog.value = true } }
                    val onShareQueue =
                        remember(playbackViewModel, appContext) { { playbackViewModel.shareQueue(appContext) } }

                    PlayerBar(
                        state = PlayerBarState(
                            title = playbackState.title,
                            artist = playbackState.artist,
                            album = playbackState.album,
                            isPlaying = playbackState.isPlaying,
                            coverArtUrl = nowPlayingCoverUrl,
                            coverArtId = effectiveCoverArtId,
                            isCasting = playbackState.isCasting,
                            castDeviceName = playbackState.castDeviceName,
                            volume = playbackState.volume,
                            repeatMode = playbackState.repeatMode,
                            shuffleModeEnabled = playbackState.shuffleModeEnabled,
                            playbackSpeed = playbackState.playbackSpeed,
                            sleepTimerEndMs = playbackState.sleepTimerEndMs,
                            isStarred = playbackState.isStarred,
                            isDisliked = playbackState.isDisliked,
                            trackRating = playbackState.trackRating,
                            nextTrackTitle = playbackState.nextTrackTitle,
                            nextTrackArtist = playbackState.nextTrackArtist,
                            trackIndex = playbackState.trackIndex,
                            queueSize = playbackState.queueSize,
                            isOffline = playbackState.isOffline,
                            isQueueSynced = playbackState.isQueueSynced,
                            nextTracks = remember(
                                playbackState.queueSize,
                                playbackState.trackIndex,
                                playbackState.currentTrackId,
                            ) {
                                playbackViewModel.getUpcomingTracks(maxOf(50, playbackState.queueSize))
                            },
                            downloadedTrackIds = playbackState.downloadedTrackIds,
                            colors = nowPlayingPlayerColors,
                            expanded = true,
                            trackId = playbackState.currentTrackId,
                            lyricsText = lyricsText,
                            lyricLines = lyricLines,
                            lyricsLoading = lyricsLoading,
                            contextSource = playbackState.contextSource,
                            priorityQueueSize = playbackState.priorityQueueSize,
                            waveformBars = waveformBars,
                        ),
                        position = position,
                        duration = playbackState.duration,
                        onPlayPause = onPlayPause,
                        onSkipPrev = onSkipPrev,
                        onSkipNext = onSkipNext,
                        onSeek = onSeek,
                        onVolumeChange = onVolumeChange,
                        onRepeatToggle = onRepeatToggle,
                        onShuffleToggle = onShuffleToggle,
                        onToggleLike = onToggleLike,
                        onToggleDislike = onToggleDislike,
                        onRate = onRate,
                        onArtistClick = onArtistClick,
                        onAlbumClick = onAlbumClick,
                        onBack = onBack,
                        onClick = {},
                        onClearQueue = onClearQueue,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onPlayQueueItem = onPlayQueueItem,
                        onSleepTimer = onSleepTimerClick,
                        onAddToPlaylist = { /* placeholder - coming soon */ },
                        onSongInfo = { /* placeholder - coming soon */ },
                        onEqualizer = { /* placeholder - coming soon */ },
                        onCast = onCast,
                        onShuffleQueue = onShuffleToggle,
                        onShareQueue = onShareQueue,
                        modifier = Modifier,
                    )

                    if (showSleepTimerDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showSleepTimerDialog = false },
                            title = { Text("Sleep timer") },
                            text = {
                                Column {
                                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                                        TextButton(
                                            onClick = {
                                                playbackViewModel.startSleepTimer(minutes)
                                                showSleepTimerDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text("$minutes minutes") }
                                    }
                                    if (playbackState.sleepTimerEndMs > 0L) {
                                        TextButton(
                                            onClick = {
                                                playbackViewModel.cancelSleepTimer()
                                                showSleepTimerDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text("Cancel timer", color = Color(0xFFE84040)) }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showSleepTimerDialog = false }) { Text("Close") }
                            },
                        )
                    }
                }
            }
        }
    }

    if (CastButtonState.showDialog.value) {
        // Force-refresh routes on dialog open (in addition to push-based callback)
        LaunchedEffect(CastButtonState.showDialog.value) {
            val router = androidx.mediarouter.media.MediaRouter.getInstance(context)
            refreshCastRoutes(router) { d -> CastButtonState.discoveredDevices.value = d }
        }
        // L9: hoist the state reads once — six reads of the same singleton state
        // in one scope, plus a SDK session lookup, previously ran on EVERY
        // dialog-block recomposition.
        val dialogIsCasting = CastButtonState.isCasting.value
        val dialogConnectedDeviceName = CastButtonState.connectedDeviceName.value
        val dialogConnectingDeviceName = CastButtonState.connectingDeviceName.value
        val dialogDiscoveredDevices = CastButtonState.discoveredDevices.value
        val sessionDevice = if (dialogIsCasting) {
            try {
                CastContext.getSharedInstance(context).sessionManager.currentCastSession?.castDevice
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        val dialogDevices =
            remember(dialogIsCasting, dialogConnectedDeviceName, dialogDiscoveredDevices, dialogConnectingDeviceName) {
                buildList {
                    // Connected device from Cast SDK (authoritative source)
                    if (dialogIsCasting && sessionDevice != null) add(sessionDevice)
                    // Discovered devices, deduplicated
                    addAll(
                        dialogDiscoveredDevices.filter { d ->
                            d.friendlyName != dialogConnectedDeviceName
                        },
                    )
                }
            }
        CastDevicePickerDialog(
            devices = dialogDevices,
            isCasting = dialogIsCasting,
            connectedDeviceName = dialogConnectedDeviceName,
            connectingDeviceName = dialogConnectingDeviceName,
            onDismiss = {
                // Cancel any pending connect (timeout + connecting state) — the
                // reconnect-hang fix: Cancel must always un-stick the picker.
                CastButtonState.onConnectCancelled?.invoke()
                CastButtonState.showDialog.value = false
            },
            onDeviceSelected = { device ->
                try {
                    com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                        "ftpmusic-ui",
                        "cast connect requested deviceId=${device.deviceId}",
                    )
                    // Connect flow is owned by MediaService (registered via
                    // CastButtonState.onConnectRequested): it ends any stale/
                    // desynced session BEFORE route.select() so the select is
                    // never a no-op, and arms a timeout so "Connecting…" can
                    // never stick. Fall back to the legacy inline select only
                    // when the service hasn't registered yet.
                    val connect = CastButtonState.onConnectRequested
                    if (connect != null) {
                        connect(device)
                    } else {
                        // Legacy fallback (service not yet bound): direct select.
                        if (CastButtonState.isCasting.value) {
                            android.util.Log.d(
                                "ftpmusic-cast",
                                "[NavHost] Switching device: ending current session before connecting to ${device.friendlyName}",
                            )
                            CastButtonState.endSessionForSwitch()
                        }
                        val router = androidx.mediarouter.media.MediaRouter.getInstance(context)
                        var found = false
                        for (route in router.routes) {
                            val castDevice = try {
                                com.google.android.gms.cast.CastDevice.getFromBundle(
                                    route.extras ?: android.os.Bundle(),
                                )
                            } catch (e: Exception) {
                                android.util.Log.w(
                                    "ftpmusic-cast",
                                    "[NavHost] getFromBundle failed for '${route.name}': ${e.message}",
                                )
                                null
                            }
                            if (castDevice?.deviceId == device.deviceId) {
                                android.util.Log.d(
                                    "ftpmusic-cast",
                                    "[NavHost] Selected device: ${device.friendlyName} — calling route.select() on '${route.name}'",
                                )
                                route.select()
                                CastButtonState.connectingDeviceName.value = device.friendlyName
                                found = true
                                // Don't dismiss — wait for onSessionStarted/onSessionStartFailed
                                break
                            }
                        }
                        if (!found) {
                            android.util.Log.e(
                                "ftpmusic-cast",
                                "[NavHost] Device not found in routes: ${device.friendlyName} (${device.deviceId})",
                            )
                            CastButtonState.castErrorMessage.value =
                                "Device \"${device.friendlyName}\" is no longer available"
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ftpmusic-cast", "[NavHost] Connection error: ${e.message}", e)
                    CastButtonState.castErrorMessage.value = "Cast connection failed: ${e.message}"
                    CastButtonState.connectingDeviceName.value = null
                }
            },
            onDisconnect = {
                // CastButtonState.disconnect() fires onDisconnectRequested callback,
                // which MediaService registered to update state + notification.
                // CastPlayer then fires onDeviceInfoChanged(remote=false) naturally.
                com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                    "ftpmusic-ui",
                    "cast disconnect requested",
                )
                CastButtonState.disconnect()
            },
        )
    }

    // Cast error dialog
    val castError = CastButtonState.castErrorMessage.value
    if (castError != null) {
        AlertDialog(
            onDismissRequest = { CastButtonState.castErrorMessage.value = null },
            title = { Text("Cast Connection Failed") },
            text = { Text(castError) },
            confirmButton = {
                TextButton(onClick = { CastButtonState.castErrorMessage.value = null }) {
                    Text("OK")
                }
            },
        )
    }
}

private fun refreshCastRoutes(
    router: androidx.mediarouter.media.MediaRouter,
    cb: (List<com.google.android.gms.cast.CastDevice>) -> Unit,
) {
    val allRoutes = router.routes
    android.util.Log.d(
        "ftpmusic-cast",
        "[NavHost] refreshCastRoutes: ${allRoutes.size} total routes at ${System.currentTimeMillis() % 100000}",
    )
    allRoutes.forEach { r ->
        android.util.Log.d(
            "ftpmusic-cast",
            "[NavHost]   route: '${r.name}' isDefault=${r.isDefault} enabled=${r.isEnabled} id=${r.id.take(
                30,
            )} extras=${r.extras?.size() ?: 0}b",
        )
    }
    val castDevices = allRoutes.filter { !it.isDefault && it.name != "Phone" }.mapNotNull { route ->
        try {
            val device = com.google.android.gms.cast.CastDevice.getFromBundle(route.extras ?: android.os.Bundle())
            android.util.Log.d(
                "ftpmusic-cast",
                "[NavHost]   CastDevice from '${route.name}': ${device?.friendlyName ?: "NULL"} (${device?.deviceId ?: "no-id"})",
            )
            device
        } catch (_: Exception) {
            android.util.Log.w("ftpmusic-cast", "[NavHost]   getFromBundle failed for '${route.name}'")
            null
        }
    }
    val uniqueDevices = castDevices.distinctBy { it.friendlyName }
    android.util.Log.d(
        "ftpmusic-cast",
        "[NavHost] result: ${castDevices.size} CastDevices, ${uniqueDevices.size} unique (by name)",
    )
    cb(uniqueDevices)
}

/**
 * Fetch lyrics from Subsonic getLyrics, parse, cache. Used for miss + silent TTL refresh.
 */
private suspend fun fetchLyricsForTrack(
    artist: String,
    title: String,
    trackId: String?,
    lyricsEntry: com.lucasdss.ftpmusic.app.di.LyricsCacheEntryPoint,
    context: android.content.Context,
): com.lucasdss.ftpmusic.app.ui.player.LyricsDisplay {
    val dao = lyricsEntry.lyricsCacheDao()
    val api = lyricsEntry.subsonicApi()
    val authParams = lyricsEntry.subsonicAuthHelper().buildAuthParams(
        com.lucasdss.ftpmusic.app.di.SubsonicCredentials.username,
        com.lucasdss.ftpmusic.app.di.SubsonicCredentials.password,
    )
    return com.lucasdss.ftpmusic.app.ui.player.LyricsFetcher.fetchAndCache(
        artist = artist,
        title = title,
        trackId = trackId,
        dao = dao,
        getLyrics = { a, t -> api.getLyrics(authParams, a, t) },
        markFetched = {
            context.getSharedPreferences(
                MetadataSyncWorker.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            ).edit().putLong("last_lyrics_fetch_ms", System.currentTimeMillis()).apply()
        },
    )
}

private suspend fun refreshLyricsInBackground(
    artist: String,
    title: String,
    trackId: String,
    lyricsEntry: com.lucasdss.ftpmusic.app.di.LyricsCacheEntryPoint,
    context: android.content.Context,
) {
    fetchLyricsForTrack(artist, title, trackId, lyricsEntry, context)
}
