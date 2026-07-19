package com.eyeofangra.app.feature

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
import com.eyeofangra.app.RecordingStore
import com.eyeofangra.app.ui.theme.Angra
import java.io.File
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
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    // Recomputed whenever a recording finishes or an item is deleted.
    var version by remember { mutableStateOf(0) }

    val files = remember(filter, version, refreshKey) {
        filter.prefixes.flatMap { RecordingStore.list(context, it) }
            .sortedByDescending { it.name.substringAfter('_') }
    }
    val used = remember(version, refreshKey) { RecordingStore.usedBytes(context) }
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
                items(files, key = { it.absolutePath }) { file ->
                    MediaRow(
                        file = file,
                        onOpen = { RecordingStore.open(context, file) },
                        onDelete = { pendingDelete = file },
                    )
                }
            }
        }
    }

    // Deleting evidence is irreversible, so it always asks first.
    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this recording?") },
            text = { Text("${file.name} will be permanently removed from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    file.delete()
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
}

@Composable
private fun MediaRow(file: File, onOpen: () -> Unit, onDelete: () -> Unit) {
    val kind = when (file.name.take(3)) {
        "VID" -> "Video"
        "AUD" -> "Audio"
        else -> "Photo"
    }
    val captured = remember(file) {
        runCatching {
            val raw = file.name.substringAfter('_').substringBeforeLast('.').take(15)
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(raw)
                ?.let { SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault()).format(it) }
        }.getOrNull() ?: SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
            .format(Date(file.lastModified()))
    }

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
            Text("$kind · ${RecordingStore.formatBytes(file.length())}", color = Angra.TextPrimary, fontSize = Angra.bodySize)
            Text(captured, color = Angra.TextSecondary, fontSize = Angra.labelSize)
        }
        TextButton(onClick = onDelete) { Text("Delete", color = Angra.TextSecondary) }
    }
}
