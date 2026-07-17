package com.credo.soundgroove.data.model

object SmartPlaylistIds {
    const val RECENTLY_PLAYED = -1001L
    const val OFTEN_PLAYED = -1002L

    const val RECENTLY_PLAYED_NAME = "Récemment écoutés"
    const val OFTEN_PLAYED_NAME = "Souvent écoutés"

    fun isSmart(id: Long): Boolean = id == RECENTLY_PLAYED || id == OFTEN_PLAYED
}
