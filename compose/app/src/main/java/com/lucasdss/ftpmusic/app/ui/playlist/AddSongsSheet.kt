package com.lucasdss.ftpmusic.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl
import kotlinx.coroutines.delay

/**
 * "Add Songs" track picker — shared between:
 *  - [PlaylistDetailScreen] (add tracks INTO the open playlist) via [AddSongsSheet].
 *  - Library create flow (add tracks to a freshly created playlist) by embedding
 *    [AddSongsPickerContent] directly inside the existing create sheet.
 *
 * UX mirrors YouTube Music: search the local catalog (or browse recent
 * suggestions), multi-select rows, then "Add N songs". Rows already present in
 * the target playlist are shown as "Added" and cannot be re-selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(
    existingTrackIds: Set<String>,
    search: suspend (String) -> List<TrackEntity>,
    suggestions: suspend () -> List<TrackEntity>,
    onAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C2E),
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        AddSongsPickerContent(
            existingTrackIds = existingTrackIds,
            search = search,
            suggestions = suggestions,
            onAdd = onAdd,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Picker body without its own sheet wrapper — embeddable inside a parent
 * ModalBottomSheet (Library create flow) to avoid nested sheets.
 */
@Composable
fun AddSongsPickerContent(
    existingTrackIds: Set<String>,
    search: suspend (String) -> List<TrackEntity>,
    suggestions: suspend () -> List<TrackEntity>,
    onAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var suggestionTracks by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selection = selectedIds.toSet()

    // Suggestions: recently played, shown until the user types ≥ 2 chars.
    LaunchedEffect(Unit) {
        suggestionTracks = runCatching { suggestions() }.getOrDefault(emptyList())
    }

    // Debounced search over the local track catalog.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(250)
        if (query.trim() != q) return@LaunchedEffect // stale keystroke
        results = runCatching { search(q) }.getOrDefault(emptyList())
        isSearching = false
    }

    fun toggle(trackId: String) {
        selectedIds = if (trackId in selection) selectedIds - trackId else selectedIds + trackId
    }

    // Whole content scrolls so the action buttons stay reachable on short
    // screens / when the results list is tall (sheet content is taller than
    // the window on some devices, and ModalBottomSheet does not scroll by
    // default — previously the Add button ended up off-screen).
    Column(
        Modifier.fillMaxWidth().padding(bottom = spacing3XL())
            .verticalScroll(rememberScrollState()),
    ) {
        // Handle
        Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
            Box(
                Modifier.width(
                    adp(32f),
                ).height(adp(4f)).clip(RoundedCornerShape(adp(2f))).background(Color(0xFF444444)),
            )
        }
        Spacer(Modifier.height(spacingL()))
        Text(
            "Add Songs",
            color = Color.White,
            fontSize = textHeadingM(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingXS()),
        )

        // Search field (same styling as LibraryScreen contextual search)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs…", color = Color(0xFF666666)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF555555)) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    Icon(
                        Icons.Default.Close,
                        "Clear",
                        tint = Color(0xFF555555),
                        modifier = Modifier.clickable {
                            query =
                                ""
                        },
                    )
                }
            } else {
                null
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color(0xFF252538),
                unfocusedContainerColor = Color(0xFF252538),
                cursorColor = Color(0xFF00C8B4),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingXS()),
            shape = RoundedCornerShape(cornerM()), singleLine = true,
        )

        val showResults = query.trim().length >= 2
        val rows = if (showResults) results else suggestionTracks

        if (isSearching) {
            Box(Modifier.fillMaxWidth().height(adp(160f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00C8B4), modifier = Modifier.size(iconMedium()))
            }
        } else if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(spacing3XL()), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.AddCircleOutline,
                        null,
                        tint = Color(0xFF333333),
                        modifier = Modifier.size(iconLarge()),
                    )
                    Spacer(Modifier.height(spacingS()))
                    Text(
                        if (showResults) "No songs match" else "No suggestions yet — search for songs",
                        color = Color(0xFF888888),
                        fontSize = textBodyM(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = adp(400f))) {
                itemsIndexed(rows, key = { _, t -> t.id }) { _, track ->
                    val isAdded = track.id in existingTrackIds
                    val isSelected = track.id in selection
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !isAdded) { toggle(track.id) }
                            .padding(horizontal = spacingL(), vertical = adp(10f)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(adp(40f)).clip(RoundedCornerShape(cornerS())).background(Color(0xFF1E1E1E)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val coverUrl = rememberCoverArtUrl(track.coverArtUrl, 80)
                            if (coverUrl != null) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    Icons.Default.MusicNote,
                                    null,
                                    tint = Color(0xFF555555),
                                    modifier = Modifier.size(adp(18f)),
                                )
                            }
                        }
                        Spacer(Modifier.width(spacingM()))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                color = if (isAdded) Color(0xFF666666) else Color.White,
                                fontSize = textBodyM(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                track.artist?.let {
                                    Text(
                                        it,
                                        color = Color(0xFF888888),
                                        fontSize = textLabelM(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                                track.durationSeconds?.let { raw ->
                                    // Some ingestion paths store milliseconds in
                                    // duration_seconds; normalize before formatting.
                                    val d = if (raw > 100_000) raw / 1000 else raw
                                    Text(
                                        " · ${d / 60}:${(d % 60).toString().padStart(2, '0')}",
                                        color = Color(0xFF666666),
                                        fontSize = textLabelM(),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(spacingS()))
                        when {
                            isAdded -> Text("Added", color = Color(0xFF555555), fontSize = textLabelM())

                            isSelected -> Icon(
                                Icons.Filled.CheckCircle,
                                null,
                                tint = Color(0xFF00C8B4),
                                modifier = Modifier.size(iconSmall()),
                            )

                            else -> Icon(
                                Icons.Outlined.AddCircleOutline,
                                null,
                                tint = Color(0xFF666666),
                                modifier = Modifier.size(iconSmall()),
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFF2A2A3E), modifier = Modifier.padding(horizontal = spacingL()))
                }
            }
        }

        Spacer(Modifier.height(spacingM()))
        // Actions: Cancel + Add N songs
        val addGradient = if (selection.isNotEmpty()) {
            Brush.linearGradient(listOf(Color(0xFF00C8B4), Color(0xFFB040E8)))
        } else {
            Brush.linearGradient(listOf(Color(0xFF2A2A3E), Color(0xFF2A2A3E)))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spacingL()),
            horizontalArrangement = Arrangement.spacedBy(spacingM()),
        ) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252538)),
                shape = RoundedCornerShape(cornerM()),
                modifier = Modifier.weight(1f).height(adp(48f)),
            ) { Text("Cancel", color = Color(0xFF888888)) }
            Button(
                onClick = { onAdd(selection.toList()) },
                enabled = selection.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(cornerM()),
                modifier = Modifier.weight(1f).height(adp(48f)).background(addGradient, RoundedCornerShape(cornerM())),
            ) {
                Text(
                    if (selection.isEmpty()) {
                        "Add Songs"
                    } else {
                        "Add ${selection.size} song${if (selection.size == 1) "" else "s"}"
                    },
                    color = if (selection.isEmpty()) Color(0xFF888888) else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = textBodyM(),
                )
            }
        }
    }
}
