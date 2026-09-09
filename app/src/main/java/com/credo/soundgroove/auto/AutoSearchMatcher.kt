package com.credo.soundgroove.auto

import com.credo.soundgroove.data.model.Song

/**
 * Matching texte pour la recherche Android Auto (titre / artiste / album).
 * Isolé pour tests unitaires sans Media3 / Uri.
 */
object AutoSearchMatcher {

    fun matches(title: String, artist: String, album: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        return title.contains(q, ignoreCase = true) ||
            artist.contains(q, ignoreCase = true) ||
            album.contains(q, ignoreCase = true)
    }

    fun matches(song: Song, query: String): Boolean =
        matches(song.title, song.artist, song.albumName, query)
}
