package com.credo.soundgroove.lyrics

import android.content.Context
import com.credo.soundgroove.data.model.Song

/** Charge les paroles locales d'un morceau (aucun accès réseau — voir suite v2). */
object LyricsRepository {

    fun loadLyrics(context: Context, song: Song): LyricsContent {
        val file = LyricsFileResolver.findLyricsFile(context, song) ?: return LyricsContent.NotFound
        return runCatching {
            val raw = file.readText()
            if (file.extension.lowercase() == "lrc" && LrcParser.isLikelySynced(raw)) {
                val lines = LrcParser.parse(raw)
                if (lines.isNotEmpty()) LyricsContent.Synced(lines) else LyricsContent.PlainText(stripTags(raw))
            } else {
                LyricsContent.PlainText(raw.trim())
            }
        }.getOrDefault(LyricsContent.NotFound)
    }

    private fun stripTags(raw: String): String =
        raw.lineSequence()
            .map { it.replace(Regex("""\[[^\]]*\]"""), "").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
}
