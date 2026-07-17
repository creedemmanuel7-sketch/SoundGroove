package com.credo.soundgroove.lyrics

/** État du contenu de paroles pour le morceau en cours. */
sealed class LyricsContent {
    data object Loading : LyricsContent()
    data object SearchingOnline : LyricsContent()
    data class Synced(val lines: List<LyricLine>) : LyricsContent()
    data class PlainText(val text: String) : LyricsContent()
    data object NotFound : LyricsContent()
}
