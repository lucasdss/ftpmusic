package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.LocalListChromePrefs
import com.lucasdss.ftpmusic.app.ui.Muted
import com.lucasdss.ftpmusic.app.ui.knobSize
import com.lucasdss.ftpmusic.app.ui.primaryTextWeight
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelM

/**
 * Shared song list row: title + optional duration on the title line,
 * meta row below (cache · like · dislike). Options (⋮) on the right.
 *
 * List chrome gated by [LocalListChromePrefs] (ADR-0104). Fixed title size
 * (ADR 0096 / SCROLL_FPS).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isActive: Boolean = false,
    downloadStatus: String = "none",
    durationLabel: String? = null,
    isLiked: Boolean? = null,
    isDisliked: Boolean? = null,
    onLike: (() -> Unit)? = null,
    onDislike: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    /** Replaces the default ⋮ when set (e.g. Downloads delete). */
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val chrome = LocalListChromePrefs.current
    val showDuration = chrome.showListDuration && !durationLabel.isNullOrBlank()
    val showLike = chrome.showListReactions && isLiked != null && onLike != null
    val showDislike = chrome.showListReactions && isDisliked != null && onDislike != null

    val playableModifier = when {
        onClick != null && onLongClick != null ->
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)

        onClick != null -> Modifier.clickable(onClick = onClick)

        else -> Modifier
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier.weight(1f).then(playableModifier)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = if (isActive) BrandTeal else Color.White,
                    fontSize = textHeadingS(),
                    fontWeight = primaryTextWeight(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                    modifier = Modifier.weight(1f),
                )
                if (showDuration) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = durationLabel!!,
                        color = Muted,
                        fontSize = textLabelM(),
                        maxLines = 1,
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color(0xFF888888),
                    fontSize = textLabelM(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
            }
            val showMeta = downloadStatus != "none" || showLike || showDislike
            if (showMeta) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (downloadStatus != "none") {
                        DownloadDot(downloadStatus)
                    }
                    if (showLike) {
                        ReactionGlyphButton(
                            icon = Icons.Filled.ThumbUp,
                            contentDescription = if (isLiked == true) "Unlike" else "Like",
                            tint = if (isLiked == true) BrandTeal else Color(0xFF444444),
                            onClick = onLike!!,
                        )
                    }
                    if (showDislike) {
                        ReactionGlyphButton(
                            icon = Icons.Filled.ThumbDown,
                            contentDescription = if (isDisliked == true) "Remove dislike" else "Dislike",
                            tint = if (isDisliked == true) Color(0xFFE84040) else Color(0xFF444444),
                            onClick = onDislike!!,
                        )
                    }
                }
            }
        }
        when {
            trailingContent != null -> {
                Spacer(Modifier.width(spacingS()))
                trailingContent()
            }

            onMore != null -> {
                Spacer(Modifier.width(spacingS()))
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Track menu",
                        tint = Color(0xFF444444),
                        modifier = Modifier.size(knobSize()),
                    )
                }
            }
        }
    }
}

/** Formats seconds as `m:ss` for song meta rows. */
fun formatSongDuration(seconds: Int): String {
    val mins = seconds / 60
    val sec = seconds % 60
    return "$mins:${sec.toString().padStart(2, '0')}"
}
