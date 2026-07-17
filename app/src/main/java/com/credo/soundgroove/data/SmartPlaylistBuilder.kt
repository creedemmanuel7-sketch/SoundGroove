package com.credo.soundgroove.data

import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.SmartPlaylistIds
import com.credo.soundgroove.data.model.Song

object SmartPlaylistBuilder {

    fun buildRecentlyPlayed(songs: List<Song>): Playlist =
        Playlist(
            id = SmartPlaylistIds.RECENTLY_PLAYED,
            name = SmartPlaylistIds.RECENTLY_PLAYED_NAME,
            songs = songs.distinctBy { it.id },
            isSmart = true
        )

    fun buildOftenPlayed(songs: List<Song>): Playlist =
        Playlist(
            id = SmartPlaylistIds.OFTEN_PLAYED,
            name = SmartPlaylistIds.OFTEN_PLAYED_NAME,
            songs = songs.distinctBy { it.id },
            isSmart = true
        )

    fun buildWithLyrics(songs: List<Song>): Playlist =
        Playlist(
            id = SmartPlaylistIds.WITH_LYRICS,
            name = SmartPlaylistIds.WITH_LYRICS_NAME,
            songs = songs.distinctBy { it.id },
            isSmart = true
        )

    fun merge(
        manualPlaylists: List<Playlist>,
        recentlyPlayed: List<Song>,
        oftenPlayed: List<Song>,
        withLyrics: List<Song>
    ): List<Playlist> {
        val smart = listOf(
            buildRecentlyPlayed(recentlyPlayed),
            buildOftenPlayed(oftenPlayed),
            buildWithLyrics(withLyrics)
        )
        return smart + manualPlaylists.filter { !it.isSmart }
    }
}
