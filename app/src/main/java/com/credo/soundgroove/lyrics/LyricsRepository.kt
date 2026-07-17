package com.credo.soundgroove.lyrics

import android.content.Context
import com.credo.soundgroove.data.model.Song

/**
 * Charge et persiste les paroles d'un morceau.
 * Priorité locale : cache app → fichier voisin (.lrc / .txt).
 * Puis recherche en ligne LRCLIB (avec cache obligatoire après succès).
 */
object LyricsRepository {

    private const val MIN_PLAIN_TEXT_LENGTH = 8

    fun loadFromCache(context: Context, song: Song): LyricsContent? {
        val raw = LyricsCacheStore.read(context, song.id) ?: return null
        return parseRaw(raw)
    }

    /** Charge uniquement les sources locales (cache + fichier voisin). */
    fun loadLocalLyrics(context: Context, song: Song): LyricsContent {
        loadFromCache(context, song)?.let { cached ->
            if (isComplete(cached)) return cached
        }

        val file = LyricsFileResolver.findLyricsFile(context, song) ?: return LyricsContent.NotFound
        val raw = runCatching { file.readText() }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return LyricsContent.NotFound

        val content = parseRaw(raw, file.extension)
        if (isComplete(content)) {
            LyricsCacheStore.write(context, song.id, raw)
        }
        return content
    }

    /**
     * Recherche en ligne via LRCLIB. Met en cache le texte brut en cas de succès
     * pour éviter les appels répétés (usage raisonnable).
     */
    fun fetchOnlineLyrics(context: Context, song: Song): LyricsContent {
        val online = LrcLibClient.fetchLyrics(song) ?: return LyricsContent.NotFound
        val raw = online.bestRawText() ?: return LyricsContent.NotFound

        LyricsCacheStore.write(context, song.id, raw)
        LyricsFileResolver.tryWriteLyricsFile(context, song, raw)

        val content = parseRaw(raw)
        return if (isComplete(content)) content else LyricsContent.NotFound
    }

    fun saveLyrics(context: Context, song: Song, rawText: String): LyricsContent {
        val trimmed = rawText.trim()
        require(trimmed.isNotBlank()) { "Le texte des paroles est vide." }

        LyricsCacheStore.write(context, song.id, trimmed)
        LyricsFileResolver.tryWriteLyricsFile(context, song, trimmed)
        return parseRaw(trimmed)
    }

    fun isComplete(content: LyricsContent): Boolean = when (content) {
        is LyricsContent.Synced -> content.lines.any { it.text.isNotBlank() }
        is LyricsContent.PlainText -> content.text.trim().length >= MIN_PLAIN_TEXT_LENGTH
        is LyricsContent.Loading,
        is LyricsContent.SearchingOnline,
        is LyricsContent.NotFound -> false
    }

    fun parseRaw(raw: String, extensionHint: String? = null): LyricsContent {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return LyricsContent.NotFound

        val isLrc = extensionHint?.lowercase() == "lrc" || LrcParser.isLikelySynced(trimmed)
        if (isLrc && LrcParser.isLikelySynced(trimmed)) {
            val lines = LrcParser.parse(trimmed)
            if (lines.isNotEmpty()) return LyricsContent.Synced(lines)
        }

        val plain = if (isLrc) stripTags(trimmed) else trimmed
        if (plain.isBlank()) return LyricsContent.NotFound
        return LyricsContent.PlainText(plain)
    }

    private fun stripTags(raw: String): String =
        raw.lineSequence()
            .map { it.replace(Regex("""\[[^\]]*\]"""), "").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
}
