package com.lucasdss.ftpmusic.app.ui.library

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.DailyMixEntity
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.SyncStatus
import com.lucasdss.ftpmusic.app.ui.textHeadingL
import com.lucasdss.ftpmusic.app.ui.textLabelM
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DailyMixState(
    val buildingDailyMix: Boolean = false,
    val dailyMixProgress: Int = 0,
    val dailyMixTotal: Int = 0,
)

@HiltViewModel
class SyncingViewModel @Inject constructor(
    private val metadataSyncWorker: MetadataSyncWorker,
    private val metadataDao: CachedMetadataDao,
    private val genreMixDao: GenreMixDao,
    private val playlistDao: PlaylistDao,
    private val lyricsCacheDao: LyricsCacheDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    private val dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository,
) : ViewModel() {

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _dailyMix = MutableStateFlow(DailyMixState())
    val dailyMix: StateFlow<DailyMixState> = _dailyMix.asStateFlow()

    private val _isDone = MutableStateFlow(false)
    val isDone: StateFlow<Boolean> = _isDone.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    private var elapsedJob: kotlinx.coroutines.Job? = null
    private var syncJob: kotlinx.coroutines.Job? = null

    fun startSync(userTriggered: Boolean = false, rebuildOnly: Boolean = false) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            val existing = metadataDao.albumCount()
            // rebuildOnly: just regenerate Daily Mixes, no server sync
            if (rebuildOnly) {
                _status.value = SyncStatus(
                    albums = existing,
                    albumsTotal = existing,
                    artists = metadataDao.artistCount(),
                    trackCount = metadataDao.cachedTrackCount(),
                    genres = genreMixDao.getTopGenres().size,
                    phase = "complete",
                    isRunning = false,
                )
                startElapsedTimer()
                generateDailyMixesSync(manual = true)
                _isDone.value = true
                elapsedJob?.cancel()
                return@launch
            }
            // User-triggered full sync: re-sync from server, force track re-fetch
            if (userTriggered) {
                val mixTotal = dailyMixRepository.getAll().size
                _status.value = SyncStatus(
                    albumsTotal = 0,
                    artistsTotal = 0,
                    genresTotal = 0,
                    albumTracksProgressTotal = 0,
                    dailyMixTotal = mixTotal,
                    phase = "albums",
                    isRunning = true,
                )
                startElapsedTimer()
                // Retry loop: if CAS fails (another sync running), wait and retry
                // until our force-resync starts. The user explicitly requested this.
                // Max 3 retries — if something keeps the CAS slot indefinitely, give up.
                var retries = 0
                var forceJob: kotlinx.coroutines.Job? = null
                while (forceJob == null && retries < 3) {
                    forceJob = metadataSyncWorker.syncNowAsync(forceTrackResync = true)
                    if (forceJob == null) {
                        metadataSyncWorker.status.first { !it.isRunning }
                        retries++
                    }
                }
                if (forceJob == null) {
                    elapsedJob?.cancel()
                    return@launch
                }
                metadataSyncWorker.status.collect { s ->
                    // Merge worker fields into our status — preserve elapsedMs
                    // (owned by the timer) and dailyMixTotal (set by us). Using
                    // update{} reads the current state, so the timer's live
                    // elapsedMs is carried forward instead of overwritten by
                    // the worker's stale phase-boundary elapsed value.
                    _status.update {
                        it.copy(
                            albums = s.albums, albumsTotal = s.albumsTotal,
                            artists = s.artists, artistsTotal = s.artistsTotal,
                            genres = s.genres, genresTotal = s.genresTotal,
                            albumTracksProgress = s.albumTracksProgress,
                            albumTracksProgressTotal = s.albumTracksProgressTotal,
                            trackCount = s.trackCount,
                            phase = s.phase, isRunning = s.isRunning,
                            dailyMixTotal = mixTotal,
                        )
                    }
                    if (!s.isRunning && (s.phase == "complete" || s.phase == "error")) {
                        if (s.phase == "complete") {
                            appContext.getSharedPreferences(MetadataSyncWorker.PREFS_NAME, Context.MODE_PRIVATE).edit()
                                .putInt("sync_albums", s.albums)
                                .putInt("sync_artists", s.artists)
                                .putInt("sync_tracks", s.trackCount)
                                .putInt("sync_genres", s.genres)
                                .apply()
                            startDailyMixPhase()
                            generateDailyMixesSync()
                        } else {
                            _isError.value = true
                        }
                        _isDone.value = true
                        elapsedJob?.cancel()
                        return@collect
                    }
                }
                return@launch
            }
            // First login or no metadata: full sync. Try CAS first — if another
            // sync is already running, observe its status instead of starting a new one.
            _status.value = SyncStatus(
                albumsTotal = 0,
                artistsTotal = 0,
                genresTotal = 0,
                albumTracksProgressTotal = 0,
                dailyMixTotal = dailyMixRepository.getAll().size,
                phase = "albums",
                isRunning = true,
            )
            startElapsedTimer()
            metadataSyncWorker.syncNowAsync()
            metadataSyncWorker.status.collect { s ->
                // Merge worker fields into our status — preserve dailyMixTotal
                // (set by us) and dailyMixProgress (set during generation).
                _status.update {
                    it.copy(
                        albums = s.albums, albumsTotal = s.albumsTotal,
                        artists = s.artists, artistsTotal = s.artistsTotal,
                        genres = s.genres, genresTotal = s.genresTotal,
                        albumTracksProgress = s.albumTracksProgress,
                        albumTracksProgressTotal = s.albumTracksProgressTotal,
                        trackCount = s.trackCount,
                        phase = s.phase, isRunning = s.isRunning,
                    )
                }
                if (!s.isRunning && (s.phase == "complete" || s.phase == "error")) {
                    if (s.phase == "complete") {
                        appContext.getSharedPreferences(MetadataSyncWorker.PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .putInt("sync_albums", s.albums)
                            .putInt("sync_artists", s.artists)
                            .putInt("sync_tracks", s.trackCount)
                            .putInt("sync_genres", s.genres)
                            .apply()
                        startDailyMixPhase()
                        generateDailyMixesSync()
                    } else {
                        _isError.value = true
                    }
                    _isDone.value = true
                    elapsedJob?.cancel()
                    return@collect
                }
            }
        }
    }

    private fun startElapsedTimer() {
        elapsedJob?.cancel()
        elapsedJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            while (isActive) {
                _status.update { it.copy(elapsedMs = System.currentTimeMillis() - startMs) }
                delay(1000L)
            }
        }
    }

    private fun startDailyMixPhase() {
        _status.update { it.copy(phase = "dailyMix", isRunning = true) }
    }

    /** Generate/refresh every Custom Daily Mix, reporting per-mix progress. */
    private suspend fun generateDailyMixesSync(manual: Boolean = false) {
        var mixTotal = 0
        try {
            val today = java.time.LocalDate.now().toString()
            mixTotal = dailyMixRepository.getAll().size
            if (mixTotal == 0) return

            _dailyMix.value =
                DailyMixState(buildingDailyMix = true, dailyMixTotal = mixTotal, dailyMixProgress = 0)

            dailyMixRepository.generateAll(today, manual = manual) { done, _ ->
                _dailyMix.value = _dailyMix.value.copy(dailyMixProgress = done)
                _status.value = _status.value.copy(dailyMixProgress = done)
                delay(300) // let UI show progress per mix
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("ftpmusic", "[DailyMix] generation failed: ${e.message}", e)
        } finally {
            _dailyMix.value = _dailyMix.value.copy(buildingDailyMix = false)
            _status.value = _status.value.copy(
                dailyMixProgress = mixTotal,
                dailyMixTotal = mixTotal,
                isRunning = false,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncingScreen(
    onComplete: () -> Unit,
    userTriggered: Boolean = false,
    rebuildOnly: Boolean = false,
    viewModel: SyncingViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.startSync(userTriggered, rebuildOnly) }
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isDone by viewModel.isDone.collectAsStateWithLifecycle()
    val isError by viewModel.isError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(isDone) {
        if (isDone) {
            if (userTriggered) {
                Toast.makeText(
                    context,
                    if (isError) "Sync failed — check connection" else "Sync complete",
                    Toast.LENGTH_SHORT,
                ).show()
                return@LaunchedEffect
            }
            Toast.makeText(context, "Sync complete — navigating…", Toast.LENGTH_SHORT).show()
            delay(3000)
            onComplete()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    val rows = status.toRows()

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D0D14), Color(0xFF1A1A2E)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo pulse (unchanged)
            Text("♫", fontSize = 48.sp, color = Color(0xFF00C8B4).copy(alpha = pulse))
            Spacer(Modifier.height(24.dp))
            Text("Syncing your library…", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Keep the app open while we fetch your music", color = Color(0xFF666666), fontSize = 14.sp)

            Spacer(Modifier.height(32.dp))

            // ── Row table ────────────────────────────────
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rows.forEach { row ->
                    SyncRowCard(row)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Elapsed timer or status ──────────────────
            if (!isDone) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Elapsed ${formatSyncDuration(status.elapsedMs)}",
                        color = Color(0xFF888888),
                        fontSize = 14.sp,
                    )
                    if (status.isRunning) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            color = Color(0xFFFFA726),
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            } else {
                if (userTriggered) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isError) {
                            Text(
                                "Sync failed",
                                color = Color(0xFFE84040),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Server connection lost or API error.\nCheck your connection and try again.",
                                color = Color(0xFF888888),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        } else {
                            Text(
                                "Ready! 🎵",
                                color = Color(0xFF00C8B4),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onComplete,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C8B4)),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("OK", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncRowCard(row: SyncStatus.Row) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C2E))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status icon
        Text(
            row.icon,
            fontSize = 16.sp,
            color = statusColor(row.icon),
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        // Label + progress bar
        Column(Modifier.weight(1f)) {
            Text(row.label, color = Color.White, fontSize = 14.sp)
            if (row.showProgress && row.total > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (row.total > 0) row.progress.toFloat() / row.total else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF00C8B4),
                    trackColor = Color.White.copy(alpha = 0.1f),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // Count
        val countText = if (row.total > 0) {
            "${formatNumber(row.progress)} / ${formatNumber(row.total)}"
        } else {
            formatNumber(row.progress)
        }
        Text(countText, color = Color(0xFF888888), fontSize = 13.sp)
    }
}

private fun statusColor(icon: String) = when (icon) {
    "✓" -> Color(0xFF00C8B4)
    "⟳" -> Color(0xFFFFA726)
    else -> Color(0xFF555555)
}

private fun formatNumber(n: Int): String = if (n >= 1000) "%,d".format(java.util.Locale.US, n) else n.toString()

internal fun formatSyncDuration(ms: Long): String {
    val sec = ms / 1000
    val min = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(min, s)
}
