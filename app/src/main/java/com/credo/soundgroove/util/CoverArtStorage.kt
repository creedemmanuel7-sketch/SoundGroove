package com.credo.soundgroove.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CoverArtStorage {
    private const val COVER_DIR = "cover_overrides"

    private fun coverFile(context: Context, songId: Long): File =
        File(File(context.filesDir, COVER_DIR), "$songId.jpg")

    suspend fun saveFromUri(context: Context, songId: Long, sourceUri: Uri): Uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, COVER_DIR).apply { mkdirs() }
            val dest = File(dir, "$songId.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Impossible de lire l'image sélectionnée")
            Uri.fromFile(dest)
        }

    fun getStoredUri(context: Context, songId: Long): Uri? {
        val file = coverFile(context, songId)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    fun delete(context: Context, songId: Long) {
        coverFile(context, songId).delete()
    }
}
