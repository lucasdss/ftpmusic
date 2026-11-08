package com.lucasdss.ftpmusic.app.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverPackOrder
import com.lucasdss.ftpmusic.app.data.cover.CoverStylePackPreference
import com.lucasdss.ftpmusic.app.data.cover.DefaultCollectionCoverCatalog
import com.lucasdss.ftpmusic.app.data.cover.UserCoverLibrary
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.spacing3XL
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingM
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.spacingXL
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingM
import com.lucasdss.ftpmusic.app.ui.textLabelS
import com.lucasdss.ftpmusic.app.ui.textMicro
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/**
 * Shared cover edit sheet: Defaults / Saved / albums / Device / Clear (ADR 0150 / 0151).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionCoverEditSheet(
    title: String,
    hasFixedCover: Boolean,
    subject: CollectionCoverSubject,
    collectionName: String,
    onPickLibrary: () -> Unit,
    onPickDevice: () -> Unit,
    onPickSaved: (relativePath: String) -> Unit,
    onPickBundled: (assetPath: String) -> Unit,
    onPickDerived: () -> Unit = {},
    onClear: () -> Unit,
    onSavedRemoved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deps = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CollectionCoverEditEntryPoint::class.java,
        )
    }
    val catalog = remember(context) { DefaultCollectionCoverCatalog(context.applicationContext) }
    var savedEntries by remember { mutableStateOf<List<UserCoverLibrary.Entry>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<UserCoverLibrary.Entry?>(null) }
    var deleteUsageCount by remember { mutableStateOf(0) }
    val activePack = remember { deps.coverStylePackPreference().current() }
    val packThumbs = remember(activePack, subject, collectionName) {
        CollectionCoverPackOrder.orderedThumbs(
            packs = catalog.packs(),
            activePackId = activePack,
            resolveAsset = { packId ->
                when (subject) {
                    CollectionCoverSubject.GENRE -> catalog.resolveGenre(packId, collectionName)?.assetPath
                    CollectionCoverSubject.MIX -> catalog.resolveMix(packId, collectionName)?.assetPath
                    CollectionCoverSubject.PLAYLIST -> catalog.resolvePlaylist(packId, collectionName)?.assetPath
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        savedEntries = deps.userCoverLibrary().list()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
        modifier = Modifier.semantics { testTag = "collection_cover_edit_sheet" },
    ) {
        Column(Modifier.padding(bottom = spacing3XL())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF444444)),
                )
            }
            Column(Modifier.padding(horizontal = spacingXL(), vertical = spacingM())) {
                FittingText(
                    text = title,
                    color = Color.White,
                    fontSize = textHeadingM(),
                    minFontSize = textMicro(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Cover art",
                    color = Color(0xFF888888),
                    fontSize = textBodyM(),
                )
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(horizontal = spacingL()),
            )

            if (packThumbs.isNotEmpty()) {
                Text(
                    "Style packs",
                    color = Color(0xFF888888),
                    fontSize = textLabelS(),
                    modifier = Modifier.padding(horizontal = spacingXL(), vertical = spacingS()),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacingXL()),
                    horizontalArrangement = Arrangement.spacedBy(spacingS()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacingM())
                        .semantics { testTag = "collection_cover_edit_defaults" },
                ) {
                    items(packThumbs, key = { it.packId }) { thumb ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = catalog.assetUri(thumb.assetPath),
                                contentDescription = thumb.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (thumb.packId == activePack) {
                                            BrandTeal
                                        } else {
                                            BrandTeal.copy(alpha = 0.4f)
                                        },
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { onPickBundled(thumb.assetPath) },
                            )
                            Text(
                                thumb.label,
                                color = Color(0xFFAAAAAA),
                                fontSize = textMicro(),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                // Opt-in track collage for mixes/playlists (DERIVED fixed kind).
                if (subject == CollectionCoverSubject.MIX || subject == CollectionCoverSubject.PLAYLIST) {
                    TextButton(
                        onClick = onPickDerived,
                        modifier = Modifier.padding(horizontal = spacingXL()),
                    ) {
                        Text("Track collage", color = BrandTeal)
                    }
                }
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.padding(horizontal = spacingL()),
                )
            }

            if (savedEntries.isNotEmpty()) {
                Text(
                    "Saved",
                    color = Color(0xFF888888),
                    fontSize = textLabelS(),
                    modifier = Modifier.padding(horizontal = spacingXL(), vertical = spacingS()),
                )
                Text(
                    "Tap to use · long-press to remove",
                    color = Color(0xFF666666),
                    fontSize = textMicro(),
                    modifier = Modifier.padding(horizontal = spacingXL()).padding(bottom = spacingS()),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacingXL()),
                    horizontalArrangement = Arrangement.spacedBy(spacingS()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacingM())
                        .semantics { testTag = "collection_cover_edit_saved" },
                ) {
                    items(savedEntries, key = { it.id }) { entry ->
                        AsyncImage(
                            model = entry.absolutePath,
                            contentDescription = "Saved cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { onPickSaved(entry.relativePath) },
                                    onLongClick = {
                                        scope.launch {
                                            deleteUsageCount =
                                                deps.userCoverLibrary().pointerCount(entry.relativePath)
                                            pendingDelete = entry
                                        }
                                    },
                                ),
                        )
                    }
                }
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.padding(horizontal = spacingL()),
                )
            }

            CoverEditAction(
                label = "From albums",
                icon = Icons.Default.PhotoLibrary,
                color = BrandTeal,
                testTag = "collection_cover_edit_library",
                onClick = onPickLibrary,
            )
            CoverEditAction(
                label = "From Device",
                icon = Icons.Default.Image,
                color = BrandTeal,
                testTag = "collection_cover_edit_device",
                onClick = onPickDevice,
            )
            if (hasFixedCover) {
                CoverEditAction(
                    label = "Clear cover",
                    icon = Icons.Default.Clear,
                    color = Color(0xFFE84040),
                    testTag = "collection_cover_edit_clear",
                    onClick = onClear,
                )
            }
        }
    }

    pendingDelete?.let { entry ->
        val usage = deleteUsageCount
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove saved image?") },
            text = {
                Text(
                    if (usage > 0) {
                        "Used by $usage collection${if (usage == 1) "" else "s"}. " +
                            "Those covers will clear; the image will be deleted permanently."
                    } else {
                        "This image will be deleted permanently. Collections keep other covers."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            deps.userCoverLibrary().deleteAndClearPointers(entry.id)
                            savedEntries = deps.userCoverLibrary().list()
                            pendingDelete = null
                            onSavedRemoved()
                        }
                    },
                ) { Text("Remove", color = Color(0xFFE84040)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CoverEditAction(label: String, icon: ImageVector, color: Color, testTag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = spacingL())
            .semantics { this.testTag = testTag },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = textBodyM(), fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = spacingL()))
}

/**
 * Auto-open sheet only when crop/picker not mid-flight.
 * Rotation restores [pendingCropUri]; [LaunchedEffect] must not re-open sheet on top.
 */
fun canAutoOpenCoverEditSheet(hasPendingCrop: Boolean, showAlbumPicker: Boolean): Boolean =
    !hasPendingCrop && !showAlbumPicker

/** Controller for long-press / button cover edit on one collection. */
class CollectionCoverEditController(
    val openSheet: () -> Unit,
    val openLibrary: () -> Unit,
    val openDevice: () -> Unit,
    val clear: () -> Unit,
)

/**
 * Remembers picker/crop/sheet state and renders overlays.
 * Call from any screen; use [CollectionCoverEditController] for buttons / long-press.
 */
@Composable
fun rememberCollectionCoverEditController(
    title: String,
    hasFixedCover: Boolean,
    onLibraryCover: (coverArtId: String) -> Unit,
    onCroppedBitmap: (Bitmap) -> Unit,
    onClear: () -> Unit,
    onLoadFailed: () -> Unit,
    onExportFailed: () -> Unit,
    onIdleDismiss: () -> Unit = {},
    subject: CollectionCoverSubject = CollectionCoverSubject.MIX,
    collectionName: String = title,
    onSavedCover: (relativePath: String) -> Unit = {},
    onBundledCover: (assetPath: String) -> Unit = {},
    onDerivedCover: () -> Unit = {},
    onSavedRemoved: () -> Unit = {},
): CollectionCoverEditController {
    val coverStore = rememberCollectionCoverStore()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showAlbumPicker by rememberSaveable { mutableStateOf(false) }
    var pendingCropUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pendingCropUri = uri
        } else {
            onIdleDismiss()
        }
    }

    if (showSheet) {
        CollectionCoverEditSheet(
            title = title,
            hasFixedCover = hasFixedCover,
            subject = subject,
            collectionName = collectionName,
            onPickLibrary = {
                showSheet = false
                showAlbumPicker = true
            },
            onPickDevice = {
                showSheet = false
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onPickSaved = { path ->
                showSheet = false
                onSavedCover(path)
            },
            onPickBundled = { path ->
                showSheet = false
                onBundledCover(path)
            },
            onPickDerived = {
                showSheet = false
                onDerivedCover()
            },
            onClear = {
                showSheet = false
                onClear()
            },
            onSavedRemoved = onSavedRemoved,
            onDismiss = {
                showSheet = false
                onIdleDismiss()
            },
        )
    }

    if (showAlbumPicker) {
        AlbumCoverPickerSheet(
            onPicked = { coverArtId ->
                showAlbumPicker = false
                onLibraryCover(coverArtId)
            },
            onDismiss = {
                showAlbumPicker = false
                onIdleDismiss()
            },
        )
    }

    pendingCropUri?.let { cropUri ->
        CoverCropDialog(
            uri = cropUri,
            coverStore = coverStore,
            onConfirm = { cropped ->
                pendingCropUri = null
                onCroppedBitmap(cropped)
            },
            onDismiss = {
                pendingCropUri = null
                onIdleDismiss()
            },
            onLoadFailed = {
                pendingCropUri = null
                onLoadFailed()
            },
            onExportFailed = {
                pendingCropUri = null
                onExportFailed()
            },
        )
    }

    return CollectionCoverEditController(
        openSheet = {
            if (canAutoOpenCoverEditSheet(
                    hasPendingCrop = pendingCropUri != null,
                    showAlbumPicker = showAlbumPicker,
                )
            ) {
                showSheet = true
            }
        },
        openLibrary = { showAlbumPicker = true },
        openDevice = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        clear = onClear,
    )
}

/**
 * Host that renders [content] with a [CollectionCoverEditController] receiver.
 */
@Composable
fun CollectionCoverEditHost(
    title: String,
    hasFixedCover: Boolean,
    onLibraryCover: (coverArtId: String) -> Unit,
    onCroppedBitmap: (Bitmap) -> Unit,
    onClear: () -> Unit,
    onLoadFailed: () -> Unit,
    onExportFailed: () -> Unit,
    subject: CollectionCoverSubject = CollectionCoverSubject.MIX,
    collectionName: String = title,
    onSavedCover: (relativePath: String) -> Unit = {},
    onBundledCover: (assetPath: String) -> Unit = {},
    onDerivedCover: () -> Unit = {},
    onSavedRemoved: () -> Unit = {},
    content: @Composable CollectionCoverEditController.() -> Unit,
) {
    val controller = rememberCollectionCoverEditController(
        title = title,
        hasFixedCover = hasFixedCover,
        onLibraryCover = onLibraryCover,
        onCroppedBitmap = onCroppedBitmap,
        onClear = onClear,
        onLoadFailed = onLoadFailed,
        onExportFailed = onExportFailed,
        onIdleDismiss = {},
        subject = subject,
        collectionName = collectionName,
        onSavedCover = onSavedCover,
        onBundledCover = onBundledCover,
        onDerivedCover = onDerivedCover,
        onSavedRemoved = onSavedRemoved,
    )
    controller.content()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CollectionCoverEditEntryPoint {
    fun userCoverLibrary(): UserCoverLibrary
    fun coverStylePackPreference(): CoverStylePackPreference
}
