package com.credo.soundgroove.widget

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import com.credo.soundgroove.RecentlyPlayedEntity
import com.credo.soundgroove.SoundGrooveDatabase
import com.credo.soundgroove.toFavoriteEntity
import com.credo.soundgroove.toSong
import com.credo.soundgroove.data.model.Song

/**
 * Accès Room lecture / favori pour widgets — offline-first, hors ViewModel.
 */
object WidgetOfflineStore {

    data class PulseTrack(
        val songId: Long,
        val title: String,
        val artist: String,
        val uri: Uri,
        val albumArtUri: Uri?,
    )

    fun resolveSongId(uri: Uri?): Long {
        if (uri == null) return -1L
        return runCatching { ContentUris.parseId(uri) }.getOrDefault(-1L)
    }

    suspend fun isFavorite(context: Context, songId: Long, mediaUri: String): Boolean {
        val dao = SoundGrooveDatabase.getInstance(context).favoriteDao()
        if (songId >= 0L && dao.isFavorite(songId)) return true
        if (mediaUri.isNotBlank() && dao.isFavoriteByUri(mediaUri)) return true
        return false
    }

    suspend fun toggleFavoriteFromState(context: Context, state: WidgetPlaybackState): Boolean? {
        val song = resolveSong(context, state) ?: return null
        val dao = SoundGrooveDatabase.getInstance(context).favoriteDao()
        val currently = dao.isFavorite(song.id) || dao.isFavoriteByUri(song.uri.toString())
        if (currently) {
            dao.delete(song.id)
            dao.getByUri(song.uri.toString())?.let { dao.delete(it.songId) }
            return false
        }
        dao.insert(song.toFavoriteEntity())
        return true
    }

    suspend fun recentPulse(context: Context, limit: Int = 3): List<PulseTrack> {
        return SoundGrooveDatabase.getInstance(context)
            .recentlyPlayedDao()
            .getRecentOnce(limit)
            .map { it.toPulse() }
    }

    suspend fun continueListening(context: Context): PulseTrack? {
        val snapshot = com.credo.soundgroove.util.PlaybackSessionStore.read(context) ?: return null
        val recent = SoundGrooveDatabase.getInstance(context)
            .recentlyPlayedDao()
            .getBySongId(snapshot.songId)
        if (recent != null) return recent.toPulse()
        val fav = SoundGrooveDatabase.getInstance(context)
            .favoriteDao()
            .getAllOnce()
            .find { it.songId == snapshot.songId }
        return fav?.let {
            PulseTrack(
                songId = it.songId,
                title = it.title,
                artist = it.artist,
                uri = Uri.parse(it.uri),
                albumArtUri = it.albumArtUri?.let(Uri::parse),
            )
        }
    }

    private suspend fun resolveSong(context: Context, state: WidgetPlaybackState): Song? {
        val db = SoundGrooveDatabase.getInstance(context)
        if (state.songId >= 0L) {
            db.recentlyPlayedDao().getBySongId(state.songId)?.toSong()?.let { return it }
            db.favoriteDao().getAllOnce().find { it.songId == state.songId }?.toSong()?.let { return it }
        }
        if (state.mediaUri.isNotBlank()) {
            db.recentlyPlayedDao().getByUri(state.mediaUri)?.toSong()?.let { return it }
            db.favoriteDao().getByUri(state.mediaUri)?.toSong()?.let { return it }
        }
        if (state.mediaUri.isBlank() || state.title.isBlank()) return null
        val uri = Uri.parse(state.mediaUri)
        val id = if (state.songId >= 0L) state.songId else resolveSongId(uri)
        if (id < 0L) return null
        return Song(
            id = id,
            title = state.title,
            artist = state.artist,
            uri = uri,
            albumArtUri = state.albumArtUri,
        )
    }

    private fun RecentlyPlayedEntity.toPulse() = PulseTrack(
        songId = songId,
        title = title,
        artist = artist,
        uri = Uri.parse(uri),
        albumArtUri = albumArtUri?.let(Uri::parse),
    )
}
