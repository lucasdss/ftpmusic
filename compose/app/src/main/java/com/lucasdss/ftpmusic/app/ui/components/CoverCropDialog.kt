package com.lucasdss.ftpmusic.app.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverStore
import com.lucasdss.ftpmusic.app.ui.BrandBg
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelM
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoverCropStoreEntryPoint {
    fun collectionCoverStore(): CollectionCoverStore
}

@Composable
fun rememberCollectionCoverStore(): CollectionCoverStore {
    val context = LocalContext.current
    return remember {
        val app = context.applicationContext
        try {
            EntryPointAccessors.fromApplication(
                app,
                CoverCropStoreEntryPoint::class.java,
            ).collectionCoverStore()
        } catch (_: IllegalStateException) {
            // Robolectric / non-Hilt Compose tests — construct store directly.
            CollectionCoverStore(app)
        }
    }
}

/**
 * Square pan/zoom crop dialog after Device gallery pick (ADR 0125).
 * Cancel discards; Use returns cropped square [Bitmap] (caller owns recycle).
 */
@Composable
fun CoverCropDialog(
    uri: Uri,
    coverStore: CollectionCoverStore,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
    onLoadFailed: () -> Unit = {},
    onExportFailed: () -> Unit = onLoadFailed,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var scale by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var viewportPx by rememberSaveable { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // Ownership ref so dismiss / cancel-before-assign can recycle without double-free.
    val ownedDecode = remember { arrayOf<Bitmap?>(null) }

    val onLoadFailedState = rememberUpdatedState(onLoadFailed)

    fun releaseOwnedDecode() {
        ownedDecode[0]?.recycle()
        ownedDecode[0] = null
        bitmap = null
    }

    LaunchedEffect(uri) {
        loading = true
        scale = 0f
        offsetX = 0f
        offsetY = 0f
        releaseOwnedDecode()
        var decoded: Bitmap? = null
        try {
            decoded = coverStore.decodeForCrop(uri)
            ensureActive()
            if (decoded == null) {
                loading = false
                // Single signal — caller maps load-fail to dismiss/toast.
                onLoadFailedState.value()
            } else {
                ownedDecode[0] = decoded
                bitmap = decoded
                decoded = null
                loading = false
            }
        } finally {
            decoded?.recycle()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ownedDecode[0]?.recycle()
            ownedDecode[0] = null
        }
    }

    Dialog(
        onDismissRequest = {
            if (!exporting) {
                releaseOwnedDecode()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.96f)
                .clip(RoundedCornerShape(20.dp))
                .background(BrandBg)
                .padding(16.dp)
                .semantics { testTag = "cover_crop_dialog" },
        ) {
            Text(
                "Adjust cover",
                color = Color.White,
                fontSize = textHeadingS(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Drag to frame · pinch optional",
                color = NavUnselected,
                fontSize = textBodyM(),
            )
            Spacer(Modifier.height(8.dp))
            val bmpForZoom = bitmap
            if (bmpForZoom != null && scale > 0f && viewportPx > 0f) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            val next = CoverCropMath.clampScale(
                                scale / 1.25f,
                                bmpForZoom.width.toFloat(),
                                bmpForZoom.height.toFloat(),
                                viewportPx,
                            )
                            val (nx, ny) = CoverCropMath.clampOffset(
                                next,
                                offsetX,
                                offsetY,
                                bmpForZoom.width.toFloat(),
                                bmpForZoom.height.toFloat(),
                                viewportPx,
                            )
                            scale = next
                            offsetX = nx
                            offsetY = ny
                        },
                        enabled = !exporting,
                        modifier = Modifier
                            .height(48.dp)
                            .semantics { testTag = "cover_crop_zoom_out" },
                    ) {
                        Text("Zoom −", color = BrandTeal, fontSize = textLabelM())
                    }
                    TextButton(
                        onClick = {
                            val next = CoverCropMath.clampScale(
                                scale * 1.25f,
                                bmpForZoom.width.toFloat(),
                                bmpForZoom.height.toFloat(),
                                viewportPx,
                            )
                            val (nx, ny) = CoverCropMath.clampOffset(
                                next,
                                offsetX,
                                offsetY,
                                bmpForZoom.width.toFloat(),
                                bmpForZoom.height.toFloat(),
                                viewportPx,
                            )
                            scale = next
                            offsetX = nx
                            offsetY = ny
                        },
                        enabled = !exporting,
                        modifier = Modifier
                            .height(48.dp)
                            .semantics { testTag = "cover_crop_zoom_in" },
                    ) {
                        Text("Zoom +", color = BrandTeal, fontSize = textLabelM())
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A1A))
                    .semantics {
                        testTag = "cover_crop_viewport"
                        contentDescription = "Cover crop. Drag to frame, pinch optional."
                    },
            ) {
                val vp = with(LocalDensity.current) { maxWidth.toPx() }
                val bmp = bitmap
                LaunchedEffect(vp, bmp) {
                    if (bmp != null && vp > 0f) {
                        viewportPx = vp
                        if (scale <= 0f) {
                            scale = CoverCropMath.minScale(
                                bmp.width.toFloat(),
                                bmp.height.toFloat(),
                                vp,
                            )
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                when {
                    loading || bmp == null || scale <= 0f -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrandTeal, modifier = Modifier.size(36.dp))
                        }
                    }

                    else -> {
                        CoverCropViewport(
                            bitmap = bmp,
                            viewportPx = vp,
                            enabled = !exporting,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            onScaleChange = { scale = it },
                            onOffsetChange = { x, y ->
                                offsetX = x
                                offsetY = y
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = {
                        if (!exporting) {
                            bitmap?.recycle()
                            onDismiss()
                        }
                    },
                    enabled = !exporting,
                    modifier = Modifier
                        .height(48.dp)
                        .semantics { testTag = "cover_crop_cancel" },
                ) {
                    Text("Cancel", color = NavUnselected, fontSize = textLabelM())
                }
                TextButton(
                    onClick = {
                        val src = bitmap ?: return@TextButton
                        if (exporting || scale <= 0f || viewportPx <= 0f) return@TextButton
                        exporting = true
                        val exportScale = scale
                        val exportOx = offsetX
                        val exportOy = offsetY
                        val exportVp = viewportPx
                        scope.launch {
                            val cropped = withContext(Dispatchers.Default) {
                                exportCroppedBitmap(src, exportVp, exportScale, exportOx, exportOy)
                            }
                            exporting = false
                            if (cropped != null) {
                                ownedDecode[0] = null
                                src.recycle()
                                bitmap = null
                                onConfirm(cropped)
                            } else {
                                ownedDecode[0] = null
                                src.recycle()
                                bitmap = null
                                // Single signal — caller maps export-fail to dismiss/toast.
                                onExportFailed()
                            }
                        }
                    },
                    enabled = !exporting && bitmap != null && scale > 0f && viewportPx > 0f,
                    modifier = Modifier
                        .height(48.dp)
                        .semantics { testTag = "cover_crop_use" },
                ) {
                    Text(
                        if (exporting) "Saving…" else "Use",
                        color = BrandTeal,
                        fontSize = textLabelM(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverCropViewport(
    bitmap: Bitmap,
    viewportPx: Float,
    enabled: Boolean,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
) {
    val imageW = bitmap.width.toFloat()
    val imageH = bitmap.height.toFloat()
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val scaleState = rememberUpdatedState(scale)
    val offsetXState = rememberUpdatedState(offsetX)
    val offsetYState = rememberUpdatedState(offsetY)

    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (enabled) {
                    Modifier.pointerInput(imageW, imageH, viewportPx) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = CoverCropMath.clampScale(
                                scaleState.value * zoom,
                                imageW,
                                imageH,
                                viewportPx,
                            )
                            val (nx, ny) = CoverCropMath.clampOffset(
                                nextScale,
                                offsetXState.value + pan.x,
                                offsetYState.value + pan.y,
                                imageW,
                                imageH,
                                viewportPx,
                            )
                            onScaleChange(nextScale)
                            onOffsetChange(nx, ny)
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dispW = imageW * scale
            val dispH = imageH * scale
            val left = size.width / 2f + offsetX - dispW / 2f
            val top = size.height / 2f + offsetY - dispH / 2f
            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(
                    dispW.roundToInt().coerceAtLeast(1),
                    dispH.roundToInt().coerceAtLeast(1),
                ),
            )
        }
    }
}

internal fun exportCroppedBitmap(
    source: Bitmap,
    viewportPx: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): Bitmap? {
    val rect = CoverCropMath.sourceCropRect(
        source.width,
        source.height,
        viewportPx,
        scale,
        offsetX,
        offsetY,
    )
    if (rect.size <= 0) return null
    return try {
        Bitmap.createBitmap(source, rect.left, rect.top, rect.size, rect.size)
    } catch (_: Exception) {
        null
    }
}
