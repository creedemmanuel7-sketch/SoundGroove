package com.credo.soundgroove.library

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.credo.soundgroove.MetadataOverrideEntity
import com.credo.soundgroove.SoundGrooveDatabase
import com.credo.soundgroove.data.SmartPlaylistBuilder
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.SmartPlaylistIds
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.data.repository.CombinedMusicRepository
import com.credo.soundgroove.data.repository.DatabaseRepository
import com.credo.soundgroove.lyrics.LyricsAvailability
import com.credo.soundgroove.lyrics.LyricsRepository
import com.credo.soundgroove.util.CoverArtStorage
import com.credo.soundgroove.util.MetadataEditor
import com.credo.soundgroove.util.PlaybackPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestion de la bibliothèque locale — extrait de [com.credo.soundgroove.viewmodel.SoundGrooveViewModel]
 * pour alléger le ViewModel racine. Couvre scan, favoris, playlists, métadonnées et dossiers masqués.
 */
class LibraryManager(
    private val application: Application,
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences,
    private val db: SoundGrooveDatabase = SoundGrooveDatabase.getInstance(application),
    private val dbRepository: DatabaseRepository = DatabaseRepository(db),
    private val combinedMusicRepository: CombinedMusicRepository = CombinedMusicRepository(application),
) {
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _hiddenFolders = MutableStateFlow(loadHiddenFolders())
    val hiddenFolders: StateFlow<Set<String>> = _hiddenFolders.asStateFlow()

    val songs: StateFlow<List<Song>> = combine(_allSongs, _hiddenFolders) { all, hidden ->
        filterSongsByHiddenFolders(all, hidden)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _songsWithLyrics = MutableStateFlow<List<Song>>(emptyList())
    val songsWithLyrics: StateFlow<List<Song>> = _songsWithLyrics.asStateFlow()

    private val _metadataOverrides = MutableStateFlow<Map<Long, MetadataOverrideEntity>>(emptyMap())
    val metadataOverrides: StateFlow<Map<Long, MetadataOverrideEntity>> = _metadataOverrides.asStateFlow()

    private val _libraryFolderUris = MutableStateFlow(combinedMusicRepository.getFolderUris())
    val libraryFolderUris: StateFlow<Set<Uri>> = _libraryFolderUris.asStateFlow()

    private val _playlistMessage = MutableStateFlow<String?>(null)
    val playlistMessage: StateFlow<String?> = _playlistMessage.asStateFlow()

    private val _metadataEditMessage = MutableStateFlow<String?>(null)
    val metadataEditMessage: StateFlow<String?> = _metadataEditMessage.asStateFlow()

    private val _sortMode = MutableStateFlow(prefs.getInt(KEY_LIBRARY_SORT_MODE, 0).coerceIn(0, 3))
    val sortMode: StateFlow<Int> = _sortMode.asStateFlow()

    val sortedSongs: StateFlow<List<Song>> = combine(songs, _sortMode) { visibleSongs, mode ->
        sortSongs(visibleSongs, mode)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Callback optionnel après rechargement bibliothèque (ex. resync player). */
    var onSongsReloaded: (() -> Unit)? = null

    /** Callback après changement overrides métadonnées. */
    var onMetadataRefresh: (() -> Unit)? = null

    init {
        observeDatabase()
    }

    fun syncSongs(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            _allSongs.value = songs
        }
    }

    fun reloadMusic(onError: (String) -> Unit = {}) {
        scope.launch {
            try {
                val loadedSongs = combinedMusicRepository.getAllSongs().map { applyMetadataOverride(it) }
                _allSongs.value = loadedSongs
                if (loadedSongs.isNotEmpty()) {
                    val validIds = loadedSongs.mapTo(HashSet(loadedSongs.size)) { it.id }
                    withContext(Dispatchers.IO) {
                        dbRepository.purgeOrphanSongReferences(validIds)
                        PlaybackPreferences.pruneOrphanTrackEqualizerPresets(application, validIds)
                        CoverArtStorage.deleteOrphans(application, validIds)
                    }
                }
                onSongsReloaded?.invoke()
            } catch (t: Throwable) {
                Log.e(TAG, "reloadMusic failed", t)
                onError("Scan bibliothèque interrompu — réessayez depuis Réglages")
            }
        }
    }

    fun applyMetadataOverride(song: Song): Song {
        val override = _metadataOverrides.value[song.id] ?: return song
        return song.copy(
            title = override.title ?: song.title,
            artist = override.artist ?: song.artist,
            albumName = override.album ?: song.albumName,
            albumArtUri = override.coverArtUri?.let { Uri.parse(it) } ?: song.albumArtUri
        )
    }

    fun displaySong(song: Song): Song = applyMetadataOverride(song)

    fun findSongById(id: Long): Song? = _allSongs.value.find { it.id == id }

    fun findSongByUri(uri: String): Song? =
        _allSongs.value.find { it.uri.toString() == uri }

    fun updateSortMode(mode: Int) {
        val safe = mode.coerceIn(0, 3)
        _sortMode.value = safe
        prefs.edit().putInt(KEY_LIBRARY_SORT_MODE, safe).apply()
    }

    fun hideFolder(folderPath: String) {
        val updated = _hiddenFolders.value + folderPath
        _hiddenFolders.value = updated
        saveHiddenFolders(updated)
    }

    fun unhideFolder(folderPath: String) {
        val updated = _hiddenFolders.value - folderPath
        _hiddenFolders.value = updated
        saveHiddenFolders(updated)
    }

    fun addLibraryFolder(treeUri: Uri) {
        combinedMusicRepository.addFolderUri(treeUri)
        _libraryFolderUris.value = combinedMusicRepository.getFolderUris()
        reloadMusic()
    }

    fun removeLibraryFolder(treeUri: Uri) {
        combinedMusicRepository.removeFolderUri(treeUri)
        _libraryFolderUris.value = combinedMusicRepository.getFolderUris()
        reloadMusic()
    }

    fun toggleFavorite(song: Song) {
        scope.launch { dbRepository.toggleFavorite(song) }
    }

    fun createPlaylist(name: String, onCreated: ((Long) -> Unit)? = null) {
        scope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val id = dbRepository.createPlaylist(trimmed)
            _playlistMessage.value = "Playlist « $trimmed » créée"
            onCreated?.invoke(id)
        }
    }

    fun clearPlaylistMessage() {
        _playlistMessage.value = null
    }

    fun deletePlaylist(playlistId: Long) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
        scope.launch { dbRepository.deletePlaylist(playlistId) }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song, position: Int = 0) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
        scope.launch {
            dbRepository.addSongToPlaylist(playlistId, song, position)
            _playlistMessage.value = "1 titre ajouté à la playlist"
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songs: List<Song>, startPosition: Int = 0) {
        if (SmartPlaylistIds.isSmart(playlistId) || songs.isEmpty()) return
        scope.launch {
            dbRepository.addSongsToPlaylist(playlistId, songs, startPosition)
            _playlistMessage.value = when (songs.size) {
                1 -> "1 titre ajouté à la playlist"
                else -> "${songs.size} titres ajoutés à la playlist"
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
        scope.launch { dbRepository.removeSongFromPlaylist(playlistId, songId) }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
        scope.launch { dbRepository.renamePlaylist(playlistId, newName) }
    }

    fun clearRecentlyPlayed() {
        scope.launch { dbRepository.clearRecentlyPlayed() }
    }

    fun clearMetadataEditMessage() {
        _metadataEditMessage.value = null
    }

    fun recordRecentlyPlayed(song: Song) {
        scope.launch { dbRepository.addRecentlyPlayed(song) }
    }

    fun mapSongsWithOverride(songs: List<Song>): List<Song> =
        songs.map { applyMetadataOverride(it) }

    fun patchSongInCatalog(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        albumArtUri: Uri? = null,
    ) {
        _allSongs.value = _allSongs.value.map { s ->
            if (s.id == songId) {
                applyMetadataOverride(
                    s.copy(
                        title = title,
                        artist = artist,
                        albumName = album,
                        albumArtUri = albumArtUri ?: s.albumArtUri,
                    )
                )
            } else s
        }
        _favoriteSongs.value = _favoriteSongs.value.map { s ->
            if (s.id == songId) applyMetadataOverride(s.copy(title = title, artist = artist, albumName = album, albumArtUri = albumArtUri ?: s.albumArtUri)) else s
        }
        _recentlyPlayed.value = _recentlyPlayed.value.map { s ->
            if (s.id == songId) applyMetadataOverride(s.copy(title = title, artist = artist, albumName = album, albumArtUri = albumArtUri ?: s.albumArtUri)) else s
        }
        _playlists.value = _playlists.value.map { playlist ->
            playlist.copy(
                songs = playlist.songs.map { s ->
                    if (s.id == songId) applyMetadataOverride(s.copy(title = title, artist = artist, albumName = album, albumArtUri = albumArtUri ?: s.albumArtUri)) else s
                }
            )
        }
    }

    fun saveSongMetadata(song: Song, title: String, artist: String, album: String) {
        scope.launch {
            runCatching {
                dbRepository.saveMetadataOverride(song.id, title, artist, album)
                val result = MetadataEditor.tryWriteToMediaStore(
                    application,
                    song,
                    title.trim(),
                    artist.trim(),
                    album.trim()
                )
                _metadataEditMessage.value = result.message
                patchSongInCatalog(
                    songId = song.id,
                    title = title.trim(),
                    artist = artist.trim(),
                    album = album.trim(),
                )
            }.onFailure {
                _metadataEditMessage.value = "Impossible d'enregistrer les métadonnées. Réessaie."
            }
        }
    }

    fun saveSongCoverArt(song: Song, sourceUri: Uri, onSaved: (Song) -> Unit = {}) {
        scope.launch {
            runCatching {
                if (!CoverArtStorage.isAcceptableImageSource(application, sourceUri)) {
                    _metadataEditMessage.value =
                        "Format d'image non pris en charge. Utilise JPG, JPEG, PNG ou WebP."
                    return@launch
                }
                val savedUri = CoverArtStorage.saveFromUri(application, song.id, sourceUri)
                if (savedUri == null) {
                    _metadataEditMessage.value =
                        "Impossible de lire cette image (JPG, JPEG, PNG ou WebP). Choisis une autre photo."
                    return@launch
                }
                dbRepository.saveCoverArtOverride(song.id, savedUri.toString())
                val updated = song.copy(albumArtUri = savedUri)
                patchSongInCatalog(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.albumName,
                    albumArtUri = savedUri,
                )
                onSaved(updated)
                _metadataEditMessage.value = "Pochette mise à jour."
            }.onFailure {
                _metadataEditMessage.value = "Impossible d'enregistrer la pochette. Réessaie."
            }
        }
    }

    fun getHiddenFoldersSnapshot(): Set<String> = _hiddenFolders.value

    fun restoreHiddenFolders(folders: Set<String>) {
        _hiddenFolders.value = folders
        saveHiddenFolders(folders)
    }

    private fun observeDatabase() {
        scope.launch {
            combine(dbRepository.getFavorites(), _metadataOverrides) { favs, _ ->
                favs.map { applyMetadataOverride(it) }
            }.collect { _favoriteSongs.value = it }
        }
        scope.launch {
            combine(dbRepository.getRecentlyPlayed(), _metadataOverrides) { recent, _ ->
                recent.map { applyMetadataOverride(it) }
            }.collect { _recentlyPlayed.value = it }
        }
        scope.launch {
            combine(_allSongs, LyricsAvailability.revision, _metadataOverrides) { songs, _, _ ->
                songs.map { applyMetadataOverride(it) }
            }.collectLatest { songs ->
                _songsWithLyrics.value = withContext(Dispatchers.IO) {
                    LyricsRepository.filterSongsWithLyrics(application, songs)
                }
            }
        }
        scope.launch {
            combine(
                dbRepository.getAllPlaylists(),
                dbRepository.getRecentlyPlayed(),
                dbRepository.getOftenPlayed(),
                _songsWithLyrics,
                _metadataOverrides
            ) { manualPlaylists, recent, often, withLyrics, _ ->
                val applyOverride: (Song) -> Song = { applyMetadataOverride(it) }
                SmartPlaylistBuilder.merge(
                    manualPlaylists = manualPlaylists.map { playlist ->
                        playlist.copy(songs = playlist.songs.map(applyOverride))
                    },
                    recentlyPlayed = recent.map(applyOverride),
                    oftenPlayed = often.map(applyOverride),
                    withLyrics = withLyrics
                )
            }.collect { _playlists.value = it }
        }
        scope.launch {
            dbRepository.getMetadataOverrides().collect { overrides ->
                _metadataOverrides.value = overrides
                _allSongs.value = _allSongs.value.map { applyMetadataOverride(it) }
                onMetadataRefresh?.invoke()
            }
        }
    }

    private fun loadHiddenFolders(): Set<String> {
        val stored = prefs.getStringSet("hidden_folders", emptySet()) ?: emptySet()
        return stored.toSet()
    }

    private fun saveHiddenFolders(folders: Set<String>) {
        prefs.edit().putStringSet("hidden_folders", folders).apply()
    }

    private fun folderKey(song: Song): String =
        song.folderPath.takeIf { it.isNotBlank() } ?: "Dossier inconnu"

    private fun filterSongsByHiddenFolders(all: List<Song>, hidden: Set<String>): List<Song> =
        if (hidden.isEmpty()) all else all.filter { folderKey(it) !in hidden }

    private fun sortSongs(songs: List<Song>, mode: Int): List<Song> =
        when (mode) {
            0 -> songs.sortedBy { it.title.lowercase() }
            1 -> songs.sortedByDescending { it.title.lowercase() }
            2 -> songs.sortedBy { it.artist.lowercase() }
            3 -> songs.sortedByDescending { it.dateAdded }
            else -> songs
        }

    companion object {
        private const val TAG = "LibraryManager"
        private const val KEY_LIBRARY_SORT_MODE = "library_sort_mode"
    }
}
