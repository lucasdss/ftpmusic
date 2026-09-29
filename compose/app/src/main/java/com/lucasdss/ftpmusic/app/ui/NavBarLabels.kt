package com.lucasdss.ftpmusic.app.ui

/**
 * Icon TalkBack contentDescription only when labels are hidden
 * (avoids double announce when the NavigationBarItem label is visible).
 */
internal fun navTabIconContentDescription(hideNavLabels: Boolean, label: String): String? =
    if (hideNavLabels) label else null
