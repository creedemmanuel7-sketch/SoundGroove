package com.credo.soundgroove.data.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val albumArtUri: Uri?,
    val albumName: String = "",
    val folderPath: String = "",
    val duration: Long = 0L,
    val dateAdded: Long = 0L
)

data class Playlist(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val songs: List<Song> = emptyList(),
    val isSmart: Boolean = false
)
