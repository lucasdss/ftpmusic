package com.lucasdss.ftpmusic.app.ui

/**
 * Pure helper for ADR-0056 touch-target size — testable without Compose.
 * Adaptive UI uses [com.lucasdss.ftpmusic.app.ui.components.minTouchTarget] (= adp of this).
 */
fun minTouchTargetDp(): Float = com.lucasdss.ftpmusic.app.ui.components.MIN_TOUCH_TARGET_DP
