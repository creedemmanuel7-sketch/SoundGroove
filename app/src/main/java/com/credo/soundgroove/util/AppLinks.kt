package com.credo.soundgroove.util

object AppLinks {
    const val WEBSITE_URL = "https://soundgroove.app"
    const val WEBSITE_LABEL = "soundgroove.app"

    fun shareText(songTitle: String, artist: String): String =
        "$songTitle — $artist\nÉcouté avec SoundGroove · $WEBSITE_URL"
}
