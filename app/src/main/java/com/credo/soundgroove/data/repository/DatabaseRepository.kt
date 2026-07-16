package com.credo.soundgroove.data.repository

import com.credo.soundgroove.FavoriteDao
import com.credo.soundgroove.PlaylistDao
import com.credo.soundgroove.PlaylistEntity
import com.credo.soundgroove.PlaylistSongEntity
import com.credo.soundgroove.RecentlyPlayedDao
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.toFavoriteEntity
import com.credo.soundgroove.toRecentlyPlayedEntity
import com.credo.soundgroove.toSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class DatabaseRepository(
    private val favoriteDao: FavoriteDao,
    private val recentlyPlayedDao: RecentlyPlayedDao,
    private val playlistDao: PlaylistDao
) {
    // --- Favorites ---
    fun getFavorites(): Flow<List<Song>> = favoriteDao.getAll().map { list ->
        list.map { it.toSong() }
    }

    suspend fun toggleFavorite(song: Song) {
        val isFav = favoriteDao.isFavorite(song.id)
        if (isFav) {
            favoriteDao.delete(song.id)
        } else {
            favoriteDao.insert(song.toFavoriteEntity())
        }
    }

    suspend fun isFavorite(songId: Long): Boolean = favoriteDao.isFavorite(songId)

    // --- Recently Played ---
    fun getRecentlyPlayed(): Flow<List<Song>> = recentlyPlayedDao.getAll().map { list ->
        list.map { it.toSong() }
    }

    suspend fun addRecentlyPlayed(song: Song) {
        recentlyPlayedDao.insert(song.toRecentlyPlayedEntity())
        recentlyPlayedDao.trimToLimit()
    }

    suspend fun clearRecentlyPlayed() {
        recentlyPlayedDao.clearAll()
    }

    // --- Playlists ---
    fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().flatMapLatest { entities ->
            if (entities.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    entities.map { entity ->
                        playlistDao.getSongsForPlaylist(entity.id).map { playlistSongs ->
                            Playlist(
                                id = entity.id,
                                name = entity.name,
                                songs = playlistSongs.map { it.toSong() }
                            )
                        }
                    }
                ) { playlists -> playlists.toList() }
            }
        }
    }

    private fun PlaylistSongEntity.toSong(): Song =
        Song(
            id = songId,
            title = title,
            artist = artist,
            uri = android.net.Uri.parse(uri),
            albumArtUri = albumArtUri?.let { android.net.Uri.parse(it) }
        )

    fun getPlaylistWithSongs(playlistId: Long, name: String): Flow<Playlist> {
        return playlistDao.getSongsForPlaylist(playlistId).map { playlistSongs ->
            val songs = playlistSongs.map { ps ->
                Song(
                    id = ps.songId,
                    title = ps.title,
                    artist = ps.artist,
                    uri = android.net.Uri.parse(ps.uri),
                    albumArtUri = ps.albumArtUri?.let { android.net.Uri.parse(it) }
                )
            }
            Playlist(id = playlistId, name = name, songs = songs)
        }
    }

    suspend fun createPlaylist(name: String) {
        val id = System.currentTimeMillis()
        playlistDao.insertPlaylist(PlaylistEntity(id, name))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.clearPlaylist(playlistId)
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        playlistDao.renamePlaylist(playlistId, newName)
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: Song, position: Int) {
        playlistDao.insertSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                uri = song.uri.toString(),
                albumArtUri = song.albumArtUri?.toString(),
                position = position
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSong(playlistId, songId)
    }

    suspend fun getFavoritesSnapshot(): List<Song> =
        favoriteDao.getAllOnce().map { it.toSong() }

    suspend fun getPlaylistsSnapshot(): List<Playlist> {
        val entities = playlistDao.getAllPlaylistsOnce()
        val allSongs = playlistDao.getAllPlaylistSongsOnce()
        return entities.map { entity ->
            Playlist(
                id = entity.id,
                name = entity.name,
                songs = allSongs
                    .filter { it.playlistId == entity.id }
                    .sortedBy { it.position }
                    .map { it.toSong() }
            )
        }
    }

    suspend fun replaceLibraryData(favorites: List<Song>, playlists: List<Playlist>) {
        favoriteDao.clearAll()
        favorites.forEach { favoriteDao.insert(it.toFavoriteEntity()) }

        playlistDao.clearAllSongs()
        playlistDao.clearAllPlaylists()
        playlists.forEach { playlist ->
            playlistDao.insertPlaylist(PlaylistEntity(playlist.id, playlist.name))
            playlist.songs.forEachIndexed { index, song ->
                playlistDao.insertSong(
                    PlaylistSongEntity(
                        playlistId = playlist.id,
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        uri = song.uri.toString(),
                        albumArtUri = song.albumArtUri?.toString(),
                        position = index
                    )
                )
            }
        }
    }
}
