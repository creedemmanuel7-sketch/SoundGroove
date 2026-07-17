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

    fun merge(manualPlaylists: List<Playlist>, recentlyPlayed: List<Song>, oftenPlayed: List<Song>): List<Playlist> {
        val smart = listOf(
            buildRecentlyPlayed(recentlyPlayed),
            buildOftenPlayed(oftenPlayed)
        )
        return smart + manualPlaylists.filter { !it.isSmart }
    }
}
