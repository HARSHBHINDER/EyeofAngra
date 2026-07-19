package com.eyeofangra.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/// Names, lists, deletes, and opens recording files in app-specific external storage.
/// Filenames sort chronologically because of the yyyyMMdd_HHmmss stamp.
object RecordingStore {

    fun dir(context: Context): File = context.getExternalFilesDir(null)!!

    fun newFile(context: Context, prefix: String, ext: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var file = File(dir(context), "${prefix}_$stamp.$ext")
        // Two captures inside one second must never overwrite each other: evidence.
        var n = 2
        while (file.exists()) {
            file = File(dir(context), "${prefix}_${stamp}_$n.$ext")
            n++
        }
        return file
    }

    fun list(context: Context, prefix: String): List<File> =
        dir(context).listFiles { f -> f.name.startsWith("${prefix}_") }
            ?.sortedByDescending { it.name } ?: emptyList()

    /// Hands playback/viewing to whatever player the device already has.
    fun open(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "com.eyeofangra.app.files", file)
        val mime = when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "m4a" -> "audio/mp4"
            else -> "image/jpeg"
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}
