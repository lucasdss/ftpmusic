package com.lucasdss.ftpmusic.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search

/**
 * Canonical bottom-nav items (ADR-0055). Settings is AppHeader gear, not a tab.
 * Favorites remains a first-class peer tab.
 */
fun bottomNavItems(): List<BottomNavItem> = listOf(
    BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem("library", "Library", Icons.Filled.MusicNote, Icons.Outlined.MusicNote),
    BottomNavItem("favorites", "Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
)
