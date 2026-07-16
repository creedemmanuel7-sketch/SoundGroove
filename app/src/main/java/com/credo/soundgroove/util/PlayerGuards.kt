package com.credo.soundgroove.util

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.credo.soundgroove.Song

/** Garde-fous Media3 pour éviter les crashs (index invalides, file vide). */
object PlayerGuards {

    fun safeSeekToNext(player: Player): Boolean {
        if (player.mediaItemCount <= 0) return false
        if (player.currentMediaItemIndex >= player.mediaItemCount - 1) return false
        player.seekToNextMediaItem()
        return true
    }

    fun safeSeekToPrevious(player: Player): Boolean {
        if (player.mediaItemCount <= 0) return false
        if (player.currentMediaItemIndex <= 0) return false
        player.seekToPreviousMediaItem()
        return true
    }

    fun safeSeekToIndex(player: Player, index: Int): Boolean {
        if (player.mediaItemCount <= 0 || index !in 0 until player.mediaItemCount) return false
        player.seekToDefaultPosition(index)
        return true
    }

    fun safeRemoveMediaItem(player: Player, index: Int): Boolean {
        if (index !in 0 until player.mediaItemCount) return false
        player.removeMediaItem(index)
        return true
    }

    fun safeMoveMediaItem(player: Player, from: Int, to: Int): Boolean {
        if (player.mediaItemCount <= 0) return false
        if (from !in 0 until player.mediaItemCount || to !in 0 until player.mediaItemCount || from == to) {
            return false
        }
        player.moveMediaItem(from, to)
        return true
    }

    fun resolveSongFromMediaItem(item: MediaItem, songs: List<Song>): Song? {
        val mediaId = item.mediaId
        return songs.find { it.uri.toString() == mediaId }
            ?: item.localConfiguration?.uri?.let { uri -> songs.find { it.uri == uri } }
    }

    fun rebuildPlaylistFromPlayer(player: Player, songs: List<Song>): List<Song> =
        (0 until player.mediaItemCount).mapNotNull { index ->
            resolveSongFromMediaItem(player.getMediaItemAt(index), songs)
        }

    fun safeCurrentIndex(player: Player): Int =
        if (player.mediaItemCount <= 0) 0
        else player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
}
