package com.lucasdss.ftpmusic.app.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.spacing3XL
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingM
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.spacingXL
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingM
import com.lucasdss.ftpmusic.app.ui.textMicro

/**
 * Shared Library / Device / Clear sheet for collection cover edit (ADR 0150).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionCoverEditSheet(
    title: String,
    hasFixedCover: Boolean,
    onPickLibrary: () -> Unit,
    onPickDevice: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            CoverEditAction(
                label = "From Library",
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
}

@Composable
private fun CoverEditAction(
    label: String,
    icon: ImageVector,
    color: Color,
    testTag: String,
    onClick: () -> Unit,
) {
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
): CollectionCoverEditController {
    val coverStore = rememberCollectionCoverStore()
    var showSheet by remember { mutableStateOf(false) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

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
            onClear = {
                showSheet = false
                onClear()
            },
            onDismiss = {
                // Swipe/back only — not an action selection.
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
        openSheet = { showSheet = true },
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
    )
    controller.content()
}
