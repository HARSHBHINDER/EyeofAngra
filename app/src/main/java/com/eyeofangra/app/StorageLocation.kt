package com.eyeofangra.app

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/// Decides where a capture is written.
///
/// Toggle off -> this app's private storage ("on board"), the reliable default.
/// Toggle on with a chosen folder -> that folder, via the Storage Access Framework.
/// If the chosen folder is missing or its permission was revoked, callers fall back
/// to internal storage, so a recording is never lost to a bad path.
object StorageLocation {

    /// A writable document created in the chosen folder: its uri and an open descriptor.
    data class CustomTarget(val uri: Uri, val pfd: ParcelFileDescriptor)

    /// The persisted tree uri when the toggle is on and write permission still holds.
    fun customTree(context: Context): Uri? {
        // ponytail: one-shot blocking read of a preference off the capture-start path;
        // fine here, would matter only if capture start were latency-critical.
        val s = runBlocking { SettingsStore.flow(context).first() }
        val uriString = s.storageUri
        if (!s.customStorage || uriString == null) return null
        val uri = Uri.parse(uriString)
        val held = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        return if (held) uri else null
    }

    private fun mime(ext: String) = when (ext) {
        "mp4" -> "video/mp4"
        "m4a" -> "audio/mp4"
        else -> "image/jpeg"
    }

    /// Creates the output document in the chosen folder, or null to mean "use internal".
    fun open(context: Context, prefix: String, ext: String): CustomTarget? {
        val tree = customTree(context) ?: return null
        return runCatching {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: return null
            val doc = dir.createFile(mime(ext), RecordingStore.stampName(prefix, ext)) ?: return null
            val pfd = context.contentResolver.openFileDescriptor(doc.uri, "rw") ?: return null
            CustomTarget(doc.uri, pfd)
        }.getOrNull()
    }

    /// Human-readable name of where captures currently go, for the Settings screen.
    fun label(context: Context): String {
        val tree = customTree(context) ?: return "This app's private storage"
        return DocumentFile.fromTreeUri(context, tree)?.name ?: "Chosen folder"
    }
}
