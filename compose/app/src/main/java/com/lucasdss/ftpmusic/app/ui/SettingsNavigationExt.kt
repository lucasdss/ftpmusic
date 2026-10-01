package com.lucasdss.ftpmusic.app.ui

import androidx.navigation.NavController

/** Opens Settings with market stack-back policy (ADR-0056). */
fun NavController.navigateToSettings() {
    navigate(SETTINGS_ROUTE) { settingsStackOptions() }
}
