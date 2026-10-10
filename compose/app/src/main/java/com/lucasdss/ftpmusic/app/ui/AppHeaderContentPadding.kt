package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Top inset for primary-tab Lazy lists under the overlay AppHeader (ADR-0107 hard-fix).
 *
 * Fixed to full header height — list viewport does not resize during enterAlways
 * collapse; content scrolls into the header band when chrome translates away.
 */
val LocalAppHeaderContentPadding = compositionLocalOf { 0.dp }

/** Pure helper for tests — same rule as shell CompositionLocalProvider. */
fun resolveAppHeaderContentPadding(showHeader: Boolean, headerHeight: Dp): Dp =
    if (showHeader && headerHeight > 0.dp) headerHeight else 0.dp
