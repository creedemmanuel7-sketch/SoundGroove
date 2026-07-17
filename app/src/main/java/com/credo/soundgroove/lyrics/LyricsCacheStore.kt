package com.credo.soundgroove.lyrics

import android.content.Context
import java.io.File

/** Cache local des paroles saisies ou importées, indexé par [songId]. */
object LyricsCacheStore {

    private const val DIR_NAME = "lyrics"

    private fun cacheDir(context: Context): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    private fun cacheFile(context: Context, songId: Long): File =
        File(cacheDir(context), "$songId.txt")

    fun read(context: Context, songId: Long): String? {
        val file = cacheFile(context, songId)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun write(context: Context, songId: Long, rawText: String): Boolean =
        runCatching {
            cacheFile(context, songId).writeText(rawText)
            true
        }.getOrDefault(false)

    fun delete(context: Context, songId: Long): Boolean =
        runCatching { cacheFile(context, songId).delete() }.getOrDefault(false)
}
