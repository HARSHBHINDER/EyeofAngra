package com.eyeofangra.app.feature

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eyeofangra.app.MediaEntry
import com.eyeofangra.app.MediaLibrary
import com.eyeofangra.app.RecordingStore
import com.eyeofangra.app.ui.components.AudioIcon
import com.eyeofangra.app.ui.components.VideoIcon
import com.eyeofangra.app.ui.theme.Angra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Filter(val label: String, val prefixes: List<String>) {
    All("All", listOf("VID", "AUD", "IMG")),
    Videos("Video", listOf("VID")),
    Audio("Audio", listOf("AUD")),
    Photos("Photo", listOf("IMG")),
}

@Composable
fun VaultScreen(refreshKey: Any?) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(Filter.All) }
    var pendingDelete by remember { mutableStateOf<MediaEntry?>(null) }
    var exportChoice by remember { mutableStateOf<MediaEntry?>(null) }
    var exportTarget by remember { mutableStateOf<MediaEntry?>(null) }
    var exportMove by remember { mutableStateOf(false) }
    // Recomputed whenever a recording finishes or an item is deleted.
    var version by remember { mutableStateOf(0) }
    // A real mime type (not "*/*") so SAF keeps the right extension — an export
    // saved with the wrong extension plays back as corrupt even though the bytes
    // (already MP4/AAC from the recorder) are untouched.
    val onExportResult: (android.net.Uri?) -> Unit = { dest ->
        val target = exportTarget
        val move = exportMove
        exportTarget = null
        if (dest != null && target != null) {
            val ok = MediaLibrary.export(context, target, dest)
            if (ok && move) {
                MediaLibrary.delete(context, target)
                version++
            }
        }
    }
    val exportVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4"), onExportResult)
    val exportAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4"), onExportResult)
    val exportPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg"), onExportResult)
    fun launchExport(entry: MediaEntry) {
        exportTarget = entry
        when (MediaLibrary.mime(entry.name)) {
            "video/mp4" -> exportVideoLauncher.launch(entry.name)
            "audio/mp4" -> exportAudioLauncher.launch(entry.name)
            else -> exportPhotoLauncher.launch(entry.name)
        }
    }

    // Both locations: this app's storage and the folder chosen in Settings.
    val files = remember(filter, version, refreshKey) {
        MediaLibrary.list(context, filter.prefixes)
    }
    val used = remember(version, refreshKey) { MediaLibrary.usedBytes(context) }
    val free = remember(version, refreshKey) { RecordingStore.freeBytes(context) }

    Column(Modifier.fillMaxSize().background(Angra.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Angra.s4, end = Angra.s4, top = Angra.s5),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "Vault",
                Modifier.weight(1f),
                color = Angra.TextPrimary,
                fontSize = 30.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
            Text(RecordingStore.formatBytes(used), color = Angra.TextSecondary, fontSize = Angra.labelSize)
        }
        Text(
            "Held only on this device · ${RecordingStore.formatBytes(free)} free",
            Modifier.padding(horizontal = Angra.s4, vertical = Angra.s1),
            color = Angra.TextSecondary,
            fontSize = Angra.labelSize,
        )

        // Gold pill marks the active filter; the rest sit back on the surface tone.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Angra.s4, vertical = Angra.s3),
            horizontalArrangement = Arrangement.spacedBy(Angra.s2),
        ) {
            Filter.entries.forEach { f ->
                val selected = filter == f
                Text(
                    f.label,
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Angra.GoldGradient else SolidColor(Angra.SurfaceAlt))
                        .clickable { filter = f }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (selected) Angra.Background else Angra.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }

        if (files.isEmpty()) {
            Text(
                "Nothing captured yet.\nRecordings you make appear here, held only on this device.",
                Modifier.fillMaxWidth().padding(Angra.s7),
                color = Angra.TextSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "Tap to play · hold for export or delete",
                Modifier.padding(start = Angra.s4, bottom = Angra.s2),
                color = Angra.TextDisabled,
                fontSize = 11.sp,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(start = Angra.s4, end = Angra.s4, bottom = Angra.s5),
                horizontalArrangement = Arrangement.spacedBy(Angra.s3),
                verticalArrangement = Arrangement.spacedBy(Angra.s3),
            ) {
                items(files, key = { it.id }) { entry ->
                    Tile(
                        entry = entry,
                        onOpen = { MediaLibrary.open(context, entry) },
                        onExport = { exportChoice = entry },
                        onDelete = { pendingDelete = entry },
                    )
                }
            }
        }
    }

    // Deleting evidence is irreversible, so it always asks first.
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this recording?") },
            text = { Text("${entry.name} will be permanently removed from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    MediaLibrary.delete(context, entry)
                    pendingDelete = null
                    version++
                }) { Text("Delete", color = Angra.Recording) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
            containerColor = Angra.Surface,
        )
    }

    // Copy keeps the original in the vault; Move removes it once the export succeeds.
    exportChoice?.let { entry ->
        AlertDialog(
            onDismissRequest = { exportChoice = null },
            title = { Text("Export this recording?") },
            text = { Text("Pick a folder for ${entry.name}.") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        exportChoice = null
                        exportMove = false
                        launchExport(entry)
                    }) { Text("Copy") }
                    TextButton(onClick = {
                        exportChoice = null
                        exportMove = true
                        launchExport(entry)
                    }) { Text("Move", color = Angra.Recording) }
                }
            },
            dismissButton = {
                TextButton(onClick = { exportChoice = null }) { Text("Cancel") }
            },
            containerColor = Angra.Surface,
        )
    }
}

/// Photos show themselves; video and audio get a gold mark, which avoids decoding a
/// frame for every tile. A scrim keeps the size legible over any image.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(entry: MediaEntry, onOpen: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val kind = entry.name.take(3)
    var menu by remember { mutableStateOf(false) }
    val thumb by produceState<ImageBitmap?>(null, entry.id) {
        if (kind == "IMG") value = withContext(Dispatchers.IO) { loadThumb(context, entry) }
    }
    val shape = RoundedCornerShape(16.dp)

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Angra.CardGradient)
            .border(Angra.hairline, Angra.EdgeLight, shape)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
    ) {
        val image = thumb
        if (image != null) {
            Image(image, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.align(Alignment.Center).size(30.dp)) {
                if (kind == "AUD") AudioIcon(true, Angra.Gold) else VideoIcon(true, Angra.Gold)
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(40.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
        )
        Text(
            RecordingStore.formatBytes(entry.size),
            Modifier.align(Alignment.BottomStart).padding(8.dp),
            color = Angra.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        // Marks files written to the folder chosen in Settings, so "not in the app
        // folder" never reads as "lost".
        if (entry.inChosenFolder) {
            Text(
                "FOLDER",
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Angra.Gold,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text("Export") }, onClick = { menu = false; onExport() })
            DropdownMenuItem(text = { Text("Delete", color = Angra.Recording) }, onClick = { menu = false; onDelete() })
        }
    }
}

/// Decodes a photo at roughly tile size, so a grid of 12 MP images never loads
/// full-resolution bitmaps into memory.
private fun loadThumb(context: Context, entry: MediaEntry): ImageBitmap? = runCatching {
    fun open() = entry.file?.inputStream() ?: context.contentResolver.openInputStream(entry.docUri!!)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 320) sample *= 2
    open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?.asImageBitmap()
}.getOrNull()
