package com.credo.soundgroove.auto

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Identifiants de l'arbre Android Auto ([MediaLibraryService]).
 *
 * Racines : Playlists / Dossiers / Favoris.
 */
object AutoMediaIds {
    const val ROOT = "sg_root"
    const val PLAYLISTS = "sg_playlists"
    const val FOLDERS = "sg_folders"
    const val FAVORITES = "sg_favorites"

    private const val PLAYLIST_PREFIX = "sg_playlist:"
    private const val FOLDER_PREFIX = "sg_folder:"
    private const val SONG_PREFIX = "sg_song:"

    fun playlist(id: Long): String = "$PLAYLIST_PREFIX$id"

    fun folder(path: String): String = "$FOLDER_PREFIX${encode(path)}"

    fun song(uri: String): String = "$SONG_PREFIX$uri"

    fun parsePlaylistId(mediaId: String): Long? =
        mediaId.removePrefix(PLAYLIST_PREFIX).toLongOrNull().takeIf {
            mediaId.startsWith(PLAYLIST_PREFIX)
        }

    fun parseFolderPath(mediaId: String): String? =
        if (mediaId.startsWith(FOLDER_PREFIX)) {
            decode(mediaId.removePrefix(FOLDER_PREFIX))
        } else {
            null
        }

    fun parseSongUri(mediaId: String): String? =
        if (mediaId.startsWith(SONG_PREFIX)) {
            mediaId.removePrefix(SONG_PREFIX)
        } else {
            null
        }

    fun isBrowsableRoot(mediaId: String): Boolean =
        mediaId == ROOT ||
            mediaId == PLAYLISTS ||
            mediaId == FOLDERS ||
            mediaId == FAVORITES ||
            mediaId.startsWith(PLAYLIST_PREFIX) ||
            mediaId.startsWith(FOLDER_PREFIX)

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)
}
