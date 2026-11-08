package com.lucasdss.ftpmusic.app.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** True when infinite / decorative motion should be suppressed. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun reduceMotion(): Boolean = LocalReduceMotion.current

/**
 * Provides [LocalReduceMotion] from system animator scales (+ optional Appearance force).
 */
@Composable
fun ProvideReduceMotion(
    appearanceForceReduce: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reduce = remember(appearanceForceReduce, context) {
        val cr = context.contentResolver
        val animator = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transition = Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        ReduceMotionPolicy.shouldReduce(animator, transition, appearanceForceReduce)
    }
    CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
}
