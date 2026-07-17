package com.credo.soundgroove.lyrics

import android.net.Uri
import com.credo.soundgroove.data.model.Song

/** Construit les URL de recherche de paroles (utilisées par la WebView in-app). */
object LyricsSearchHelper {

    fun buildGoogleLyricsSearchUrl(song: Song): String {
        val query = Uri.encode("${song.title} ${song.artist} lyrics")
        return "https://www.google.com/search?q=$query"
    }

    fun buildGeniusSearchUrl(song: Song): String {
        val query = Uri.encode("${song.title} ${song.artist}")
        return "https://genius.com/search?q=$query"
    }
}
