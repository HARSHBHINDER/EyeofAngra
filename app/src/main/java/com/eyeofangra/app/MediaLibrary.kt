package com.eyeofangra.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/// One capture, wherever it lives — this app's private storage or the folder the
/// user chose. The Vault shows both, so turning the storage toggle on never makes
/// earlier evidence look as though it vanished.
data class MediaEntry(
    val name: String,
    val size: Long,
    val modified: Long,
    val inChosenFolder: Boolean,
    val file: File? = null,
    val docUri: Uri? = null,
) {
    val id: String get() = file?.absolutePath ?: docUri.toString()
}

object MediaLibrary {

    fun mime(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4" -> "video/mp4"
        "m4a" -> "audio/mp4"
        else -> "image/jpeg"
    }

    /// Every capture matching [prefixes], newest first, from both locations.
    fun list(context: Context, prefixes: List<String>): List<MediaEntry> {
        val internal = prefixes.flatMap { RecordingStore.list(context, it) }.map {
            MediaEntry(it.name, it.length(), it.lastModified(), inChosenFolder = false, file = it)
        }
        return (internal + chosen(context, prefixes))
            .sortedByDescending { it.name.substringAfter('_') }
    }

    /// ponytail: DocumentFile.listFiles() is a full directory query; fine for a vault
    /// screen, swap for a paged content-resolver query if folders grow to thousands.
    private fun chosen(context: Context, prefixes: List<String>): List<MediaEntry> {
        val tree = StorageLocation.customTree(context) ?: return emptyList()
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
        return runCatching {
            dir.listFiles()
                .filter { doc -> prefixes.any { doc.name?.startsWith("${it}_") == true } }
                .map {
                    MediaEntry(
                        name = it.name.orEmpty(),
                        size = it.length(),
                        modified = it.lastModified(),
                        inChosenFolder = true,
                        docUri = it.uri,
                    )
                }
        }.getOrDefault(emptyList())
    }

    /// Total bytes captured across both locations.
    fun usedBytes(context: Context): Long =
        RecordingStore.usedBytes(context) +
            chosen(context, listOf("VID", "AUD", "IMG")).sumOf { it.size }

    fun open(context: Context, entry: MediaEntry) {
        entry.file?.let { RecordingStore.open(context, it); return }
        val uri = entry.docUri ?: return
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime(entry.name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    fun delete(context: Context, entry: MediaEntry): Boolean {
        entry.file?.let { return it.delete() }
        val uri = entry.docUri ?: return false
        return runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            .getOrDefault(false)
    }

    /// Copies one capture to [dest] (a SAF document). MP4 keeps its index at the
    /// end, so success means every byte arrived — never "the copy started".
    ///
    /// Low load: FileChannel.transferTo hands the copy to the kernel instead of
    /// pumping every byte through app memory, and a sync every 16 MB stops the
    /// kernel buffering gigabytes and then stalling everything to flush them.
    fun export(context: Context, entry: MediaEntry, dest: Uri, onBytes: (Long) -> Unit = {}): Boolean = runCatching {
        val inPfd = entry.file?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
            ?: context.contentResolver.openFileDescriptor(entry.docUri!!, "r")
            ?: return false
        val outPfd = context.contentResolver.openFileDescriptor(dest, "wt") ?: return false
        inPfd.use { inP ->
            outPfd.use { outP ->
                val source = FileInputStream(inP.fileDescriptor).channel
                val target = FileOutputStream(outP.fileDescriptor).channel
                val total = source.size()
                var position = 0L
                while (position < total) {
                    val n = source.transferTo(position, minOf(CHUNK, total - position), target)
                    if (n <= 0) break
                    position += n
                    onBytes(n)
                    // Some providers (FUSE) reject this; the copy is still correct.
                    runCatching { Os.fdatasync(outP.fileDescriptor) }
                }
                runCatching { Os.fsync(outP.fileDescriptor) }
                position == total && total == entry.size
            }
        }
    }.getOrDefault(false)

    private const val CHUNK = 16L shl 20
}

/// Progress of a batch export: item [index] of [count], [percent] of all bytes.
data class ExportStatus(val index: Int, val count: Int, val percent: Int)

/// One batch export at a time, owned by the app rather than a screen, so its
/// progress survives switching tabs and the Vault picks it straight back up.
object Exporter {
    val status = MutableStateFlow<ExportStatus?>(null)
    val notice = MutableStateFlow<String?>(null)
    /// Bumped when an export changes the vault contents (a Move), so lists refresh.
    val changes = MutableStateFlow(0)

    // ponytail: dies with the app process; move to a foreground service if exports
    // must survive the app being swiped away.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /// Copies (or moves) [entries] into the folder [tree] the user picked once.
    fun start(context: Context, entries: List<MediaEntry>, tree: Uri, move: Boolean) {
        if (status.value != null || entries.isEmpty()) return
        val app = context.applicationContext
        status.value = ExportStatus(1, entries.size, 0)
        scope.launch {
            // Background priority: the copy yields the CPU to the UI instead of
            // competing with it.
            val previous = Process.getThreadPriority(Process.myTid())
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            var failed = 0
            try {
                val total = entries.sumOf { it.size }.coerceAtLeast(1)
                var done = 0L
                var lastPercent = 0
                val dir = DocumentFile.fromTreeUri(app, tree)
                entries.forEachIndexed { i, entry ->
                    status.value = ExportStatus(i + 1, entries.size, lastPercent)
                    val doc = dir?.createFile(MediaLibrary.mime(entry.name), entry.name)
                    val ok = doc != null && MediaLibrary.export(app, entry, doc.uri) { n ->
                        done += n
                        val percent = (done * 100 / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            status.value = ExportStatus(i + 1, entries.size, percent)
                        }
                    }
                    if (ok) {
                        // Move removes the original only after a verified copy.
                        if (move) MediaLibrary.delete(app, entry)
                    } else {
                        failed++
                        // Never leave a half-copied, unplayable file behind.
                        runCatching { doc?.delete() }
                    }
                }
            } finally {
                Process.setThreadPriority(previous)
            }
            if (move) changes.value++
            val verb = if (move) "Moved" else "Copied"
            notice.value = if (failed == 0) "$verb ${entries.size} ${if (entries.size == 1) "item" else "items"}"
            else "$failed of ${entries.size} failed — those originals are untouched"
            status.value = null
        }
    }
}
