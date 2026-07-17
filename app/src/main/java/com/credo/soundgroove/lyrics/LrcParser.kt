package com.credo.soundgroove.lyrics

/**
 * Parseur LRC léger, sans dépendance externe.
 *
 * Format attendu : `[mm:ss.xx] texte de la ligne`, avec la possibilité d'avoir
 * plusieurs horodatages sur une même ligne (ex. refrain répété) :
 * `[00:12.00][00:45.30] Paroles`.
 * Les balises de métadonnées ([ti:...], [ar:...], [by:...], etc.) sont ignorées.
 */
object LrcParser {

    private val TIME_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

    /** Vrai si le contenu comporte au moins une balise temporelle exploitable. */
    fun isLikelySynced(raw: String): Boolean = TIME_TAG_REGEX.containsMatchIn(raw)

    fun parse(raw: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { rawLine ->
            val matches = TIME_TAG_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            matches.forEach { match ->
                val timeMs = toMillis(match) ?: return@forEach
                lines += LyricLine(timeMs, text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** Reconstruit un LRC minimal à partir des lignes synchronisées (édition de secours). */
    fun format(lines: List<LyricLine>): String =
        lines.joinToString("\n") { line ->
            val totalMs = line.timeMs.coerceAtLeast(0L)
            val minutes = totalMs / 60_000L
            val seconds = (totalMs % 60_000L) / 1_000L
            val centis = (totalMs % 1_000L) / 10L
            "[%02d:%02d.%02d] %s".format(minutes, seconds, centis, line.text)
        }

    private fun toMillis(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val fraction = match.groupValues[3]
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }
}
