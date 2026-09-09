package com.credo.soundgroove.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.room.withTransaction
import com.credo.soundgroove.SoundGrooveDatabase
import com.credo.soundgroove.data.backup.BackupManager
import com.credo.soundgroove.data.backup.BackupSnapshot
import com.credo.soundgroove.data.backup.PlaybackSettingsBackup
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.SmartPlaylistIds
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.data.repository.ListeningStats
import com.credo.soundgroove.data.repository.ListeningStatsRepository
import com.credo.soundgroove.data.repository.LocalScrobbleStats
import com.credo.soundgroove.data.repository.ScrobbleRepository
import com.credo.soundgroove.data.repository.SearchHistoryRepository
import com.credo.soundgroove.library.LibraryManager
import com.credo.soundgroove.notifications.SmartNotificationManager
import com.credo.soundgroove.playback.PlaybackManager
import com.credo.soundgroove.remote.RemoteCommandAction
import com.credo.soundgroove.remote.RemoteHostServer
import com.credo.soundgroove.remote.RemoteLanAddresses
import com.credo.soundgroove.remote.RemotePlaybackState
import com.credo.soundgroove.remote.RemoteProtocol
import com.credo.soundgroove.remote.RemoteSongState
import com.credo.soundgroove.ui.theme.AppAccent
import com.credo.soundgroove.ui.theme.AppTheme
import com.credo.soundgroove.util.EqualizerBandInfo
import com.credo.soundgroove.util.EqualizerManager
import com.credo.soundgroove.util.EqualizerPreset
import com.credo.soundgroove.util.LyricsPreferences
import com.credo.soundgroove.util.PlaybackPreferences
import com.credo.soundgroove.PlaybackService
import com.credo.soundgroove.data.repository.DatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel racine — orchestrateur léger.
 *
 * Délègue la bibliothèque à [LibraryManager], la lecture à [PlaybackManager],
 * et conserve thème / réglages / remote / stats / sleep timer.
 *
 * Voir `docs/MVVM_REFACTOR_2026-08-22.md` pour le détail de l'architecture.
 */
class SoundGrooveViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("soundgroove_prefs", android.content.Context.MODE_PRIVATE)
    private val db = SoundGrooveDatabase.getInstance(application)
    private val searchHistoryRepository = SearchHistoryRepository(application)
    private val listeningStatsRepository = ListeningStatsRepository(application)
    private val scrobbleRepository = ScrobbleRepository(application)
    private val backupManager = BackupManager(application)

    val library = LibraryManager(application, viewModelScope, prefs, db)
    val playback = PlaybackManager(
        application = application,
        scope = viewModelScope,
        library = library,
        onTrackStarted = { song ->
            scrobbleRepository.onTrackChanged()
            library.recordRecentlyPlayed(song)
        },
        onListeningSecond = { recordListeningSecond() },
        onScrobbleProgress = { song, position, duration ->
            if (scrobbleRepository.trackProgress(song, position, duration)) {
                _scrobbleStats.value = scrobbleRepository.getStats()
            }
        },
        onEqualizerTrackChanged = { songId ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                EqualizerManager.applyForTrack(application, songId)
                _equalizerPreset.value = EqualizerManager.effectivePresetForTrack(application, songId)
                refreshTrackEqPinned()
                refreshEqualizerBands()
            }
        },
    )

    // --- Thème / UI prefs ---
    private val _currentTheme = MutableStateFlow(
        try {
            AppTheme.valueOf(prefs.getString("selected_theme", AppTheme.NOIR_ABSOLU.name) ?: AppTheme.NOIR_ABSOLU.name)
        } catch (_: Exception) {
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

    // --- Délégation bibliothèque ---
    val songs: StateFlow<List<Song>> = library.songs
    val sortedSongs: StateFlow<List<Song>> = library.sortedSongs
    val favoriteSongs: StateFlow<List<Song>> = library.favoriteSongs
    val recentlyPlayed: StateFlow<List<Song>> = library.recentlyPlayed
    val playlists: StateFlow<List<Playlist>> = library.playlists
    val songsWithLyrics: StateFlow<List<Song>> = library.songsWithLyrics
    val metadataOverrides = library.metadataOverrides
    val hiddenFolders: StateFlow<Set<String>> = library.hiddenFolders
    val libraryFolderUris: StateFlow<Set<Uri>> = library.libraryFolderUris
    val playlistMessage: StateFlow<String?> = library.playlistMessage
    val metadataEditMessage: StateFlow<String?> = library.metadataEditMessage
    val sortMode: StateFlow<Int> = library.sortMode

    // --- Délégation lecture ---
    val mediaController: StateFlow<MediaController?> = playback.mediaController
    val isControllerConnecting: StateFlow<Boolean> = playback.isControllerConnecting
    val currentSong: StateFlow<Song?> = playback.currentSong
    val playbackQueue: StateFlow<List<Song>> = playback.playbackQueue
    val playbackQueueIndex: StateFlow<Int> = playback.playbackQueueIndex
    val isPlaying: StateFlow<Boolean> = playback.isPlaying
    val isBuffering: StateFlow<Boolean> = playback.isBuffering
    val playbackError: StateFlow<String?> = playback.playbackError
    val shuffleEnabled: StateFlow<Boolean> = playback.shuffleEnabled
    val repeatMode: StateFlow<Int> = playback.repeatMode
    val playbackPosition: StateFlow<Long> = playback.playbackPosition
    val playbackDuration: StateFlow<Long> = playback.playbackDuration

    // --- Recherche / backup / scrobble ---
    private val _recentSearches = MutableStateFlow(searchHistoryRepository.getRecentSearches())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _scrobbleStats = MutableStateFlow(scrobbleRepository.getStats())
    val scrobbleStats: StateFlow<LocalScrobbleStats> = _scrobbleStats.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // --- Réglages lecture ---
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

    private val _lyricsSyncOffsetMs = MutableStateFlow(LyricsPreferences.syncOffsetMs(application))
    val lyricsSyncOffsetMs: StateFlow<Long> = _lyricsSyncOffsetMs.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(PlaybackPreferences.isEqualizerEnabled(application))
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(PlaybackPreferences.equalizerPreset(application))
    val equalizerPreset: StateFlow<EqualizerPreset> = _equalizerPreset.asStateFlow()

    private val _equalizerBands = MutableStateFlow<List<EqualizerBandInfo>>(emptyList())
    val equalizerBands: StateFlow<List<EqualizerBandInfo>> = _equalizerBands.asStateFlow()

    private val _currentTrackEqPinned = MutableStateFlow(false)
    val currentTrackEqPinned: StateFlow<Boolean> = _currentTrackEqPinned.asStateFlow()

    private val _smartNotificationsEnabled = MutableStateFlow(prefs.getBoolean("smart_notifications_enabled", true))
    val smartNotificationsEnabled: StateFlow<Boolean> = _smartNotificationsEnabled.asStateFlow()

    private val _persistentMiniPlayerEnabled = MutableStateFlow(prefs.getBoolean("persistent_miniplayer_enabled", true))
    val persistentMiniPlayerEnabled: StateFlow<Boolean> = _persistentMiniPlayerEnabled.asStateFlow()

    private val _performanceModeEnabled = MutableStateFlow(prefs.getBoolean("performance_mode_enabled", false))
    val performanceModeEnabled: StateFlow<Boolean> = _performanceModeEnabled.asStateFlow()

    private val _albumCoverAccentEnabled = MutableStateFlow(prefs.getBoolean("album_cover_accent_enabled", false))
    val albumCoverAccentEnabled: StateFlow<Boolean> = _albumCoverAccentEnabled.asStateFlow()

    // --- Remote LAN ---
    private var remoteHost: RemoteHostServer? = null
    private var remotePushJob: Job? = null
    private val _remoteHostEnabled = MutableStateFlow(false)
    val remoteHostEnabled: StateFlow<Boolean> = _remoteHostEnabled.asStateFlow()
    private val _remotePin = MutableStateFlow<String?>(null)
    val remotePin: StateFlow<String?> = _remotePin.asStateFlow()
    private val _remoteLanIp = MutableStateFlow<String?>(null)
    val remoteLanIp: StateFlow<String?> = _remoteLanIp.asStateFlow()
    private val _remoteClientCount = MutableStateFlow(0)
    val remoteClientCount: StateFlow<Int> = _remoteClientCount.asStateFlow()
    private val _remoteHostError = MutableStateFlow<String?>(null)
    val remoteHostError: StateFlow<String?> = _remoteHostError.asStateFlow()
    val remotePort: Int get() = RemoteProtocol.DEFAULT_PORT

    // --- Tabs / stats ---
    private val _mainSelectedTab = MutableStateFlow(prefs.getInt(KEY_MAIN_SELECTED_TAB, 0).coerceIn(0, 3))
    val mainSelectedTab: StateFlow<Int> = _mainSelectedTab.asStateFlow()

    private val _librarySelectedTab = MutableStateFlow(prefs.getInt(KEY_LIBRARY_SELECTED_TAB, 0).coerceAtLeast(0))
    val librarySelectedTab: StateFlow<Int> = _librarySelectedTab.asStateFlow()

    private val _totalListeningSeconds = MutableStateFlow(prefs.getLong("total_listening_seconds", 0L))
    val totalListeningSeconds: StateFlow<Long> = _totalListeningSeconds.asStateFlow()

    private val _listeningStats = MutableStateFlow(ListeningStats(0, 0, 0, 0))
    val listeningStats: StateFlow<ListeningStats> = _listeningStats.asStateFlow()

    // --- Sleep timer ---
    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null
    private var resumeReminderJob: Job? = null
    private var continuousPlaySeconds = 0
    private var sessionSummaryShown = false

    init {
        SoundGrooveDatabase.consumeOpenWarning()?.let { _backupMessage.value = it }
        publishListeningStats(listeningStatsRepository.getStats(_totalListeningSeconds.value))

        playback.playbackSpeed = _playbackSpeed.value
        playback.playbackPitch = _playbackPitch.value

        library.onSongsReloaded = { playback.onLibraryReloaded() }
        library.onMetadataRefresh = { playback.refreshDisplayFromLibrary() }

        playback.init()
        library.reloadMusic { msg -> playback.reportPlaybackError(msg) }
        refreshEqualizerBands()
    }

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

    fun formatListeningTime(seconds: Long = _listeningStats.value.totalSeconds): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h${minutes.toString().padStart(2, '0')}"
            minutes > 0 -> "$minutes min"
            safeSeconds > 0 -> "< 1 min"
            else -> "0 min"
        }
    }

    fun syncSongs(songs: List<Song>) = library.syncSongs(songs)
    fun reloadMusic() = library.reloadMusic { msg -> playback.reportPlaybackError(msg) }

    fun displaySong(song: Song): Song = library.displaySong(song)

    // Bibliothèque
    fun hideFolder(folderPath: String) = library.hideFolder(folderPath)
    fun unhideFolder(folderPath: String) = library.unhideFolder(folderPath)
    fun addLibraryFolder(treeUri: Uri) = library.addLibraryFolder(treeUri)
    fun removeLibraryFolder(treeUri: Uri) = library.removeLibraryFolder(treeUri)
    fun toggleFavorite(song: Song) = library.toggleFavorite(song)
    fun createPlaylist(name: String, onCreated: ((Long) -> Unit)? = null) = library.createPlaylist(name, onCreated)
    fun clearPlaylistMessage() = library.clearPlaylistMessage()
    fun deletePlaylist(playlistId: Long) = library.deletePlaylist(playlistId)
    fun addSongToPlaylist(playlistId: Long, song: Song, position: Int = 0) = library.addSongToPlaylist(playlistId, song, position)
    fun addSongsToPlaylist(playlistId: Long, songs: List<Song>, startPosition: Int = 0) = library.addSongsToPlaylist(playlistId, songs, startPosition)
    fun removeSongFromPlaylist(playlistId: Long, songId: Long) = library.removeSongFromPlaylist(playlistId, songId)
    fun renamePlaylist(playlistId: Long, newName: String) = library.renamePlaylist(playlistId, newName)
    fun clearRecentlyPlayed() = library.clearRecentlyPlayed()
    fun clearMetadataEditMessage() = library.clearMetadataEditMessage()
    fun updateSortMode(mode: Int) = library.updateSortMode(mode)

    fun saveSongMetadata(song: Song, title: String, artist: String, album: String) {
        library.saveSongMetadata(song, title, artist, album)
        playback.patchCurrentAndQueueSong(song.id) { s ->
            library.displaySong(s.copy(title = title.trim(), artist = artist.trim(), albumName = album.trim()))
        }
        currentSong.value?.takeIf { it.id == song.id }?.let { updated ->
            playback.replaceCurrentMediaItemIfNeeded(updated)
        }
        playback.persistSession()
    }

    fun saveSongCoverArt(song: Song, sourceUri: Uri) {
        library.saveSongCoverArt(song, sourceUri) { updated ->
            playback.patchCurrentAndQueueSong(song.id) { library.displaySong(updated) }
            playback.replaceCurrentMediaItemIfNeeded(library.displaySong(updated))
            playback.persistSession()
        }
    }

    // Lecture
    fun playSong(song: Song) = playback.playSong(song)
    fun playSongs(queue: List<Song>, startSong: Song) = playback.playSongs(queue, startSong)
    fun playPlaylist(playlist: Playlist, startSong: Song? = null) {
        if (playlist.songs.isEmpty()) return
        playback.playPlaylist(playlist.songs, startSong ?: playlist.songs.first())
    }
    fun seekToQueueIndex(index: Int) = playback.seekToQueueIndex(index)
    fun removeFromPlaybackQueue(index: Int) = playback.removeFromPlaybackQueue(index)
    fun moveInPlaybackQueue(from: Int, to: Int) = playback.moveInPlaybackQueue(from, to)
    fun clearUpcoming() = playback.clearUpcoming()
    fun togglePlayPause() = playback.togglePlayPause()
    fun skipNext() = playback.skipNext()
    fun skipPrevious() = playback.skipPrevious()
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeatMode() = playback.cycleRepeatMode()
    fun seekTo(position: Long) = playback.seekTo(position)
    fun clearPlaybackError() = playback.clearPlaybackError()
    fun playNext(song: Song) = playback.playNext(song)
    fun addToQueue(song: Song) = playback.addToQueue(song)

    // Recherche / backup
    fun addRecentSearch(query: String) {
        searchHistoryRepository.addSearch(query)
        _recentSearches.value = searchHistoryRepository.getRecentSearches()
    }

    fun clearSearchHistory() {
        searchHistoryRepository.clearHistory()
        _recentSearches.value = emptyList()
    }

    fun clearBackupMessage() { _backupMessage.value = null }

    fun clearScrobbleHistory() {
        scrobbleRepository.clearAll()
        _scrobbleStats.value = scrobbleRepository.getStats()
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val snapshot = BackupSnapshot(
                    theme = _currentTheme.value,
                    accent = _currentAccent.value,
                    favorites = DatabaseRepository(db).getFavoritesSnapshot(),
                    playlists = DatabaseRepository(db).getPlaylistsSnapshot(),
                    playbackSettings = buildPlaybackSettingsBackup(),
                )
                backupManager.writeToUri(uri, backupManager.serialize(snapshot))
                "Sauvegarde exportée avec succès."
            }.onSuccess { _backupMessage.value = it }
                .onFailure { _backupMessage.value = it.message ?: "Échec de l'exportation." }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val snapshot = backupManager.parse(backupManager.readFromUri(uri))
                db.withTransaction {
                    DatabaseRepository(db).replaceLibraryData(snapshot.favorites, snapshot.playlists)
                }
                snapshot.theme?.let { setTheme(it) }
                snapshot.accent?.let { setAccent(it) }
                snapshot.playbackSettings?.let { restorePlaybackSettingsBackup(it) }
                "Restauration terminée : ${snapshot.favorites.size} favori(s), ${snapshot.playlists.size} playlist(s)."
            }.onSuccess { _backupMessage.value = it }
                .onFailure { _backupMessage.value = it.message ?: "Échec de la restauration." }
        }
    }

    // Réglages
    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        playback.playbackSpeed = clamped
        prefs.edit().putFloat(PlaybackPreferences.KEY_PLAYBACK_SPEED, clamped).apply()
        mediaController.value?.setPlaybackSpeed(clamped)
    }

    fun setPlaybackPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _playbackPitch.value = clamped
        playback.playbackPitch = clamped
        prefs.edit().putFloat(PlaybackPreferences.KEY_PLAYBACK_PITCH, clamped).apply()
        mediaController.value?.playbackParameters = PlaybackParameters(_playbackSpeed.value, clamped)
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _gaplessEnabled.value = enabled
        prefs.edit().putBoolean(PlaybackPreferences.KEY_GAPLESS_ENABLED, enabled).apply()
        PlaybackService.instance?.refreshPlaybackSettings()
    }

    fun setCrossfadeDurationMs(ms: Int) {
        val valid = PlaybackPreferences.CROSSFADE_OPTIONS_MS.find { it == ms }
            ?: PlaybackPreferences.CROSSFADE_OPTIONS_MS.minByOrNull { kotlin.math.abs(it - ms) } ?: 0
        _crossfadeDurationMs.value = valid
        prefs.edit().putInt(PlaybackPreferences.KEY_CROSSFADE_MS, valid).apply()
        PlaybackService.instance?.refreshPlaybackSettings()
    }

    fun setVinylModeEnabled(enabled: Boolean) {
        _vinylModeEnabled.value = enabled
        prefs.edit().putBoolean(PlaybackPreferences.KEY_VINYL_MODE_ENABLED, enabled).apply()
    }

    fun toggleVinylMode() = setVinylModeEnabled(!_vinylModeEnabled.value)

    fun setLyricsSyncOffsetMs(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(LyricsPreferences.MIN_OFFSET_MS, LyricsPreferences.MAX_OFFSET_MS)
        _lyricsSyncOffsetMs.value = clamped
        LyricsPreferences.setSyncOffsetMs(getApplication(), clamped)
    }

    fun refreshEqualizerBands() { _equalizerBands.value = EqualizerManager.getBandInfos() }

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabled.value = enabled
        EqualizerManager.setEnabled(getApplication(), enabled)
    }

    fun setEqualizerPreset(preset: EqualizerPreset, forCurrentTrack: Boolean = false) {
        if (forCurrentTrack) {
            currentSong.value?.let { PlaybackPreferences.setTrackEqualizerPreset(getApplication(), it.id, preset) }
            EqualizerManager.applyPreset(getApplication(), preset, persistGlobal = false)
        } else {
            EqualizerManager.applyPreset(getApplication(), preset, persistGlobal = true)
        }
        _equalizerPreset.value = EqualizerManager.effectivePresetForTrack(getApplication(), currentSong.value?.id ?: 0L)
        refreshTrackEqPinned()
        refreshEqualizerBands()
    }

    fun clearTrackEqualizerPreset(songId: Long) {
        PlaybackPreferences.clearTrackEqualizerPreset(getApplication(), songId)
        if (currentSong.value?.id == songId) {
            EqualizerManager.applyForTrack(getApplication(), songId)
            _equalizerPreset.value = EqualizerManager.effectivePresetForTrack(getApplication(), songId)
            refreshEqualizerBands()
        }
        refreshTrackEqPinned()
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelMillibels: Short, forCurrentTrack: Boolean = false) {
        EqualizerManager.setBandLevel(getApplication(), bandIndex, levelMillibels)
        _equalizerPreset.value = EqualizerPreset.CUSTOM
        if (forCurrentTrack) {
            currentSong.value?.let {
                PlaybackPreferences.setTrackEqualizerPreset(getApplication(), it.id, EqualizerPreset.CUSTOM)
            }
        }
        refreshTrackEqPinned()
        refreshEqualizerBands()
    }

    fun refreshTrackEqPinned() {
        val songId = currentSong.value?.id ?: 0L
        _currentTrackEqPinned.value = songId != 0L &&
            PlaybackPreferences.getTrackEqualizerPreset(getApplication(), songId) != null
    }

    fun playbackModeLabel(): String =
        PlaybackPreferences.playbackModeLabel(_gaplessEnabled.value, _crossfadeDurationMs.value)

    fun setSmartNotificationsEnabled(enabled: Boolean) {
        _smartNotificationsEnabled.value = enabled
        prefs.edit().putBoolean("smart_notifications_enabled", enabled).apply()
        if (!enabled) {
            resumeReminderJob?.cancel()
            SmartNotificationManager.cancelAll(getApplication())
        } else if (!isPlaying.value) {
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

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateMainSelectedTab(tab: Int) {
        val safe = tab.coerceIn(0, 3)
        _mainSelectedTab.value = safe
        prefs.edit().putInt(KEY_MAIN_SELECTED_TAB, safe).apply()
    }

    fun updateLibrarySelectedTab(tab: Int) {
        val safe = tab.coerceAtLeast(0)
        _librarySelectedTab.value = safe
        prefs.edit().putInt(KEY_LIBRARY_SELECTED_TAB, safe).apply()
    }

    // Sleep timer
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

    // Remote
    fun setRemoteHostEnabled(enabled: Boolean) {
        if (enabled) startRemoteHost() else stopRemoteHost()
    }

    fun regenerateRemotePin() {
        val host = remoteHost ?: return
        _remotePin.value = host.rotatePin()
        _remoteLanIp.value = RemoteLanAddresses.primaryIpv4()
        _remoteClientCount.value = 0
    }

    override fun onCleared() {
        playback.persistSession()
        playback.release()
        resumeReminderJob?.cancel()
        stopRemoteHost()
        super.onCleared()
    }

    private fun recordListeningSecond() {
        val updated = _totalListeningSeconds.value + 1
        publishListeningStats(listeningStatsRepository.recordSecond(updated))
        continuousPlaySeconds++
        if (_smartNotificationsEnabled.value && !sessionSummaryShown &&
            continuousPlaySeconds >= SESSION_SUMMARY_THRESHOLD_SECONDS
        ) {
            sessionSummaryShown = true
            SmartNotificationManager.showSessionSummary(getApplication(), continuousPlaySeconds / 60)
        }
    }

    private fun publishListeningStats(stats: ListeningStats) {
        _listeningStats.value = stats
        if (stats.totalSeconds != _totalListeningSeconds.value) {
            _totalListeningSeconds.value = stats.totalSeconds
            prefs.edit().putLong("total_listening_seconds", stats.totalSeconds).apply()
        }
    }

    private fun scheduleResumeReminderIfEnabled() {
        if (!_smartNotificationsEnabled.value) return
        resumeReminderJob?.cancel()
        val song = currentSong.value ?: return
        resumeReminderJob = viewModelScope.launch {
            delay(RESUME_REMINDER_DELAY_MS)
            if (!isPlaying.value && currentSong.value?.id == song.id) {
                SmartNotificationManager.showResumeReminder(getApplication(), song)
            }
        }
    }

    private fun buildPlaybackSettingsBackup(): PlaybackSettingsBackup {
        val app = getApplication<Application>()
        return PlaybackSettingsBackup(
            gaplessEnabled = _gaplessEnabled.value,
            crossfadeMs = _crossfadeDurationMs.value,
            playbackSpeed = _playbackSpeed.value,
            playbackPitch = _playbackPitch.value,
            equalizerEnabled = _equalizerEnabled.value,
            equalizerPreset = _equalizerPreset.value.name,
            equalizerBandLevels = PlaybackPreferences.equalizerBandLevels(app),
            hiddenFolders = library.getHiddenFoldersSnapshot(),
            libraryFolderUris = libraryFolderUris.value.map { it.toString() }.toSet(),
            trackEqPresets = PlaybackPreferences.getAllTrackEqualizerPresets(app).mapValues { it.value.name },
            performanceModeEnabled = _performanceModeEnabled.value,
            smartNotificationsEnabled = _smartNotificationsEnabled.value,
            vinylModeEnabled = _vinylModeEnabled.value,
        )
    }

    private fun restorePlaybackSettingsBackup(settings: PlaybackSettingsBackup) {
        val app = getApplication<Application>()
        setGaplessEnabled(settings.gaplessEnabled)
        setCrossfadeDurationMs(settings.crossfadeMs)
        setPlaybackSpeed(settings.playbackSpeed)
        setPlaybackPitch(settings.playbackPitch)
        setEqualizerEnabled(settings.equalizerEnabled)
        PlaybackPreferences.setEqualizerPreset(app, EqualizerPreset.fromStored(settings.equalizerPreset))
        PlaybackPreferences.setEqualizerBandLevels(app, settings.equalizerBandLevels)
        PlaybackPreferences.replaceTrackEqualizerPresets(
            app,
            settings.trackEqPresets.mapValues { EqualizerPreset.fromStored(it.value) }
        )
        _equalizerPreset.value = EqualizerPreset.fromStored(settings.equalizerPreset)
        library.restoreHiddenFolders(settings.hiddenFolders)
        settings.libraryFolderUris.forEach { uriStr ->
            runCatching { Uri.parse(uriStr) }.getOrNull()?.let { library.addLibraryFolder(it) }
        }
        setPerformanceModeEnabled(settings.performanceModeEnabled)
        setSmartNotificationsEnabled(settings.smartNotificationsEnabled)
        setVinylModeEnabled(settings.vinylModeEnabled)
        EqualizerManager.applyForTrack(app, currentSong.value?.id ?: 0L)
        refreshTrackEqPinned()
        reloadMusic()
    }

    private fun startRemoteHost() {
        stopRemoteHost()
        _remoteHostError.value = null
        val server = RemoteHostServer(
            port = RemoteProtocol.DEFAULT_PORT,
            onCommand = { action, positionMs, volume ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    handleRemoteCommand(action, positionMs, volume)
                }
            },
            getState = { buildRemotePlaybackState() },
            onClientCountChanged = { count -> _remoteClientCount.value = count },
        )
        try {
            server.start()
            remoteHost = server
            _remotePin.value = server.rotatePin()
            _remoteLanIp.value = RemoteLanAddresses.primaryIpv4()
            _remoteHostEnabled.value = true
            remotePushJob = viewModelScope.launch {
                while (_remoteHostEnabled.value) {
                    remoteHost?.pushState()
                    delay(1_000L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_REMOTE, "Impossible de démarrer le host remote", e)
            remoteHost = null
            _remoteHostEnabled.value = false
            _remotePin.value = null
            _remoteHostError.value = e.message ?: "Port ${RemoteProtocol.DEFAULT_PORT} indisponible"
        }
    }

    private fun stopRemoteHost() {
        remotePushJob?.cancel()
        remotePushJob = null
        val host = remoteHost
        remoteHost = null
        _remoteHostEnabled.value = false
        _remotePin.value = null
        _remoteClientCount.value = 0
        if (host != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { host.stop() }
            }
        }
    }

    private fun handleRemoteCommand(action: RemoteCommandAction, positionMs: Long?, volume: Float?) {
        val controller = mediaController.value
        when (action) {
            RemoteCommandAction.PLAY -> controller?.play()
            RemoteCommandAction.PAUSE -> controller?.pause()
            RemoteCommandAction.NEXT -> skipNext()
            RemoteCommandAction.PREVIOUS -> skipPrevious()
            RemoteCommandAction.SEEK -> positionMs?.let { seekTo(it.coerceAtLeast(0L)) }
            RemoteCommandAction.SET_VOLUME -> volume?.let { controller?.volume = it.coerceIn(0f, 1f) }
        }
    }

    private fun buildRemotePlaybackState(): RemotePlaybackState {
        val song = currentSong.value
        val controller = mediaController.value
        return RemotePlaybackState(
            song = song?.let {
                RemoteSongState(
                    id = it.id.toString(),
                    title = it.title,
                    artist = it.artist,
                    album = it.albumName,
                    durationMs = it.duration,
                    artUrl = it.albumArtUri?.toString(),
                )
            },
            isPlaying = isPlaying.value,
            positionMs = playbackPosition.value,
            volume = controller?.volume ?: 1f,
            queueSize = playbackQueue.value.size,
        )
    }

    companion object {
        private const val TAG_REMOTE = "RemoteHost"
        private const val KEY_MAIN_SELECTED_TAB = "main_selected_tab"
        private const val KEY_LIBRARY_SELECTED_TAB = "library_selected_tab"
        private const val RESUME_REMINDER_DELAY_MS = 15 * 60 * 1000L
        private const val SESSION_SUMMARY_THRESHOLD_SECONDS = 20 * 60
    }
}
