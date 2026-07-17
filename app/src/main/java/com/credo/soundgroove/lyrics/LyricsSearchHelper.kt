package com.credo.soundgroove.lyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.credo.soundgroove.data.model.Song

/** Ouvre un site de paroles dans le navigateur pour copier/coller manuellement. */
object LyricsSearchHelper {

    fun openGoogleLyricsSearch(context: Context, song: Song) {
        val query = Uri.encode("${song.title} ${song.artist} lyrics")
        launchBrowser(context, "https://www.google.com/search?q=$query")
    }

    fun openGeniusSearch(context: Context, song: Song) {
        val query = Uri.encode("${song.title} ${song.artist}")
        launchBrowser(context, "https://genius.com/search?q=$query")
    }

    private fun launchBrowser(context: Context, url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }
}
