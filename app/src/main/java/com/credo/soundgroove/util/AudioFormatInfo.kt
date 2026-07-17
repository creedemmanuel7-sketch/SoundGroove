package com.credo.soundgroove.util

/**
 * Formats audio supportés — texte grand public pour l'écran À propos.
 */
object AudioFormatInfo {

    const val FORMATS_ABOUT_TEXT =
        "Formats pris en charge : MP3, AAC, M4A, OGG, Opus, WAV et FLAC.\n" +
            "Le DSD n'est pas lu pour le moment.\n\n" +
            "SoundGroove lit la musique stockée sur votre appareil."

    fun isDsdPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".dsf") || lower.endsWith(".dff")
    }

    fun isFlacPath(path: String): Boolean = path.lowercase().endsWith(".flac")

    fun formatSupportLabel(path: String): String = when {
        isDsdPath(path) -> "DSD — non supporté"
        isFlacPath(path) -> "FLAC — supporté"
        else -> "Format standard"
    }
}
