package com.credo.soundgroove.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.credo.soundgroove.PlaybackService
import com.credo.soundgroove.SoundGrooveDatabase
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.data.repository.DatabaseRepository
import com.credo.soundgroove.data.repository.MusicRepository
import com.credo.soundgroove.data.repository.SearchHistoryRepository
import com.credo.soundgroove.data.backup.BackupManager
import com.credo.soundgroove.data.backup.BackupSnapshot
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.net.Uri
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.credo.soundgroove.notifications.SmartNotificationManager
import com.credo.soundgroove.ui.theme.AppTheme

class SoundGrooveViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("soundgroove_prefs", android.content.Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(
        try {
            AppTheme.valueOf(prefs.getString("selected_theme", AppTheme.NOIR_ABSOLU.name) ?: AppTheme.NOIR_ABSOLU.name)
        } catch (e: Exception) {
            AppTheme.NOIR_ABSOLU
        }
    )
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    private val _showThemeSelection = MutableStateFlow(!prefs.contains("selected_theme"))
    val showThemeSelection: StateFlow<Boolean> = _showThemeSelection.asStateFlow()

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun completeThemeSelection(theme: AppTheme) {
        setTheme(theme)
        _showThemeSelection.value = false
    }

    private val db = SoundGrooveDatabase.getInstance(application)
    private val dbRepository = DatabaseRepository(
        db.favoriteDao(),
        db.recentlyPlayedDao(),
        db.playlistDao()
    )
    private val searchHistoryRepository = SearchHistoryRepository(application)
    private val backupManager = BackupManager(application)
    private val musicRepository = MusicRepository(application)

    private val _recentSearches = MutableStateFlow(searchHistoryRepository.getRecentSearches())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _hiddenFolders = MutableStateFlow(loadHiddenFolders())
    val hiddenFolders: StateFlow<Set<String>> = _hiddenFolders.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    // --- MediaController ---
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController: StateFlow<MediaController?> = _mediaController.asStateFlow()

    // --- State ---
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = combine(_allSongs, _hiddenFolders) { all, hidden ->
        filterSongsByHiddenFolders(all, hidden)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // Player State
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    // UI State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null

    private val _playbackSpeed = MutableStateFlow(prefs.getFloat("playback_speed", 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _smartNotificationsEnabled = MutableStateFlow(prefs.getBoolean("smart_notifications_enabled", true))
    val smartNotificationsEnabled: StateFlow<Boolean> = _smartNotificationsEnabled.asStateFlow()

    private val _persistentMiniPlayerEnabled = MutableStateFlow(prefs.getBoolean("persistent_miniplayer_enabled", true))
    val persistentMiniPlayerEnabled: StateFlow<Boolean> = _persistentMiniPlayerEnabled.asStateFlow()

    private val _performanceModeEnabled = MutableStateFlow(prefs.getBoolean("performance_mode_enabled", false))
    val performanceModeEnabled: StateFlow<Boolean> = _performanceModeEnabled.asStateFlow()
    
    private val _sortMode = MutableStateFlow(0)
    val sortMode: StateFlow<Int> = _sortMode.asStateFlow()

    val sortedSongs: StateFlow<List<Song>> = combine(songs, _sortMode) { visibleSongs, mode ->
        sortSongs(visibleSongs, mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var resumeReminderJob: Job? = null
    private var continuousPlaySeconds = 0
    private var sessionSummaryShown = false

    private val _mainSelectedTab = MutableStateFlow(0)
    val mainSelectedTab: StateFlow<Int> = _mainSelectedTab.asStateFlow()

    private val _librarySelectedTab = MutableStateFlow(0)
    val librarySelectedTab: StateFlow<Int> = _librarySelectedTab.asStateFlow()

    private val _totalListeningSeconds = MutableStateFlow(prefs.getLong("total_listening_seconds", 0L))
    val totalListeningSeconds: StateFlow<Long> = _totalListeningSeconds.asStateFlow()

    init {
        initMediaController()
        loadMusic()
        observeDatabase()
    }

    fun formatListeningTime(seconds: Long = _totalListeningSeconds.value): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 -> "${hours} h ${minutes.toString().padStart(2, '0')} min"
            minutes > 0 -> "$minutes min"
            else -> "< 1 min"
        }
    }

    fun syncSongs(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            _allSongs.value = songs
            updateCurrentSongFromMediaItem(_mediaController.value?.currentMediaItem)
        }
    }

    fun reloadMusic() {
        loadMusic()
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(getApplication(), ComponentName(getApplication(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            _mediaController.value = controller
            controller?.setPlaybackSpeed(_playbackSpeed.value)
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        resumeReminderJob?.cancel()
                        continuousPlaySeconds = 0
                        sessionSummaryShown = false
                    } else {
                        maybeShowSessionSummaryIfEnabled()
                        scheduleResumeReminderIfEnabled()
                    }
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateCurrentSongFromMediaItem(mediaItem)
                }
            })
            updateCurrentSongFromMediaItem(controller?.currentMediaItem)
            _isPlaying.value = controller?.isPlaying == true
            startProgressUpdate()
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (true) {
                val controller = _mediaController.value
                _playbackPosition.value = controller?.currentPosition ?: 0L
                if (controller?.isPlaying == true) {
                    val updated = _totalListeningSeconds.value + 1
                    _totalListeningSeconds.value = updated
                    prefs.edit().putLong("total_listening_seconds", updated).apply()
                    continuousPlaySeconds++
                    if (_smartNotificationsEnabled.value &&
                        !sessionSummaryShown &&
                        continuousPlaySeconds >= SESSION_SUMMARY_THRESHOLD_SECONDS
                    ) {
                        sessionSummaryShown = true
                        SmartNotificationManager.showSessionSummary(
                            getApplication(),
                            continuousPlaySeconds / 60
                        )
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun scheduleResumeReminderIfEnabled() {
        if (!_smartNotificationsEnabled.value) return
        resumeReminderJob?.cancel()
        val song = _currentSong.value ?: return
        resumeReminderJob = viewModelScope.launch {
            delay(RESUME_REMINDER_DELAY_MS)
            if (!_isPlaying.value && _currentSong.value?.id == song.id) {
                SmartNotificationManager.showResumeReminder(getApplication(), song)
            }
        }
    }

    private fun maybeShowSessionSummaryIfEnabled() {
        if (!_smartNotificationsEnabled.value || sessionSummaryShown) return
        if (continuousPlaySeconds >= SESSION_SUMMARY_THRESHOLD_SECONDS) {
            sessionSummaryShown = true
            SmartNotificationManager.showSessionSummary(
                getApplication(),
                continuousPlaySeconds / 60
            )
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
        if (hidden.isEmpty()) all
        else all.filter { folderKey(it) !in hidden }

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

    fun addRecentSearch(query: String) {
        searchHistoryRepository.addSearch(query)
        _recentSearches.value = searchHistoryRepository.getRecentSearches()
    }

    fun clearSearchHistory() {
        searchHistoryRepository.clearHistory()
        _recentSearches.value = emptyList()
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val snapshot = BackupSnapshot(
                    theme = _currentTheme.value,
                    favorites = dbRepository.getFavoritesSnapshot(),
                    playlists = dbRepository.getPlaylistsSnapshot()
                )
                val json = backupManager.serialize(snapshot)
                backupManager.writeToUri(uri, json)
                "Sauvegarde exportée avec succès."
            }.onSuccess { message ->
                _backupMessage.value = message
            }.onFailure { error ->
                _backupMessage.value = error.message ?: "Échec de l'exportation."
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = backupManager.readFromUri(uri)
                val snapshot = backupManager.parse(json)
                dbRepository.replaceLibraryData(snapshot.favorites, snapshot.playlists)
                snapshot.theme?.let { setTheme(it) }
                "Restauration terminée : ${snapshot.favorites.size} favori(s), ${snapshot.playlists.size} playlist(s)."
            }.onSuccess { message ->
                _backupMessage.value = message
            }.onFailure { error ->
                _backupMessage.value = error.message ?: "Échec de la restauration."
            }
        }
    }

    private fun sortSongs(songs: List<Song>, mode: Int): List<Song> =
        when (mode) {
            0 -> songs.sortedBy { it.title.lowercase() }
            1 -> songs.sortedByDescending { it.title.lowercase() }
            2 -> songs.sortedBy { it.artist.lowercase() }
            3 -> songs.sortedByDescending { it.dateAdded }
            else -> songs
        }

    private fun loadMusic() {
        viewModelScope.launch {
            val loadedSongs = musicRepository.getSongs()
            _allSongs.value = loadedSongs
            updateCurrentSongFromMediaItem(_mediaController.value?.currentMediaItem)
        }
    }

    private fun songToMediaItem(song: Song): MediaItem {
        val title = song.title.takeIf { it.isNotBlank() } ?: (song.uri.lastPathSegment ?: "Titre inconnu")
        val artist = song.artist.takeIf { it.isNotBlank() } ?: "Artiste inconnu"
        val album = song.albumName.takeIf { it.isNotBlank() } ?: "Album inconnu"

        return MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(song.albumArtUri)
                    .build()
            )
            .build()
    }

    private fun updateCurrentSongFromMediaItem(mediaItem: MediaItem?) {
        val item = mediaItem ?: return
        val uriStr = item.mediaId
        val resolved = _allSongs.value.find { it.uri.toString() == uriStr }
            ?: item.localConfiguration?.uri?.let { uri -> _allSongs.value.find { it.uri == uri } }

        resolved?.let { song ->
            if (_currentSong.value?.id != song.id) {
                _currentSong.value = song
                viewModelScope.launch {
                    dbRepository.addRecentlyPlayed(song)
                }
            }
        }
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            dbRepository.getFavorites().collect { _favoriteSongs.value = it }
        }
        viewModelScope.launch {
            dbRepository.getRecentlyPlayed().collect { _recentlyPlayed.value = it }
        }
        viewModelScope.launch {
            dbRepository.getAllPlaylists().collect { _playlists.value = it }
        }
    }

    // --- Player Actions ---
    fun playSong(song: Song) {
        playSongs(songs.value, song)
    }

    fun playSongs(queue: List<Song>, startSong: Song) {
        val controller = _mediaController.value ?: return
        val safeQueue = queue.ifEmpty { listOf(startSong) }
        val items = safeQueue.map { songToMediaItem(it) }
        val index = safeQueue.indexOf(startSong)
        if (index != -1) {
            controller.setMediaItems(items)
            controller.seekTo(index, 0)
            controller.prepare()
            controller.play()
            _currentSong.value = startSong
            _isPlaying.value = true
        }
    }
    
    fun playPlaylist(playlist: Playlist, startSong: Song? = null) {
        val controller = _mediaController.value ?: return
        val items = playlist.songs.map { songToMediaItem(it) }
        controller.setMediaItems(items)
        
        val index = startSong?.let { playlist.songs.indexOf(it) }?.takeIf { it != -1 } ?: 0
        controller.seekTo(index, 0)
        controller.prepare()
        controller.play()
        playlist.songs.getOrNull(index)?.let { _currentSong.value = it }
        _isPlaying.value = true
    }

    fun togglePlayPause() {
        if (_isPlaying.value) _mediaController.value?.pause() else _mediaController.value?.play()
    }

    fun skipNext() {
        val controller = _mediaController.value ?: return
        if (controller.mediaItemCount > 0 &&
            controller.currentMediaItemIndex < controller.mediaItemCount - 1
        ) {
            controller.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val controller = _mediaController.value ?: return
        if (controller.mediaItemCount > 0 && controller.currentMediaItemIndex > 0) {
            controller.seekToPreviousMediaItem()
        }
    }
    fun seekTo(position: Long) = _mediaController.value?.seekTo(position)

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        prefs.edit().putFloat("playback_speed", clamped).apply()
        _mediaController.value?.setPlaybackSpeed(clamped)
    }

    fun setSmartNotificationsEnabled(enabled: Boolean) {
        _smartNotificationsEnabled.value = enabled
        prefs.edit().putBoolean("smart_notifications_enabled", enabled).apply()
        if (!enabled) {
            resumeReminderJob?.cancel()
            SmartNotificationManager.cancelAll(getApplication())
        } else if (!_isPlaying.value) {
            scheduleResumeReminderIfEnabled()
        }
    }

    fun setPersistentMiniPlayerEnabled(enabled: Boolean) {
        _persistentMiniPlayerEnabled.value = enabled
        prefs.edit().putBoolean("persistent_miniplayer_enabled", enabled).apply()
    }

    fun setPerformanceModeEnabled(enabled: Boolean) {
        _performanceModeEnabled.value = enabled
        prefs.edit().putBoolean("performance_mode_enabled", enabled).apply()
    }

    fun playNext(song: Song) {
        val controller = _mediaController.value ?: return
        val item = songToMediaItem(song)
        if (controller.mediaItemCount == 0) {
            controller.setMediaItems(listOf(item))
            controller.prepare()
        } else {
            val insertAt = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
            controller.addMediaItem(insertAt, item)
        }
    }

    fun addToQueue(song: Song) {
        val controller = _mediaController.value ?: return
        val item = songToMediaItem(song)
        if (controller.mediaItemCount == 0) {
            controller.setMediaItems(listOf(item))
            controller.prepare()
        } else {
            controller.addMediaItem(item)
        }
    }

    // --- Database Actions ---
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            dbRepository.toggleFavorite(song)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            dbRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            dbRepository.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song, position: Int = 0) {
        viewModelScope.launch {
            dbRepository.addSongToPlaylist(playlistId, song, position)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            dbRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            dbRepository.renamePlaylist(playlistId, newName)
        }
    }

    fun clearRecentlyPlayed() {
        viewModelScope.launch {
            dbRepository.clearRecentlyPlayed()
        }
    }


    // --- UI Actions ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun updateSortMode(mode: Int) {
        _sortMode.value = mode
    }

    fun updateMainSelectedTab(tab: Int) {
        _mainSelectedTab.value = tab
    }

    fun updateLibrarySelectedTab(tab: Int) {
        _librarySelectedTab.value = tab
    }

    // --- Sleep Timer ---
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            _sleepTimerRemainingSeconds.value = minutes * 60
            sleepTimerJob = viewModelScope.launch {
                while (_sleepTimerRemainingSeconds.value!! > 0) {
                    delay(1000L)
                    _sleepTimerRemainingSeconds.value = _sleepTimerRemainingSeconds.value!! - 1
                }
                mediaController.value?.pause()
                _sleepTimerRemainingSeconds.value = null
            }
        } else {
            _sleepTimerRemainingSeconds.value = null
        }
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingSeconds.value = -1
        sleepTimerJob = viewModelScope.launch {
            val controller = mediaController.value ?: run {
                _sleepTimerRemainingSeconds.value = null
                return@launch
            }
            while (true) {
                val dur = controller.duration
                val pos = controller.currentPosition
                if (dur > 0 && pos >= dur - 800) {
                    controller.pause()
                    _sleepTimerRemainingSeconds.value = null
                    break
                }
                if (controller.playbackState == Player.STATE_ENDED) {
                    controller.pause()
                    _sleepTimerRemainingSeconds.value = null
                    break
                }
                delay(500L)
            }
        }
    }

    fun cancelSleepTimer() = setSleepTimer(0)

    override fun onCleared() {
        super.onCleared()
        resumeReminderJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    companion object {
        private const val RESUME_REMINDER_DELAY_MS = 15 * 60 * 1000L
        private const val SESSION_SUMMARY_THRESHOLD_SECONDS = 20 * 60
    }
}
