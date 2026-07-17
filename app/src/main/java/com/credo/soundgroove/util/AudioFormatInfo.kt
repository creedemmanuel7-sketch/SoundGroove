package com.credo.soundgroove.util

/**
 * Documentation des formats audio supportés par SoundGroove via Media3/ExoPlayer.
 */
object AudioFormatInfo {

    const val FORMATS_ABOUT_TEXT = "Formats audio (Media3 ExoPlayer) :\n" +
        "• MP3, AAC, M4A, OGG, Opus, WAV — lecture native\n" +
        "• FLAC — pris en charge via l'extracteur Media3 (media3-extractor)\n" +
        "• DSD (DSF/DFF) — non supporté nativement par Media3 ; les fichiers DSD ne seront pas lus\n\n" +
        "Les métadonnées ID3/Vorbis sont lues via MediaStore à l'import."

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
