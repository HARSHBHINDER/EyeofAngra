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

    /// Copies a capture to a folder the user picked (SAF). MP4 keeps its index at
    /// the end, so success means every byte arrived — never "the copy started".
    ///
    /// Smoothness, not just speed: written data is synced to flash every 16 MB.
    /// Without that the kernel buffers gigabytes, then stalls everything to flush
    /// them in bursts — the fast-slow-fast progress and the janky screen.
    fun export(context: Context, entry: MediaEntry, dest: Uri, onProgress: (Int) -> Unit = {}): Boolean = runCatching {
        val inPfd = entry.file?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
            ?: context.contentResolver.openFileDescriptor(entry.docUri!!, "r")
            ?: return false
        val outPfd = context.contentResolver.openFileDescriptor(dest, "wt") ?: return false
        var copied = 0L
        var sinceSync = 0L
        var lastPercent = -1
        inPfd.use { inP ->
            outPfd.use { outP ->
                val input = FileInputStream(inP.fileDescriptor)
                val output = FileOutputStream(outP.fileDescriptor)
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    copied += n
                    sinceSync += n
                    if (sinceSync >= SYNC_EVERY) {
                        sinceSync = 0
                        // Some providers (FUSE) reject these; the copy is still correct.
                        runCatching { Os.fdatasync(outP.fileDescriptor) }
                    }
                    val percent = if (entry.size > 0) (copied * 100 / entry.size).toInt() else 0
                    if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                }
                runCatching { Os.fsync(outP.fileDescriptor) }
            }
        }
        copied == entry.size
    }.getOrDefault(false)

    private const val SYNC_EVERY = 16L shl 20
}

/// One export at a time, owned by the app rather than a screen, so its progress
/// survives switching tabs and the Vault picks it straight back up.
object Exporter {
    /// 0..100 while running, null when idle.
    val progress = MutableStateFlow<Int?>(null)
    val notice = MutableStateFlow<String?>(null)
    /// Bumped when an export changes the vault contents (a Move), so lists refresh.
    val changes = MutableStateFlow(0)

    // ponytail: dies with the app process; move to a foreground service if exports
    // must survive the app being swiped away.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context, entry: MediaEntry, dest: Uri, move: Boolean) {
        if (progress.value != null) return
        val app = context.applicationContext
        progress.value = 0
        scope.launch {
            // Background priority: the copy yields the CPU to the UI instead of
            // competing with it, which is what made the screen stutter.
            val previous = Process.getThreadPriority(Process.myTid())
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val ok = try {
                MediaLibrary.export(app, entry, dest) { progress.value = it }
            } finally {
                Process.setThreadPriority(previous)
            }
            if (ok) {
                if (move) {
                    MediaLibrary.delete(app, entry)
                    changes.value++
                }
                notice.value = if (move) "Moved" else "Exported"
            } else {
                // Never leave a half-copied, unplayable file in the user's folder.
                runCatching { DocumentsContract.deleteDocument(app.contentResolver, dest) }
                notice.value = "Export failed — the original is untouched"
            }
            progress.value = null
        }
    }
}
