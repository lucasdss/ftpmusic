package com.lucasdss.ftpmusic.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.data.repository.CustomMix
import com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository
import com.lucasdss.ftpmusic.app.data.repository.MixFilters
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingL
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelM
import com.lucasdss.ftpmusic.app.ui.textLabelS

private val TEAL = Color(0xFF00C8B4)
private val ORANGE = Color(0xFFFFA726)
private val PURPLE = Color(0xFFB040E8)
private val RED = Color(0xFFE84040)
private val ROW_BG = Color(0xFF1C1C2E)
private val MUTED = Color(0xFF888888)
private val DIM = Color(0xFF555555)

/** Settings page: manage up to 20 named Custom Daily Mixes with composite
 *  filters (genres + decades + artists). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomDailyMixesScreen(onBack: () -> Unit = {}, viewModel: CustomDailyMixesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF101018))) {
        val editor = state.editor
        if (editor != null) {
            MixEditor(editor = editor, state = state, viewModel = viewModel)
        } else {
            MixList(state = state, onBack = onBack, viewModel = viewModel)
        }
    }
}

// ─── List ─────────────────────────────────────────────────────────────────────

@Composable
private fun MixList(state: CustomDailyMixesUiState, onBack: () -> Unit, viewModel: CustomDailyMixesViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFCCCCCC))
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    "Custom Daily Mixes",
                    color = Color.White,
                    fontSize = textHeadingS(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${state.mixes.size} / ${DailyMixRepository.MAX_MIXES} configured",
                    color = DIM,
                    fontSize = textLabelS(),
                )
            }
            Button(
                onClick = { viewModel.openNew() },
                enabled = !state.atCapacity,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TEAL,
                    contentColor = Color(0xFF101018),
                    disabledContainerColor = Color(0x14FFFFFF),
                    disabledContentColor = Color(0xFF444444),
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.semantics { testTag = "custom_mixes_add" },
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add", fontSize = textLabelM(), fontWeight = FontWeight.Bold)
            }
        }

        if (state.atCapacity) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ORANGE.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    "Limit reached (${DailyMixRepository.MAX_MIXES}). Delete a mix to add another.",
                    color = ORANGE,
                    fontSize = textLabelM(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (state.mixes.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No mixes yet", color = DIM, fontSize = textBodyM(), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap \"Add\" to create your first custom Daily Mix.",
                    color = Color(0xFF444444),
                    fontSize = textLabelM(),
                )
            }
            return
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.mixes, key = { _, mix -> mix.id }) { index, mix ->
                MixRow(
                    index = index + 1,
                    mix = mix,
                    missing = state.missingGenres[mix.id].orEmpty(),
                    onEdit = { viewModel.openEditor(mix) },
                    onDelete = { viewModel.delete(mix) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixRow(index: Int, mix: CustomMix, missing: List<String>, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ROW_BG)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics { testTag = "custom_mix_row_${mix.id}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$index",
            color = Color(0xFF444444),
            fontSize = textLabelM(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                mix.name,
                color = Color.White,
                fontSize = textBodyM(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                dimensionBadges(mix.filters).forEach { (label, color) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = 0.13f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(label, color = color, fontSize = textLabelS(), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (mix.isDefault) {
                    Text("auto", color = DIM, fontSize = textLabelS())
                }
                if (mix.autoCache) {
                    Icon(Icons.Default.ArrowDownward, "Auto-cache on", tint = TEAL, modifier = Modifier.size(12.dp))
                }
            }
            Text(
                sourceLabel(mix.filters),
                color = Color(0xFF666666),
                fontSize = textLabelS(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (missing.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = RED, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Missing: ${missing.joinToString(", ")}",
                        color = RED,
                        fontSize = textLabelS(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(
            onClick = onEdit,
            modifier = Modifier.semantics { testTag = "custom_mix_edit_${mix.id}" },
        ) {
            Icon(Icons.Default.Tune, "Edit mix", tint = DIM, modifier = Modifier.size(16.dp))
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.semantics { testTag = "custom_mix_delete_${mix.id}" },
        ) {
            Icon(Icons.Default.Close, "Delete mix", tint = DIM, modifier = Modifier.size(16.dp))
        }
    }
}

private fun dimensionBadges(filters: MixFilters): List<Pair<String, Color>> {
    val badges = mutableListOf<Pair<String, Color>>()
    if (filters.genres.isNotEmpty()) badges += "Genre" to TEAL
    if (filters.decades.isNotEmpty()) badges += "Decade" to ORANGE
    if (filters.artistIds.isNotEmpty() || filters.includeFavoriteArtists) badges += "Artists" to PURPLE
    return badges
}

private fun sourceLabel(filters: MixFilters): String {
    val parts = mutableListOf<String>()
    if (filters.genres.isNotEmpty()) {
        val shown = filters.genres.take(2).joinToString(", ")
        parts += if (filters.genres.size > 2) "$shown +${filters.genres.size - 2}" else shown
    }
    if (filters.decades.isNotEmpty()) parts += filters.decades.joinToString(", ")
    if (filters.includeFavoriteArtists) {
        parts += if (filters.artistIds.isEmpty()) {
            "All favorites"
        } else {
            "Favorites +${filters.artistIds.size}"
        }
    } else if (filters.artistIds.isNotEmpty()) {
        parts += "${filters.artistIds.size} artist${if (filters.artistIds.size != 1) "s" else ""}"
    }
    return parts.joinToString(" · ")
}

// ─── Editor ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixEditor(editor: MixEditorState, state: CustomDailyMixesUiState, viewModel: CustomDailyMixesViewModel) {
    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::closeEditor) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFCCCCCC))
            }
            Text(
                if (editor.isNew) "New Mix" else "Edit Mix",
                color = Color.White,
                fontSize = textHeadingS(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            if (!editor.isNew) {
                IconButton(
                    onClick = viewModel::requestDelete,
                    modifier = Modifier.semantics { testTag = "mix_editor_trash" },
                ) {
                    Icon(Icons.Default.Delete, "Delete mix", tint = RED, modifier = Modifier.size(18.dp))
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (editor.pendingDelete) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RED.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Delete \"${editor.name}\"?",
                        color = Color.White,
                        fontSize = textLabelM(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(
                        onClick = viewModel::cancelDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x14FFFFFF),
                            contentColor = Color(0xFFCCCCCC),
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("Cancel", fontSize = textLabelS(), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = viewModel::confirmDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = RED, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.semantics { testTag = "mix_editor_delete_confirm" },
                    ) {
                        Text("Delete", fontSize = textLabelS(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Name
            Column {
                Text("MIX NAME", color = MUTED, fontSize = textLabelS(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = viewModel::setName,
                    singleLine = true,
                    placeholder = { Text("Give it a name…", color = DIM) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TEAL.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color(0x14FFFFFF),
                        cursorColor = TEAL,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().semantics { testTag = "mix_editor_name" },
                )
                Text(
                    "${editor.name.length}/${DailyMixRepository.NAME_MAX}",
                    color = DIM,
                    fontSize = textLabelS(),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }

            // Suggestions
            Column {
                Text("SUGGESTIONS", color = DIM, fontSize = textLabelS(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val suggestions = remember(editor.activeTab, editor.genres, editor.decades) {
                    viewModel.suggestions(editor)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            text = suggestion,
                            selected = editor.name == suggestion,
                            onClick = { viewModel.setName(suggestion) },
                        )
                    }
                }
            }

            // Source tabs (cumulative — switching keeps every dimension)
            Column {
                Text("SOURCE (combine freely)", color = MUTED, fontSize = textLabelS(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceTab(
                        label = "Genre (${editor.genres.size})",
                        color = TEAL,
                        selected = editor.activeTab == DailyMixRepository.KIND_GENRES,
                        onClick = { viewModel.setActiveTab(DailyMixRepository.KIND_GENRES) },
                        tag = "source_tab_genres",
                    )
                    SourceTab(
                        label = "Decade (${editor.decades.size})",
                        color = ORANGE,
                        selected = editor.activeTab == DailyMixRepository.KIND_DECADES,
                        onClick = { viewModel.setActiveTab(DailyMixRepository.KIND_DECADES) },
                        tag = "source_tab_decades",
                    )
                    SourceTab(
                        label = "Artists (${editor.artistIds.size + if (editor.includeFavoriteArtists) 1 else 0})",
                        color = PURPLE,
                        selected = editor.activeTab == DailyMixRepository.KIND_FAVORITE_ARTISTS,
                        onClick = { viewModel.setActiveTab(DailyMixRepository.KIND_FAVORITE_ARTISTS) },
                        tag = "source_tab_favoriteArtists",
                    )
                }
                Text(
                    "Tracks must match every dimension you fill (Rock ∩ 90s). " +
                        "Multiple picks in one dimension are any-of.",
                    color = DIM,
                    fontSize = textLabelS(),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            when (editor.activeTab) {
                DailyMixRepository.KIND_GENRES -> GenresSection(editor, state, viewModel)
                DailyMixRepository.KIND_DECADES -> DecadesSection(editor, viewModel)
                else -> ArtistsSection(editor, state, viewModel)
            }

            // Auto-cache
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ROW_BG)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-Cache Mix", color = Color.White, fontSize = textBodyM(), fontWeight = FontWeight.Bold)
                    Text(
                        if (editor.autoCache) {
                            "New tracks will be cached when this mix regenerates"
                        } else {
                            "Off — tracks cached only when added to queue"
                        },
                        color = Color(0xFF666666),
                        fontSize = textLabelS(),
                    )
                }
                Switch(
                    checked = editor.autoCache,
                    onCheckedChange = viewModel::setAutoCache,
                    colors = SwitchDefaults.colors(checkedTrackColor = TEAL),
                    modifier = Modifier.semantics { testTag = "mix_editor_autocache" },
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = viewModel::save,
            enabled = viewModel.canSave(editor) && !state.isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = TEAL,
                contentColor = Color(0xFF101018),
                disabledContainerColor = Color(0x14FFFFFF),
                disabledContentColor = Color(0xFF444444),
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { testTag = "mix_editor_save" },
        ) {
            Text(
                if (editor.isNew) "Add Mix" else "Save Changes",
                fontSize = textHeadingS(),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenresSection(
    editor: MixEditorState,
    state: CustomDailyMixesUiState,
    viewModel: CustomDailyMixesViewModel,
) {
    val visible = viewModel.visibleGenres(editor)
    // Selected genres that vanished from the library have no chip in
    // `state.genres`; render them explicitly so they can be removed.
    val missingSelected = editor.genres
        .filter { name -> state.genres.none { it.name == name } }
        .sorted()
    Column {
        Text(
            "GENRES (${editor.genres.size} selected)",
            color = DIM,
            fontSize = textLabelS(),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            missingSelected.forEach { name ->
                PickChip(
                    text = "$name ✕",
                    color = RED,
                    selected = true,
                    onClick = { viewModel.toggleGenre(name) },
                    tag = "genre_chip_$name",
                )
            }
            visible.forEach { genre ->
                PickChip(
                    text = genre.name,
                    color = TEAL,
                    selected = genre.name in editor.genres,
                    onClick = { viewModel.toggleGenre(genre.name) },
                    tag = "genre_chip_${genre.name}",
                )
            }
        }
        if (viewModel.hasMoreGenres(editor)) {
            TextButton(
                onClick = viewModel::loadMoreGenres,
                modifier = Modifier.semantics { testTag = "genre_load_more" },
            ) {
                Text("Load more (${viewModel.remainingGenres(editor)} left)", color = TEAL)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecadesSection(editor: MixEditorState, viewModel: CustomDailyMixesViewModel) {
    Column {
        Text(
            "DECADES (${editor.decades.size} selected)",
            color = DIM,
            fontSize = textLabelS(),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DailyMixRepository.DECADE_RANGES.keys.forEach { decade ->
                PickChip(
                    text = decade,
                    color = ORANGE,
                    selected = decade in editor.decades,
                    onClick = { viewModel.toggleDecade(decade) },
                    tag = "decade_chip_$decade",
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistsSection(
    editor: MixEditorState,
    state: CustomDailyMixesUiState,
    viewModel: CustomDailyMixesViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Legacy dynamic favorites
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ROW_BG)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("All favorite artists", color = Color.White, fontSize = textBodyM(), fontWeight = FontWeight.Bold)
                Text(
                    "Dynamically includes every artist you thumb-up",
                    color = Color(0xFF666666),
                    fontSize = textLabelS(),
                )
            }
            Switch(
                checked = editor.includeFavoriteArtists,
                onCheckedChange = viewModel::setIncludeFavoriteArtists,
                colors = SwitchDefaults.colors(checkedTrackColor = PURPLE),
                modifier = Modifier.semantics { testTag = "artist_favorites_toggle" },
            )
        }

        OutlinedTextField(
            value = editor.artistQuery,
            onValueChange = viewModel::setArtistQuery,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = DIM, modifier = Modifier.size(18.dp)) },
            placeholder = { Text("Search artists…", color = DIM) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = PURPLE.copy(alpha = 0.5f),
                unfocusedBorderColor = Color(0x14FFFFFF),
                cursorColor = PURPLE,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().semantics { testTag = "artist_search" },
        )

        // Selected chips
        if (editor.artistIds.isNotEmpty()) {
            Text(
                "SELECTED (${editor.artistIds.size})",
                color = DIM,
                fontSize = textLabelS(),
                fontWeight = FontWeight.Bold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                editor.artistIds.forEach { id ->
                    val name = state.selectedArtistNames[id] ?: id
                    PickChip(
                        text = "$name ✕",
                        color = PURPLE,
                        selected = true,
                        onClick = { viewModel.toggleArtist(ArtistOption(id, name, false)) },
                        tag = "artist_chip_$id",
                    )
                }
            }
        }

        // Results
        if (editor.artistResults.isNotEmpty()) {
            Text("RESULTS", color = DIM, fontSize = textLabelS(), fontWeight = FontWeight.Bold)
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(ROW_BG)) {
                editor.artistResults.forEach { artist ->
                    ArtistResultRow(
                        artist = artist,
                        selected = artist.id in editor.artistIds,
                        onToggle = { viewModel.toggleArtist(artist) },
                    )
                }
            }
            if (!editor.artistResultsExhausted) {
                TextButton(
                    onClick = viewModel::loadMoreArtists,
                    modifier = Modifier.semantics { testTag = "artist_load_more" },
                ) {
                    Text("Load more artists", color = PURPLE)
                }
            }
        }
    }
}

@Composable
private fun ArtistResultRow(artist: ArtistOption, selected: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { testTag = "artist_result_${artist.id}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(PURPLE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            artist.name,
            color = Color.White,
            fontSize = textBodyM(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (artist.isFavorite) {
            Icon(Icons.Default.Star, "Favorite", tint = TEAL, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            if (selected) Icons.Default.Check else Icons.Default.Add,
            contentDescription = if (selected) "Remove" else "Add",
            tint = if (selected) TEAL else DIM,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SourceTab(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val bg = if (selected) color else ROW_BG
    val fg = if (selected) Color(0xFF101018) else MUTED
    Box(
        Modifier.weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 10.dp)
            .semantics { testTag = tag },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = textLabelM(), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PickChip(text: String, color: Color, selected: Boolean, onClick: () -> Unit, tag: String) {
    val bg = if (selected) color else ROW_BG
    val fg = if (selected) Color(0xFF101018) else Color(0xFFBBBBBB)
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { testTag = tag },
    ) {
        Text(text, color = fg, fontSize = textLabelM(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SuggestionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) TEAL else ROW_BG)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = if (selected) Color(0xFF101018) else Color(0xFFAAAAAA),
            fontSize = textLabelM(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
