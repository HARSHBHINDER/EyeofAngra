package com.eyeofangra.app.feature

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.eyeofangra.app.MediaEntry
import com.eyeofangra.app.MediaLibrary
import com.eyeofangra.app.RecordingStore
import com.eyeofangra.app.ui.theme.Angra
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Filter(val label: String, val prefixes: List<String>) {
    All("All", listOf("VID", "AUD", "IMG")),
    Videos("Videos", listOf("VID")),
    Audio("Audio", listOf("AUD")),
    Photos("Photos", listOf("IMG")),
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
        Text(
            "Vault",
            Modifier.padding(start = Angra.s4, top = Angra.s5, bottom = Angra.s2),
            color = Angra.TextPrimary,
            fontSize = Angra.titleSize,
        )
        Text(
            "${RecordingStore.formatBytes(used)} captured · ${RecordingStore.formatBytes(free)} free · stored only on this device",
            Modifier.padding(horizontal = Angra.s4),
            color = Angra.TextSecondary,
            fontSize = Angra.labelSize,
        )

        Row(
            Modifier.fillMaxWidth().padding(Angra.s4),
            horizontalArrangement = Arrangement.spacedBy(Angra.s2),
        ) {
            Filter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Angra.Gold,
                        selectedLabelColor = Angra.Background,
                        labelColor = Angra.TextSecondary,
                        containerColor = Angra.SurfaceAlt,
                    ),
                )
            }
        }

        if (files.isEmpty()) {
            Text(
                "Nothing captured yet.\nRecordings you make will be listed here.",
                Modifier.fillMaxWidth().padding(Angra.s7),
                color = Angra.TextSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Angra.s4),
                verticalArrangement = Arrangement.spacedBy(Angra.s2),
            ) {
                items(files, key = { it.id }) { entry ->
                    MediaRow(
                        entry = entry,
                        onOpen = { MediaLibrary.open(context, entry) },
                        onDelete = { pendingDelete = entry },
                        onExport = { exportChoice = entry },
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

@Composable
private fun MediaRow(entry: MediaEntry, onOpen: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit) {
    val kind = when (entry.name.take(3)) {
        "VID" -> "Video"
        "AUD" -> "Audio"
        else -> "Photo"
    }
    val captured = remember(entry.id) {
        runCatching {
            val raw = entry.name.substringAfter('_').substringBeforeLast('.').take(15)
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(raw)
                ?.let { SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault()).format(it) }
        }.getOrNull() ?: SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
            .format(Date(entry.modified))
    }
    // Says which storage the file is in, so "chosen folder" never means "lost".
    val where = if (entry.inChosenFolder) "chosen folder" else "on board"

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Angra.radiusSm))
            .background(Angra.Surface)
            .clickable(onClick = onOpen)
            .heightIn(min = Angra.touchTarget)
            .padding(horizontal = Angra.s4, vertical = Angra.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$kind · ${RecordingStore.formatBytes(entry.size)}", color = Angra.TextPrimary, fontSize = Angra.bodySize)
            Text("$captured · $where", color = Angra.TextSecondary, fontSize = Angra.labelSize)
        }
        TextButton(onClick = onExport) { Text("Export", color = Angra.TextSecondary) }
        TextButton(onClick = onDelete) { Text("Delete", color = Angra.TextSecondary) }
    }
}
