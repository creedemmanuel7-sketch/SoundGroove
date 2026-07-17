package com.credo.soundgroove.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.credo.soundgroove.PlaybackService
import com.credo.soundgroove.SoundGrooveDatabase
import com.credo.soundgroove.data.SmartPlaylistBuilder
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.SmartPlaylistIds
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.data.repository.DatabaseRepository
import com.credo.soundgroove.data.repository.ListeningStats
import com.credo.soundgroove.data.repository.ListeningStatsRepository
import com.credo.soundgroove.data.repository.MusicRepository
import com.credo.soundgroove.data.repository.SearchHistoryRepository
import com.credo.soundgroove.data.backup.BackupManager
import com.credo.soundgroove.data.backup.BackupSnapshot
import com.credo.soundgroove.lyrics.LyricsAvailability
import com.credo.soundgroove.lyrics.LyricsRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.net.Uri
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.credo.soundgroove.MetadataOverrideEntity
import com.credo.soundgroove.notifications.SmartNotificationManager
import com.credo.soundgroove.ui.theme.AppAccent
import com.credo.soundgroove.ui.theme.AppTheme
import com.credo.soundgroove.util.CoverArtStorage
import com.credo.soundgroove.util.EqualizerBandInfo
import com.credo.soundgroove.util.EqualizerManager
import com.credo.soundgroove.util.EqualizerPreset
import com.credo.soundgroove.util.MetadataEditor
import com.credo.soundgroove.util.PlaybackPreferences
import com.credo.soundgroove.util.PlayerGuards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

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

    private val _currentAccent = MutableStateFlow(
        AppAccent.fromId(prefs.getString("selected_accent", AppAccent.VIOLET.id))
    )
    val currentAccent: StateFlow<AppAccent> = _currentAccent.asStateFlow()

    private val _showThemeSelection = MutableStateFlow(!prefs.contains("selected_theme"))
    val showThemeSelection: StateFlow<Boolean> = _showThemeSelection.asStateFlow()

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun setAccent(accent: AppAccent) {
        _currentAccent.value = accent
        prefs.edit().putString("selected_accent", accent.id).apply()
    }

    fun completeThemeSelection(theme: AppTheme) {
        setTheme(theme)
        _showThemeSelection.value = false
    }

    private val db = SoundGrooveDatabase.getInstance(application)
    private val dbRepository = DatabaseRepository(
        db.favoriteDao(),
        db.recentlyPlayedDao(),
        db.playlistDao(),
        db.metadataOverrideDao()
    )
    private val searchHistoryRepository = SearchHistoryRepository(application)
    private val listeningStatsRepository = ListeningStatsRepository(application)
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

    private val _playlistMessage = MutableStateFlow<String?>(null)
    val playlistMessage: StateFlow<String?> = _playlistMessage.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _songsWithLyrics = MutableStateFlow<List<Song>>(emptyList())

    // Player State
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    /** File d'attente telle que lancée (album, suggestions, playlist, etc.). */
    private val _playbackQueue = MutableStateFlow<List<Song>>(emptyList())
    val playbackQueue: StateFlow<List<Song>> = _playbackQueue.asStateFlow()

    private val _playbackQueueIndex = MutableStateFlow(0)
    val playbackQueueIndex: StateFlow<Int> = _playbackQueueIndex.asStateFlow()

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

    private val _playbackSpeed = MutableStateFlow(prefs.getFloat(PlaybackPreferences.KEY_PLAYBACK_SPEED, 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(prefs.getFloat(PlaybackPreferences.KEY_PLAYBACK_PITCH, 1.0f))
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _gaplessEnabled = MutableStateFlow(prefs.getBoolean(PlaybackPreferences.KEY_GAPLESS_ENABLED, true))
    val gaplessEnabled: StateFlow<Boolean> = _gaplessEnabled.asStateFlow()

    private val _crossfadeDurationMs = MutableStateFlow(prefs.getInt(PlaybackPreferences.KEY_CROSSFADE_MS, 0))
    val crossfadeDurationMs: StateFlow<Int> = _crossfadeDurationMs.asStateFlow()

    private val _vinylModeEnabled = MutableStateFlow(prefs.getBoolean(PlaybackPreferences.KEY_VINYL_MODE_ENABLED, false))
    val vinylModeEnabled: StateFlow<Boolean> = _vinylModeEnabled.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(PlaybackPreferences.isEqualizerEnabled(getApplication()))
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(PlaybackPreferences.equalizerPreset(getApplication()))
    val equalizerPreset: StateFlow<EqualizerPreset> = _equalizerPreset.asStateFlow()

    private val _equalizerBands = MutableStateFlow<List<EqualizerBandInfo>>(emptyList())
    val equalizerBands: StateFlow<List<EqualizerBandInfo>> = _equalizerBands.asStateFlow()

    private val _metadataOverrides = MutableStateFlow<Map<Long, MetadataOverrideEntity>>(emptyMap())
    val metadataOverrides: StateFlow<Map<Long, MetadataOverrideEntity>> = _metadataOverrides.asStateFlow()

    private val _metadataEditMessage = MutableStateFlow<String?>(null)
    val metadataEditMessage: StateFlow<String?> = _metadataEditMessage.asStateFlow()

    private val _smartNotificationsEnabled = MutableStateFlow(prefs.getBoolean("smart_notifications_enabled", true))
    val smartNotificationsEnabled: StateFlow<Boolean> = _smartNotificationsEnabled.asStateFlow()

    private val _persistentMiniPlayerEnabled = MutableStateFlow(prefs.getBoolean("persistent_miniplayer_enabled", true))
    val persistentMiniPlayerEnabled: StateFlow<Boolean> = _persistentMiniPlayerEnabled.asStateFlow()

    private val _performanceModeEnabled = MutableStateFlow(prefs.getBoolean("performance_mode_enabled", false))
    val performanceModeEnabled: StateFlow<Boolean> = _performanceModeEnabled.asStateFlow()

    private val _albumCoverAccentEnabled = MutableStateFlow(prefs.getBoolean("album_cover_accent_enabled", false))
    val albumCoverAccentEnabled: StateFlow<Boolean> = _albumCoverAccentEnabled.asStateFlow()
    
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

    // Source de vérité UI : toujours dérivée de ListeningStatsRepository
    // (semaine/mois calendaires + total lifetime). Ne pas recalculer ailleurs.
    private val _listeningStats = MutableStateFlow(
        listeningStatsRepository.getStats(_totalListeningSeconds.value)
    )
    val listeningStats: StateFlow<ListeningStats> = _listeningStats.asStateFlow()

    init {
        initMediaController()
        loadMusic()
        observeDatabase()
    }

    fun formatListeningTime(seconds: Long = _totalListeningSeconds.value): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        return when {
            hours > 0 -> "${hours} h ${minutes.toString().padStart(2, '0')} min"
            minutes > 0 -> "$minutes min"
            safeSeconds > 0 -> "< 1 min"
            else -> "0 min"
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
            controller?.playbackParameters = PlaybackParameters(
                _playbackSpeed.value,
                _playbackPitch.value
            )
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
                    controller?.let { syncPlaybackQueueIndex(it.currentMediaItemIndex) }
                }
            })
            updateCurrentSongFromMediaItem(controller?.currentMediaItem)
            _isPlaying.value = controller?.isPlaying == true
            controller?.let { player ->
                syncPlaybackQueueIndex(player.currentMediaItemIndex)
                maybeRestorePlaybackQueueFromPlayer(player)
            }
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
                    _listeningStats.value = listeningStatsRepository.recordSecond(updated)
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
                    accent = _currentAccent.value,
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
                db.withTransaction {
                    dbRepository.replaceLibraryData(snapshot.favorites, snapshot.playlists)
                }
                snapshot.theme?.let { setTheme(it) }
                snapshot.accent?.let { setAccent(it) }
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
            val loadedSongs = musicRepository.getSongs().map { applyMetadataOverride(it) }
            _allSongs.value = loadedSongs
            updateCurrentSongFromMediaItem(_mediaController.value?.currentMediaItem)
        }
    }

    private fun applyMetadataOverride(song: Song): Song {
        val override = _metadataOverrides.value[song.id] ?: return song
        return song.copy(
            title = override.title ?: song.title,
            artist = override.artist ?: song.artist,
            albumName = override.album ?: song.albumName,
            albumArtUri = override.coverArtUri?.let { Uri.parse(it) } ?: song.albumArtUri
        )
    }

    fun displaySong(song: Song): Song = applyMetadataOverride(song)

    private fun songToMediaItem(song: Song): MediaItem {
        val display = applyMetadataOverride(song)
        val title = display.title.takeIf { it.isNotBlank() } ?: (display.uri.lastPathSegment ?: "Titre inconnu")
        val artist = display.artist.takeIf { it.isNotBlank() } ?: "Artiste inconnu"
        val album = display.albumName.takeIf { it.isNotBlank() } ?: "Album inconnu"

        return MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(display.albumArtUri)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
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
            combine(
                dbRepository.getFavorites(),
                _metadataOverrides
            ) { favs, _ -> favs.map { applyMetadataOverride(it) } }
                .collect { _favoriteSongs.value = it }
        }
        viewModelScope.launch {
            combine(
                dbRepository.getRecentlyPlayed(),
                _metadataOverrides
            ) { recent, _ -> recent.map { applyMetadataOverride(it) } }
                .collect { _recentlyPlayed.value = it }
        }
        viewModelScope.launch {
            combine(_allSongs, LyricsAvailability.revision, _metadataOverrides) { songs, _, _ ->
                songs.map { applyMetadataOverride(it) }
            }.collectLatest { songs ->
                _songsWithLyrics.value = withContext(Dispatchers.IO) {
                    LyricsRepository.filterSongsWithLyrics(getApplication(), songs)
                }
            }
        }
        viewModelScope.launch {
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
        viewModelScope.launch {
            dbRepository.getMetadataOverrides().collect { overrides ->
                _metadataOverrides.value = overrides
                _allSongs.value = _allSongs.value.map { applyMetadataOverride(it) }
                _currentSong.value?.let { current ->
                    _currentSong.value = applyMetadataOverride(current)
                }
                _playbackQueue.value = _playbackQueue.value.map { applyMetadataOverride(it) }
            }
        }
    }

    private fun setPlaybackContext(queue: List<Song>, startIndex: Int) {
        _playbackQueue.value = queue
        _playbackQueueIndex.value = startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    }

    private fun syncPlaybackQueueIndex(index: Int) {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return
        _playbackQueueIndex.value = index.coerceIn(0, queue.lastIndex)
    }

    /** Restaure la file depuis le player (ex. reprise de session) — jamais sur le thread UI. */
    private fun maybeRestorePlaybackQueueFromPlayer(player: Player) {
        if (_playbackQueue.value.isNotEmpty() || player.mediaItemCount <= 0) return
        viewModelScope.launch(Dispatchers.Default) {
            val rebuilt = PlayerGuards.rebuildPlaylistFromPlayer(player, _allSongs.value)
            if (_playbackQueue.value.isEmpty() && rebuilt.isNotEmpty()) {
                _playbackQueue.value = rebuilt
                _playbackQueueIndex.value = PlayerGuards.safeCurrentIndex(player)
            }
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
        val index = safeQueue.indexOfFirst { it.id == startSong.id }.takeIf { it >= 0 }
            ?: safeQueue.indexOf(startSong)
        if (index != -1) {
            setPlaybackContext(safeQueue, index)
            controller.setMediaItems(items)
            controller.seekTo(index, 0)
            controller.prepare()
            controller.play()
            _currentSong.value = startSong
            _isPlaying.value = true
        }
    }
    
    fun playPlaylist(playlist: Playlist, startSong: Song? = null) {
        if (playlist.songs.isEmpty()) return
        val controller = _mediaController.value ?: return
        val items = playlist.songs.map { songToMediaItem(it) }
        controller.setMediaItems(items)
        
        val index = startSong?.let { s ->
            playlist.songs.indexOfFirst { it.id == s.id }.takeIf { it >= 0 }
                ?: playlist.songs.indexOf(s)
        }?.takeIf { it != -1 } ?: 0
        setPlaybackContext(playlist.songs, index)
        controller.seekTo(index, 0)
        controller.prepare()
        controller.play()
        playlist.songs.getOrNull(index)?.let { _currentSong.value = it }
        _isPlaying.value = true
    }

    fun seekToQueueIndex(index: Int) {
        val controller = _mediaController.value ?: return
        if (!PlayerGuards.safeSeekToIndex(controller, index)) return
        controller.play()
        syncPlaybackQueueIndex(index)
    }

    fun removeFromPlaybackQueue(index: Int) {
        val controller = _mediaController.value ?: return
        if (index !in _playbackQueue.value.indices) return
        if (!PlayerGuards.safeRemoveMediaItem(controller, index)) return
        _playbackQueue.value = _playbackQueue.value.toMutableList().also { it.removeAt(index) }
        syncPlaybackQueueIndex(PlayerGuards.safeCurrentIndex(controller))
    }

    fun moveInPlaybackQueue(from: Int, to: Int) {
        val controller = _mediaController.value ?: return
        val queue = _playbackQueue.value
        if (from !in queue.indices || to !in queue.indices || from == to) return
        if (!PlayerGuards.safeMoveMediaItem(controller, from, to)) return
        _playbackQueue.value = queue.toMutableList().also { list ->
            val item = list.removeAt(from)
            list.add(to, item)
        }
        syncPlaybackQueueIndex(PlayerGuards.safeCurrentIndex(controller))
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
        if (controller.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
            controller.seekTo(0)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0)
        }
    }
    fun seekTo(position: Long) = _mediaController.value?.seekTo(position)

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        prefs.edit().putFloat(PlaybackPreferences.KEY_PLAYBACK_SPEED, clamped).apply()
        _mediaController.value?.setPlaybackSpeed(clamped)
    }

    fun setPlaybackPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _playbackPitch.value = clamped
        prefs.edit().putFloat(PlaybackPreferences.KEY_PLAYBACK_PITCH, clamped).apply()
        _mediaController.value?.playbackParameters = PlaybackParameters(
            _playbackSpeed.value,
            clamped
        )
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _gaplessEnabled.value = enabled
        prefs.edit().putBoolean(PlaybackPreferences.KEY_GAPLESS_ENABLED, enabled).apply()
        PlaybackService.instance?.refreshPlaybackSettings()
    }

    fun setCrossfadeDurationMs(ms: Int) {
        val valid = PlaybackPreferences.CROSSFADE_OPTIONS_MS.find { it == ms }
            ?: PlaybackPreferences.CROSSFADE_OPTIONS_MS.minByOrNull { kotlin.math.abs(it - ms) }
            ?: 0
        _crossfadeDurationMs.value = valid
        prefs.edit().putInt(PlaybackPreferences.KEY_CROSSFADE_MS, valid).apply()
        PlaybackService.instance?.refreshPlaybackSettings()
    }

    fun setVinylModeEnabled(enabled: Boolean) {
        _vinylModeEnabled.value = enabled
        prefs.edit().putBoolean(PlaybackPreferences.KEY_VINYL_MODE_ENABLED, enabled).apply()
    }

    fun toggleVinylMode() = setVinylModeEnabled(!_vinylModeEnabled.value)

    fun refreshEqualizerBands() {
        _equalizerBands.value = EqualizerManager.getBandInfos()
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabled.value = enabled
        EqualizerManager.setEnabled(getApplication(), enabled)
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        _equalizerPreset.value = preset
        EqualizerManager.applyPreset(getApplication(), preset)
        refreshEqualizerBands()
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelMillibels: Short) {
        EqualizerManager.setBandLevel(getApplication(), bandIndex, levelMillibels)
        _equalizerPreset.value = EqualizerPreset.CUSTOM
        refreshEqualizerBands()
    }

    fun playbackModeLabel(): String =
        PlaybackPreferences.playbackModeLabel(_gaplessEnabled.value, _crossfadeDurationMs.value)

    fun clearMetadataEditMessage() {
        _metadataEditMessage.value = null
    }

    fun saveSongMetadata(song: Song, title: String, artist: String, album: String) {
        viewModelScope.launch {
            dbRepository.saveMetadataOverride(song.id, title, artist, album)
            val result = MetadataEditor.tryWriteToMediaStore(
                getApplication(),
                song,
                title.trim(),
                artist.trim(),
                album.trim()
            )
            _metadataEditMessage.value = result.message
            _allSongs.value = _allSongs.value.map { s ->
                if (s.id == song.id) {
                    applyMetadataOverride(
                        s.copy(title = title.trim(), artist = artist.trim(), albumName = album.trim())
                    )
                } else s
            }
            _currentSong.value?.takeIf { it.id == song.id }?.let { current ->
                _currentSong.value = applyMetadataOverride(
                    current.copy(title = title.trim(), artist = artist.trim(), albumName = album.trim())
                )
            }
            _playbackQueue.value = _playbackQueue.value.map { s ->
                if (s.id == song.id) {
                    applyMetadataOverride(
                        s.copy(title = title.trim(), artist = artist.trim(), albumName = album.trim())
                    )
                } else s
            }
            val controller = _mediaController.value
            if (controller != null && _currentSong.value?.id == song.id) {
                val index = controller.currentMediaItemIndex
                val items = (0 until controller.mediaItemCount).mapNotNull { controller.getMediaItemAt(it) }
                if (index in items.indices) {
                    val updated = songToMediaItem(
                        song.copy(title = title.trim(), artist = artist.trim(), albumName = album.trim())
                    )
                    controller.replaceMediaItem(index, updated)
                }
            }
        }
    }

    fun saveSongCoverArt(song: Song, sourceUri: Uri) {
        viewModelScope.launch {
            val savedUri = CoverArtStorage.saveFromUri(getApplication(), song.id, sourceUri)
            dbRepository.saveCoverArtOverride(song.id, savedUri.toString())
            val updated = song.copy(albumArtUri = savedUri)
            _allSongs.value = _allSongs.value.map { s ->
                if (s.id == song.id) applyMetadataOverride(updated) else s
            }
            _currentSong.value?.takeIf { it.id == song.id }?.let { current ->
                _currentSong.value = applyMetadataOverride(current.copy(albumArtUri = savedUri))
            }
            _playbackQueue.value = _playbackQueue.value.map { s ->
                if (s.id == song.id) applyMetadataOverride(updated) else s
            }
            _favoriteSongs.value = _favoriteSongs.value.map { s ->
                if (s.id == song.id) applyMetadataOverride(updated) else s
            }
            _recentlyPlayed.value = _recentlyPlayed.value.map { s ->
                if (s.id == song.id) applyMetadataOverride(updated) else s
            }
            _playlists.value = _playlists.value.map { playlist ->
                playlist.copy(
                    songs = playlist.songs.map { s ->
                        if (s.id == song.id) applyMetadataOverride(updated) else s
                    }
                )
            }
            val controller = _mediaController.value
            if (controller != null && _currentSong.value?.id == song.id) {
                val index = controller.currentMediaItemIndex
                if (index in 0 until controller.mediaItemCount) {
                    controller.replaceMediaItem(index, songToMediaItem(updated))
                }
            }
        }
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

    fun setAlbumCoverAccentEnabled(enabled: Boolean) {
        _albumCoverAccentEnabled.value = enabled
        prefs.edit().putBoolean("album_cover_accent_enabled", enabled).apply()
    }

    fun playNext(song: Song) {
        val controller = _mediaController.value ?: return
        val item = songToMediaItem(song)
        if (controller.mediaItemCount == 0) {
            setPlaybackContext(listOf(song), 0)
            controller.setMediaItems(listOf(item))
            controller.prepare()
        } else {
            val insertAt = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
            controller.addMediaItem(insertAt, item)
            val newList = _playbackQueue.value.toMutableList()
            newList.add(insertAt.coerceIn(0, newList.size), song)
            _playbackQueue.value = newList
        }
    }

    fun addToQueue(song: Song) {
        val controller = _mediaController.value ?: return
        val item = songToMediaItem(song)
        if (controller.mediaItemCount == 0) {
            setPlaybackContext(listOf(song), 0)
            controller.setMediaItems(listOf(item))
            controller.prepare()
        } else {
            controller.addMediaItem(item)
            _playbackQueue.value = _playbackQueue.value + song
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
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            dbRepository.createPlaylist(trimmed)
            _playlistMessage.value = "Playlist « $trimmed » créée"
        }
    }

    fun clearPlaylistMessage() {
        _playlistMessage.value = null
    }

    fun deletePlaylist(playlistId: Long) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
        viewModelScope.launch {
            dbRepository.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song, position: Int = 0) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
        viewModelScope.launch {
            dbRepository.addSongToPlaylist(playlistId, song, position)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
        viewModelScope.launch {
            dbRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        if (SmartPlaylistIds.isSmart(playlistId)) return
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
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
    }
}
