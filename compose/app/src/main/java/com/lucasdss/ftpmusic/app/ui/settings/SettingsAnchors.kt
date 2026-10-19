package com.lucasdss.ftpmusic.app.ui.settings

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.lucasdss.ftpmusic.app.ui.encodeNavQuery
import kotlinx.coroutines.delay

/** Scroll targets for Settings search → leaf deep-link (ADR-0149 fixup). */
object SettingsAnchors {
    const val CLEAR_CACHE = "clear_cache"
    const val CONTINUOUS_PLAY = "continuous_play"
    const val CELLULAR = "cellular"
    const val CAST_FROM_PHONE = "cast_from_phone"
    const val CUSTOM_HEADERS = "custom_headers"
    const val BT_RESUME = "bt_resume"
    const val PLAYBACK_NOTIF = "playback_notif"
}

fun settingsLeafRoute(base: String, anchor: String? = null): String {
    val encoded = encodeNavQuery(anchor.orEmpty())
    return "$base?anchor=$encoded"
}

fun SettingNode.routeForQuery(query: String): String {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return settingsLeafRoute(route)
    val anchor = keywordAnchors.entries.firstOrNull { (kw, _) ->
        q.contains(kw) || kw.contains(q)
    }?.value ?: defaultAnchor
    return settingsLeafRoute(route, anchor)
}

@Composable
fun Modifier.settingsScrollAnchor(key: String, scrollTo: String?): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(scrollTo, key) {
        if (!scrollTo.isNullOrBlank() && scrollTo == key) {
            delay(48)
            requester.bringIntoView()
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .testTag("settings_anchor_$key")
}
