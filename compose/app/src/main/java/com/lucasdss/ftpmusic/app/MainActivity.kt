package com.lucasdss.ftpmusic.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.lucasdss.ftpmusic.app.playback.MediaService
import com.lucasdss.ftpmusic.app.playback.MediaServiceStartRequest
import com.lucasdss.ftpmusic.app.playback.MediaSessionPlaybackProvider
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import com.lucasdss.ftpmusic.app.ui.FtpmusicNavHost
import com.lucasdss.ftpmusic.app.ui.FtpmusicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playbackProvider: MediaSessionPlaybackProvider

    private val recoveryHandler = Handler(Looper.getMainLooper())
    private var recoveryWatchdogArmed = false
    private var recoveryEscalations = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Edge-to-edge is enforced on Android 15+ (targetSdk 36); opt in
        // explicitly so insets flow through the Compose Scaffolds.
        enableEdgeToEdge()
        // Pre-warm playback without claiming foreground-service status. Media
        // playback promotes the service only after a user playback request.
        val playbackInit = Intent(this, MediaService::class.java)
        playbackInit.action = MediaServiceStartRequest.ACTION_INITIALIZE
        startService(playbackInit)
        setContent {
            FtpmusicTheme {
                FtpmusicNavHost()
            }
        }
        // Initialize Cast SDK early
        if (isPlayServicesAvailable()) {
            try {
                CastContext.getSharedInstance(this)
                android.util.Log.d("ftpmusic-cast", "[MainActivity] CastContext initialized: SUCCESS")
            } catch (e: Exception) {
                android.util.Log.e("ftpmusic-cast", "[MainActivity] CastContext init FAILED: ${e.message}", e)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Self-heal hook: a transport control tapped while the playback pipeline
        // is down (system idle-stopped MediaService, zombie state) must never be
        // dropped — ask for a service (re)start and the provider replays the
        // command once a Player is attached again.
        playbackProvider.onPlaybackUnavailable = {
            android.util.Log.w(
                "ftpmusic",
                "[MainActivity] control arrived with no player — re-asserting playback service",
            )
            recoveryEscalations = 0
            ensurePlaybackService()
            armRecoveryWatchdog()
        }
    }

    override fun onStop() {
        playbackProvider.onPlaybackUnavailable = null
        recoveryHandler.removeCallbacksAndMessages(null)
        recoveryWatchdogArmed = false
        recoveryEscalations = 0
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Restart the pipeline if it died while we were away. The system can
        // idle-stop MediaService (media-session inactivity, Android 14+) while
        // the process stays alive; PlayerHolder.player then reads null and every
        // transport control would no-op. Re-warm exactly like onCreate: a plain
        // startService with ACTION_INITIALIZE. Safe here: with no Player wired
        // the service is not foreground, so there is no FGS to demote and no 5s
        // startForeground deadline to re-arm.
        val playerWired = PlayerHolder.player != null
        if (shouldReinitializePlaybackServiceOnResume(playerWired)) {
            android.util.Log.w(
                "ftpmusic",
                "[MainActivity] onResume: no wired player — re-initializing playback service",
            )
            val reinit = Intent(this, MediaService::class.java)
            reinit.action = MediaServiceStartRequest.ACTION_INITIALIZE
            try {
                startService(reinit)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[MainActivity] startService (INITIALIZE) failed: ${e.message}")
            }
            armRecoveryWatchdog()
        }
    }

    /** Bring the playback pipeline up as a foreground service. Mirrors
     *  PlaybackManager.ensurePlayer semantics: set the promote-on-create flag,
     *  then startForegroundService with ACTION_PLAYBACK so MediaService
     *  re-creates/re-wires a Player (the provider then replays any pending
     *  control). Only safe to call while this activity is foreground (onStart /
     *  a user control tap) — background starts are rejected and caught here. */
    private fun ensurePlaybackService() {
        MediaServiceStartRequest.foregroundRequested = true
        val intent = Intent(this, MediaService::class.java)
        intent.action = MediaServiceStartRequest.ACTION_PLAYBACK
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            MediaServiceStartRequest.foregroundRequested = false
            // ForegroundServiceStartNotAllowedException is API 31+ — reference it
            // by name so the catch works on every API level (class resolution of
            // the exception type would crash on API 26-30).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                android.util.Log.w("ftpmusic", "[MainActivity] Foreground start not allowed: ${e.message}")
            } else {
                android.util.Log.w("ftpmusic", "[MainActivity] startForegroundService failed: ${e.message}")
            }
        }
    }

    /**
     * Watchdog for the (re)start/teardown race: if a service (re)start did not
     * result in a wired Player within a few seconds, escalate once with another
     * foreground ACTION_PLAYBACK start (bounded attempts). Covers the case where
     * the first start was delivered to a service that was still tearing down.
     */
    private fun armRecoveryWatchdog() {
        if (recoveryWatchdogArmed) return
        recoveryWatchdogArmed = true
        recoveryHandler.postDelayed({
            recoveryWatchdogArmed = false
            if (PlayerHolder.player != null) return@postDelayed
            if (recoveryEscalations >= MAX_RECOVERY_ESCALATIONS) {
                android.util.Log.w(
                    "ftpmusic",
                    "[MainActivity] recovery escalations exhausted — player still not wired; next tap/onResume retries",
                )
                return@postDelayed
            }
            recoveryEscalations++
            android.util.Log.w(
                "ftpmusic",
                "[MainActivity] watchdog: player not wired after service start — escalating ($recoveryEscalations)",
            )
            ensurePlaybackService()
            armRecoveryWatchdog()
        }, RECOVERY_WATCHDOG_MS)
    }

    private fun isPlayServicesAvailable(): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) ==
            ConnectionResult.SUCCESS
    } catch (_: Exception) {
        false
    }

    companion object {
        /** Delay before escalating a failed playback-service (re)start. */
        internal const val RECOVERY_WATCHDOG_MS = 3000L

        /** Cap on consecutive escalations before giving up until the next trigger. */
        internal const val MAX_RECOVERY_ESCALATIONS = 2
    }
}

/**
 * Pure onResume decision for [MainActivity.onResume]: re-initialize the
 * playback service (plain startService ACTION_INITIALIZE) whenever no Player is
 * wired — the pipeline is dead regardless of what the service list claims.
 */
internal fun shouldReinitializePlaybackServiceOnResume(isPlayerWired: Boolean): Boolean = !isPlayerWired
