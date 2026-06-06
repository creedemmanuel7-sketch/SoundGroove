package com.credo.soundgroove.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.credo.soundgroove.PlaybackService
import com.credo.soundgroove.SoundGrooveDatabase
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.data.repository.DatabaseRepository
import com.credo.soundgroove.data.repository.MusicRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.credo.soundgroove.ui.theme.AppTheme

class SoundGrooveViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("soundgroove_prefs", android.content.Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(
        try {
            AppTheme.valueOf(prefs.getString("selected_theme", AppTheme.CLASSIC_DARK.name) ?: AppTheme.CLASSIC_DARK.name)
        } catch (e: Exception) {
            AppTheme.CLASSIC_DARK
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
    private val musicRepository = MusicRepository(application)

    // --- MediaController ---
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController: StateFlow<MediaController?> = _mediaController.asStateFlow()

    // --- State ---
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

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
    
    private val _sortMode = MutableStateFlow(0)
    val sortMode: StateFlow<Int> = _sortMode.asStateFlow()

    init {
        initMediaController()
        loadMusic()
        observeDatabase()
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(getApplication(), ComponentName(getApplication(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            _mediaController.value = controller
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val uriStr = mediaItem?.mediaId
                    _currentSong.value = _songs.value.find { it.uri.toString() == uriStr }
                    _currentSong.value?.let { song ->
                        viewModelScope.launch {
                            dbRepository.addRecentlyPlayed(song)
                        }
                    }
                }
            })
            startProgressUpdate()
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    _playbackPosition.value = _mediaController.value?.currentPosition ?: 0L
                }
                delay(1000L)
            }
        }
    }

    private fun loadMusic() {
        viewModelScope.launch {
            val loadedSongs = musicRepository.getSongs()
            _songs.value = loadedSongs
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
        val controller = _mediaController.value ?: return
        val currentQueue = controller.mediaItemCount
        val items = _songs.value.map { MediaItem.Builder().setMediaId(it.uri.toString()).setUri(it.uri).build() }
        
        if (currentQueue == 0) {
            controller.setMediaItems(items)
        }
        
        val index = _songs.value.indexOf(song)
        if (index != -1) {
            controller.seekTo(index, 0)
            controller.play()
            _isPlaying.value = true
        }
    }
    
    fun playPlaylist(playlist: Playlist, startSong: Song? = null) {
        val controller = _mediaController.value ?: return
        val items = playlist.songs.map { MediaItem.Builder().setMediaId(it.uri.toString()).setUri(it.uri).build() }
        controller.setMediaItems(items)
        
        val index = startSong?.let { playlist.songs.indexOf(it) }?.takeIf { it != -1 } ?: 0
        controller.seekTo(index, 0)
        controller.play()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) _mediaController.value?.pause() else _mediaController.value?.play()
    }

    fun skipNext() = _mediaController.value?.seekToNext()
    fun skipPrevious() = _mediaController.value?.seekToPrevious()
    fun seekTo(position: Long) = _mediaController.value?.seekTo(position)

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


    // --- UI Actions ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun updateSortMode(mode: Int) {
        _sortMode.value = mode
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

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
