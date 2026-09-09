package com.credo.soundgroove.data.repository

import androidx.room.withTransaction
import com.credo.soundgroove.MetadataOverrideEntity
import com.credo.soundgroove.PlaylistEntity
import com.credo.soundgroove.PlaylistSongEntity
import com.credo.soundgroove.SoundGrooveDatabase
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.SmartPlaylistIds
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.toFavoriteEntity
import com.credo.soundgroove.toRecentlyPlayedEntity
import com.credo.soundgroove.toSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class DatabaseRepository(
    private val db: SoundGrooveDatabase
) {
    private val favoriteDao = db.favoriteDao()
    private val recentlyPlayedDao = db.recentlyPlayedDao()
    private val playlistDao = db.playlistDao()
    private val metadataOverrideDao = db.metadataOverrideDao()

    // --- Favorites ---
    fun getFavorites(): Flow<List<Song>> = favoriteDao.getAll().map { list ->
        list.map { it.toSong() }
    }

    suspend fun toggleFavorite(song: Song) {
        db.withTransaction {
            val isFav = favoriteDao.isFavorite(song.id)
            if (isFav) {
                favoriteDao.delete(song.id)
            } else {
                favoriteDao.insert(song.toFavoriteEntity())
            }
        }
    }

    suspend fun isFavorite(songId: Long): Boolean = favoriteDao.isFavorite(songId)

    // --- Recently Played ---
    fun getRecentlyPlayed(): Flow<List<Song>> = recentlyPlayedDao.getAll().map { list ->
        list.map { it.toSong() }
    }

    fun getOftenPlayed(): Flow<List<Song>> = recentlyPlayedDao.getOftenPlayed().map { list ->
        list.map { it.toSong() }
    }

    suspend fun addRecentlyPlayed(song: Song) {
        recentlyPlayedDao.upsertAndTrim(song.toRecentlyPlayedEntity())
    }

    suspend fun clearRecentlyPlayed() {
        recentlyPlayedDao.clearAll()
    }

    // --- Playlists ---
    /**
     * Charge playlists + toutes les entrées en **2 Flux** (pas 1 requête par playlist).
     * Les songs sont groupées en mémoire — adapté aux bibliothèques locales typiques.
     */
    fun getAllPlaylists(): Flow<List<Playlist>> {
        return combine(
            playlistDao.getAllPlaylists(),
            playlistDao.getAllPlaylistSongs()
        ) { entities, allSongs ->
            val byPlaylist = allSongs.groupBy { it.playlistId }
            entities.map { entity ->
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    songs = byPlaylist[entity.id]
                        .orEmpty()
                        .map { it.toSong() }
                )
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

    suspend fun createPlaylist(name: String): Long {
        val id = System.currentTimeMillis()
        playlistDao.insertPlaylist(PlaylistEntity(id, name))
        return id
    }

    suspend fun deletePlaylist(playlistId: Long) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
        // FK ON DELETE CASCADE retire playlist_songs automatiquement.
        playlistDao.deletePlaylistWithSongs(playlistId)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
        playlistDao.renamePlaylist(playlistId, newName)
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: Song, position: Int) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
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

    suspend fun addSongsToPlaylist(playlistId: Long, songs: List<Song>, startPosition: Int) {
        if (SmartPlaylistIds.isSmart(playlistId) || songs.isEmpty()) return
        db.withTransaction {
            val existingIds = playlistDao.getSongIdsForPlaylist(playlistId).toHashSet()
            var position = startPosition
            val toInsert = ArrayList<PlaylistSongEntity>(songs.size)
            for (song in songs) {
                if (song.id in existingIds) continue
                toInsert.add(
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
                existingIds.add(song.id)
                position++
            }
            if (toInsert.isNotEmpty()) {
                playlistDao.insertSongs(toInsert)
            }
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
        playlistDao.removeSong(playlistId, songId)
    }

    suspend fun getFavoritesSnapshot(): List<Song> =
        favoriteDao.getAllOnce().map { it.toSong() }

    suspend fun getPlaylistsSnapshot(): List<Playlist> {
        val entities = playlistDao.getAllPlaylistsOnce()
        val allSongs = playlistDao.getAllPlaylistSongsOnce()
        val byPlaylist = allSongs.groupBy { it.playlistId }
        return entities.map { entity ->
            Playlist(
                id = entity.id,
                name = entity.name,
                songs = byPlaylist[entity.id]
                    .orEmpty()
                    .map { it.toSong() }
            )
        }
    }

    suspend fun replaceLibraryData(favorites: List<Song>, playlists: List<Playlist>) {
        db.withTransaction {
            favoriteDao.clearAll()
            if (favorites.isNotEmpty()) {
                favoriteDao.insertAll(favorites.map { it.toFavoriteEntity() })
            }

            // CASCADE : supprimer les playlists retire aussi playlist_songs.
            playlistDao.clearAllPlaylists()
            if (playlists.isEmpty()) return@withTransaction

            playlistDao.insertPlaylists(playlists.map { PlaylistEntity(it.id, it.name) })
            val songRows = playlists.flatMap { playlist ->
                playlist.songs.mapIndexed { index, song ->
                    PlaylistSongEntity(
                        playlistId = playlist.id,
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        uri = song.uri.toString(),
                        albumArtUri = song.albumArtUri?.toString(),
                        position = index
                    )
                }
            }
            if (songRows.isNotEmpty()) {
                playlistDao.insertSongs(songRows)
            }
        }
    }

    /**
     * Retire les références Room vers des MediaStore IDs absents du scan courant.
     * Ne fait rien si [validSongIds] est vide (scan échoué / permissions) pour ne pas
     * vider favoris/playlists par accident.
     */
    suspend fun purgeOrphanSongReferences(validSongIds: Set<Long>) {
        if (validSongIds.isEmpty()) return

        db.withTransaction {
            deleteOrphanIds(favoriteDao.getAllSongIds(), validSongIds) { favoriteDao.deleteByIds(it) }
            deleteOrphanIds(recentlyPlayedDao.getAllSongIds(), validSongIds) {
                recentlyPlayedDao.deleteByIds(it)
            }
            deleteOrphanIds(playlistDao.getAllSongIds(), validSongIds) {
                playlistDao.removeSongsByIds(it)
            }
            deleteOrphanIds(metadataOverrideDao.getAllSongIds(), validSongIds) {
                metadataOverrideDao.deleteByIds(it)
            }
        }
    }

    private suspend fun deleteOrphanIds(
        presentIds: List<Long>,
        validSongIds: Set<Long>,
        deleteChunk: suspend (List<Long>) -> Unit
    ) {
        val orphans = presentIds.filter { it !in validSongIds }
        if (orphans.isEmpty()) return
        // Limite variables SQLite (~999) : chunks de 400.
        orphans.chunked(ORPHAN_DELETE_CHUNK).forEach { chunk -> deleteChunk(chunk) }
    }

    // --- Metadata overrides ---
    fun getMetadataOverrides(): Flow<Map<Long, MetadataOverrideEntity>> =
        metadataOverrideDao.getAll().map { list ->
            list.associateBy { it.songId }
        }

    suspend fun saveMetadataOverride(
        songId: Long,
        title: String?,
        artist: String?,
        album: String?
    ) {
        db.withTransaction {
            val existing = metadataOverrideDao.getBySongId(songId)
            metadataOverrideDao.upsert(
                MetadataOverrideEntity(
                    songId = songId,
                    title = title?.takeIf { it.isNotBlank() } ?: existing?.title,
                    artist = artist?.takeIf { it.isNotBlank() } ?: existing?.artist,
                    album = album?.takeIf { it.isNotBlank() } ?: existing?.album,
                    coverArtUri = existing?.coverArtUri
                )
            )
        }
    }

    suspend fun saveCoverArtOverride(songId: Long, coverArtUri: String) {
        db.withTransaction {
            val existing = metadataOverrideDao.getBySongId(songId)
            metadataOverrideDao.upsert(
                MetadataOverrideEntity(
                    songId = songId,
                    title = existing?.title,
                    artist = existing?.artist,
                    album = existing?.album,
                    coverArtUri = coverArtUri
                )
            )
        }
    }

    companion object {
        private const val ORPHAN_DELETE_CHUNK = 400
    }
}