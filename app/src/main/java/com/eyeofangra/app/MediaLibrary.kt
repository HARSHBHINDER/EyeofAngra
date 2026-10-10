package com.eyeofangra.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

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

    /// Copies a capture to a destination the user picked (SAF), so it can leave
    /// the vault for any folder without deleting the original. Runs on a background
    /// thread — a multi-GB copy on the main thread gets the app killed mid-copy.
    /// MP4 keeps its index at the end of the file, so a short copy is unplayable:
    /// success means every byte arrived, never "the copy started".
    fun export(context: Context, entry: MediaEntry, dest: Uri, onProgress: (Int) -> Unit = {}): Boolean = runCatching {
        val input = entry.file?.inputStream()
            ?: context.contentResolver.openInputStream(entry.docUri!!)
            ?: return false
        val output = context.contentResolver.openOutputStream(dest, "wt") ?: return false
        var copied = 0L
        var lastPercent = -1
        input.use { i ->
            output.use { o ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val n = i.read(buffer)
                    if (n < 0) break
                    o.write(buffer, 0, n)
                    copied += n
                    val percent = if (entry.size > 0) (copied * 100 / entry.size).toInt() else 0
                    if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                }
            }
        }
        copied == entry.size
    }.getOrDefault(false)
}
