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

    fun loadFromCache(context: Context, song: Song): LyricsContent? = runCatching {
        val raw = LyricsCacheStore.read(context, song.id) ?: return@runCatching null
        parseRaw(raw)
    }.getOrNull()

    /** Charge uniquement les sources locales (cache + fichier voisin). */
    fun loadLocalLyrics(context: Context, song: Song): LyricsContent = runCatching {
        loadFromCache(context, song)?.let { cached ->
            if (isComplete(cached)) return@runCatching cached
        }

        val file = LyricsFileResolver.findLyricsFile(context, song) ?: return@runCatching LyricsContent.NotFound
        val raw = runCatching { file.readText() }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return@runCatching LyricsContent.NotFound

        val content = parseRaw(raw, file.extension)
        if (isComplete(content)) {
            LyricsCacheStore.write(context, song.id, raw)
        }
        content
    }.getOrElse { LyricsContent.NotFound }

    /**
     * Recherche en ligne via LRCLIB. Met en cache le texte brut en cas de succès
     * pour éviter les appels répétés (usage raisonnable).
     */
    fun fetchOnlineLyrics(context: Context, song: Song): LyricsContent = runCatching {
        val online = LrcLibClient.fetchLyrics(song) ?: return@runCatching LyricsContent.NotFound
        val raw = online.bestRawText() ?: return@runCatching LyricsContent.NotFound

        LyricsCacheStore.write(context, song.id, raw)
        LyricsFileResolver.tryWriteLyricsFile(context, song, raw)

        val content = parseRaw(raw)
        if (isComplete(content)) content else LyricsContent.NotFound
    }.getOrElse { LyricsContent.NotFound }

    fun saveLyrics(context: Context, song: Song, rawText: String): LyricsContent {
        val trimmed = rawText.trim()
        require(trimmed.isNotBlank()) { "Le texte des paroles est vide." }

        if (!LyricsCacheStore.write(context, song.id, trimmed)) {
            throw IllegalStateException("Impossible d'enregistrer les paroles en cache.")
        }
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

    fun parseRaw(raw: String, extensionHint: String? = null): LyricsContent = runCatching {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return@runCatching LyricsContent.NotFound

        val isLrc = extensionHint?.lowercase() == "lrc" || LrcParser.isLikelySynced(trimmed)
        if (isLrc && LrcParser.isLikelySynced(trimmed)) {
            val lines = LrcParser.parse(trimmed)
            if (lines.isNotEmpty()) return@runCatching LyricsContent.Synced(lines)
        }

        val plain = if (isLrc) stripTags(trimmed) else trimmed
        if (plain.isBlank()) return@runCatching LyricsContent.NotFound
        LyricsContent.PlainText(plain)
    }.getOrElse { LyricsContent.NotFound }

    private fun stripTags(raw: String): String =
        raw.lineSequence()
            .map { it.replace(Regex("""\[[^\]]*\]"""), "").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
}
