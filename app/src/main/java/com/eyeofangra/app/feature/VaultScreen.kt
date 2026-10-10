package com.eyeofangra.app.feature

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import com.eyeofangra.app.Exporter
import com.eyeofangra.app.MediaEntry
import com.eyeofangra.app.MediaLibrary
import com.eyeofangra.app.RecordingStore
import com.eyeofangra.app.ui.components.AudioIcon
import com.eyeofangra.app.ui.components.VideoIcon
import com.eyeofangra.app.ui.theme.Angra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
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
    // Selection mode: tap toggles, the action bar acts on every ticked item.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    // Tiles per row, set by pinching; 3 matches the Photos default.
    var columns by rememberSaveable { mutableIntStateOf(3) }
    // App-wide, so leaving the Vault mid-export and coming back shows the bar again.
    val status by Exporter.status.collectAsState()
    val notice by Exporter.notice.collectAsState()
    val exportChanges by Exporter.changes.collectAsState()

    val files = remember(filter, version, refreshKey, exportChanges) {
        MediaLibrary.list(context, filter.prefixes)
    }
    val used = remember(version, refreshKey, exportChanges) { MediaLibrary.usedBytes(context) }
    val free = remember(version, refreshKey, exportChanges) { RecordingStore.freeBytes(context) }
    val chosen = files.filter { it.id in selected }
    fun endSelection() { selecting = false; selected = emptySet() }

    // One folder pick for the whole batch; each file keeps its own name and type.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            Exporter.start(context, chosen, tree, pendingMove)
            endSelection()
        }
    }

    Column(Modifier.fillMaxSize().background(Angra.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Angra.s4, end = Angra.s2, top = Angra.s5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (selecting) "${selected.size} selected" else "Vault",
                Modifier.weight(1f),
                color = Angra.TextPrimary,
                fontSize = if (selecting) 22.sp else 30.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
            if (selecting) {
                TextButton(onClick = {
                    selected = if (selected.size == files.size) emptySet() else files.map { it.id }.toSet()
                }) { Text(if (selected.size == files.size) "None" else "All", color = Angra.Gold) }
                TextButton(onClick = { endSelection() }) { Text("Cancel", color = Angra.TextSecondary) }
            } else if (files.isNotEmpty()) {
                TextButton(onClick = { selecting = true }) { Text("Select", color = Angra.Gold) }
            }
        }
        Text(
            "${RecordingStore.formatBytes(used)} · held only on this device · ${RecordingStore.formatBytes(free)} free",
            Modifier.padding(horizontal = Angra.s4),
            color = Angra.TextSecondary,
            fontSize = Angra.labelSize,
        )

        status?.let { st ->
            Column(Modifier.fillMaxWidth().padding(horizontal = Angra.s4, vertical = Angra.s2)) {
                Text(
                    "Exporting ${st.index} of ${st.count} · ${st.percent}%",
                    color = Angra.Gold,
                    fontSize = Angra.labelSize,
                )
                LinearProgressIndicator(
                    progress = { st.percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = Angra.s1),
                    color = Angra.Gold,
                    trackColor = Angra.SurfaceAlt,
                )
            }
        }
        notice?.let { msg ->
            LaunchedEffect(msg) { delay(3_000); Exporter.notice.value = null }
            Text(msg, Modifier.padding(horizontal = Angra.s4, vertical = Angra.s1), color = Angra.Gold, fontSize = Angra.labelSize)
        }

        // Gold pill marks the active filter; the rest sit back on the surface tone.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Angra.s4, vertical = Angra.s3),
            horizontalArrangement = Arrangement.spacedBy(Angra.s2),
        ) {
            Filter.entries.forEach { f ->
                val on = filter == f
                Text(
                    f.label,
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Angra.GoldGradient else SolidColor(Angra.SurfaceAlt))
                        .clickable { filter = f; selected = emptySet() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (on) Angra.Background else Angra.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
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
            if (!selecting) {
                Text(
                    "Tap to play · hold and drag to select · pinch to resize",
                    Modifier.padding(start = Angra.s4, bottom = Angra.s2),
                    color = Angra.TextDisabled,
                    fontSize = 11.sp,
                )
            }
            val gridState = rememberLazyGridState()
            var autoScroll by remember { mutableFloatStateOf(0f) }
            // While a drag-select sits at the top or bottom edge, keep scrolling.
            LaunchedEffect(autoScroll) {
                while (autoScroll != 0f) {
                    gridState.scrollBy(autoScroll)
                    delay(10)
                }
            }
            val ids = files.map { it.id }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .pinchColumns(columns) { columns = it }
                    .dragSelect(
                        state = gridState,
                        ids = ids,
                        current = { selected },
                        onSelect = { selecting = true; selected = it },
                        onAutoScroll = { autoScroll = it },
                    ),
                contentPadding = PaddingValues(bottom = Angra.s5),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(files, key = { it.id }) { entry ->
                    val on = entry.id in selected
                    Tile(
                        entry = entry,
                        columns = columns,
                        selecting = selecting,
                        selected = on,
                        onClick = {
                            if (selecting) selected = if (on) selected - entry.id else selected + entry.id
                            else MediaLibrary.open(context, entry)
                        },
                    )
                }
            }
        }

        if (selecting) {
            val enabled = chosen.isNotEmpty() && status == null
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Angra.Surface)
                    .padding(horizontal = Angra.s2, vertical = Angra.s1),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(enabled = enabled, onClick = { pendingMove = false; pickFolder.launch(null) }) {
                    Text("Copy to folder", color = if (enabled) Angra.Gold else Angra.TextDisabled)
                }
                TextButton(enabled = enabled, onClick = { pendingMove = true; pickFolder.launch(null) }) {
                    Text("Move to folder", color = if (enabled) Angra.Gold else Angra.TextDisabled)
                }
                TextButton(enabled = enabled, onClick = { confirmDelete = true }) {
                    Text("Delete", color = if (enabled) Angra.Recording else Angra.TextDisabled)
                }
            }
        }
    }

    // Deleting evidence is irreversible, so it always asks first.
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${chosen.size} ${if (chosen.size == 1) "item" else "items"}?") },
            text = { Text("They will be permanently removed from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    chosen.forEach { MediaLibrary.delete(context, it) }
                    confirmDelete = false
                    endSelection()
                    version++
                }) { Text("Delete", color = Angra.Recording) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
            containerColor = Angra.Surface,
        )
    }
}

/// Two-finger pinch steps the column count, like Photos: spread for bigger tiles,
/// pinch for more of them. One finger is left alone, so scrolling still works.
private fun Modifier.pinchColumns(columns: Int, onChange: (Int) -> Unit) = pointerInput(columns) {
    awaitEachGesture {
        var zoom = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                if (zoom > 1.3f && columns > 1) { onChange(columns - 1); return@awaitEachGesture }
                if (zoom < 0.77f && columns < 7) { onChange(columns + 1); return@awaitEachGesture }
            }
        } while (event.changes.any { it.pressed })
    }
}

/// Hold a tile, then slide: everything between the first tile and the one under
/// the finger is selected, as in Photos. Near the top or bottom edge the grid
/// scrolls on its own so a long run can be swept in one gesture.
private fun Modifier.dragSelect(
    state: LazyGridState,
    ids: List<String>,
    current: () -> Set<String>,
    onSelect: (Set<String>) -> Unit,
    onAutoScroll: (Float) -> Unit,
) = pointerInput(ids) {
    var anchor = -1
    var base = emptySet<String>()
    val edge = 72.dp.toPx()
    detectDragGesturesAfterLongPress(
        onDragStart = { at ->
            state.indexAt(at)?.let {
                anchor = it
                base = current()
                onSelect(base + ids[it])
            }
        },
        onDrag = { change, _ ->
            if (anchor >= 0) {
                change.consume()
                val y = change.position.y
                val height = state.layoutInfo.viewportSize.height
                onAutoScroll(
                    when {
                        y > height - edge -> 20f
                        y < edge -> -20f
                        else -> 0f
                    },
                )
                state.indexAt(change.position)?.let { i ->
                    val range = if (i >= anchor) anchor..i else i..anchor
                    onSelect(base + range.map { ids[it] })
                }
            }
        },
        onDragEnd = { anchor = -1; onAutoScroll(0f) },
        onDragCancel = { anchor = -1; onAutoScroll(0f) },
    )
}

private fun LazyGridState.indexAt(at: Offset): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull { IntRect(it.offset, it.size).contains(at.round()) }?.index

/// A Photos-style square: edge to edge, no card chrome. Length sits bottom-right;
/// the capture time only shows when tiles are big enough to read it.
@Composable
private fun Tile(entry: MediaEntry, columns: Int, selecting: Boolean, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val kind = entry.name.take(3)
    val roomy = columns <= 3
    // Bigger tiles get a sharper decode; small ones stay cheap.
    val target = if (columns <= 2) 720 else 360
    val info by produceState(TileInfo(null, null), entry.id, target) {
        value = withContext(Dispatchers.IO) { loadInfo(context, entry, kind, target) }
    }

    Box(
        Modifier
            .aspectRatio(1f)
            .background(Angra.Surface)
            .clickable(onClick = onClick),
    ) {
        val image = info.thumb
        if (image != null) {
            Image(image, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.align(Alignment.Center).size(if (roomy) 30.dp else 20.dp)) {
                if (kind == "AUD") AudioIcon(true, Angra.Gold) else VideoIcon(true, Angra.Gold)
            }
        }
        if (info.durationMs != null || roomy) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(if (roomy) 36.dp else 22.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))),
            )
        }
        if (roomy) {
            Text(
                capturedAt(entry),
                Modifier.align(Alignment.BottomStart).padding(5.dp),
                color = Angra.TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        info.durationMs?.let { ms ->
            Text(
                formatDuration(ms),
                Modifier.align(Alignment.BottomEnd).padding(5.dp),
                color = Color.White,
                fontSize = if (roomy) 11.sp else 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // A small gold dot marks files living in the folder chosen in Settings.
        if (entry.inChosenFolder && !selecting) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Angra.Gold),
            )
        }
        if (selecting) {
            if (selected) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.18f)))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(if (roomy) 22.dp else 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Angra.GoldGradient else SolidColor(Color.Black.copy(alpha = 0.3f)))
                    .border(1.5.dp, Color.White, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("\u2713", color = Angra.Background, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/// Decodes a photo at roughly tile size, so a grid of 12 MP images never loads
/// full-resolution bitmaps into memory.
private fun loadThumb(context: Context, entry: MediaEntry, target: Int): ImageBitmap? = runCatching {
    fun open() = entry.file?.inputStream() ?: context.contentResolver.openInputStream(entry.docUri!!)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target) sample *= 2
    open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?.asImageBitmap()
}.getOrNull()

private class TileInfo(val thumb: ImageBitmap?, val durationMs: Long?)

/// Photos decode a small thumbnail; video takes a frame one second in (past any
/// black first frame) plus its length; audio gets its length only.
private fun loadInfo(context: Context, entry: MediaEntry, kind: String, target: Int): TileInfo {
    if (kind == "IMG") return TileInfo(loadThumb(context, entry, target), null)
    val r = MediaMetadataRetriever()
    return try {
        val file = entry.file
        if (file != null) r.setDataSource(file.absolutePath) else r.setDataSource(context, entry.docUri)
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        val frame = if (kind != "VID") null
        else if (Build.VERSION.SDK_INT >= 27) {
            r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, target, target)
        } else {
            r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                val scale = target.toFloat() / maxOf(it.width, it.height)
                android.graphics.Bitmap.createScaledBitmap(it, (it.width * scale).toInt(), (it.height * scale).toInt(), true)
            }
        }
        TileInfo(frame?.asImageBitmap(), ms)
    } catch (e: Exception) {
        TileInfo(null, null)
    } finally {
        runCatching { r.release() }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/// "25 Aug · 14:09" from the VID_yyyyMMdd_HHmmss name, falling back to the file time.
private fun capturedAt(entry: MediaEntry): String {
    val out = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())
    val raw = entry.name.substringAfter('_').substringBeforeLast('.').take(15)
    return runCatching { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(raw) }.getOrNull()
        ?.let { out.format(it) } ?: out.format(java.util.Date(entry.modified))
}
