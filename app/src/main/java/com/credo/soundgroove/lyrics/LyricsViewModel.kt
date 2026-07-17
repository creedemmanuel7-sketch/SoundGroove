package com.credo.soundgroove.lyrics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.credo.soundgroove.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel autonome pour l'écran Paroles. Volontairement isolé du
 * [com.credo.soundgroove.viewmodel.SoundGrooveViewModel] : la position de lecture
 * est poussée depuis l'UI (LyricsScreen) via [updatePlaybackPosition].
 */
class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val _lyricsContent = MutableStateFlow<LyricsContent>(LyricsContent.Loading)
    val lyricsContent: StateFlow<LyricsContent> = _lyricsContent.asStateFlow()

    private val _currentLineIndex = MutableStateFlow(-1)
    val currentLineIndex: StateFlow<Int> = _currentLineIndex.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private var loadedSongId: Long? = null
    private var loadJob: Job? = null

    fun loadLyricsForSong(song: Song, force: Boolean = false) {
        if (!force && loadedSongId == song.id && _lyricsContent.value !is LyricsContent.Loading &&
            _lyricsContent.value !is LyricsContent.SearchingOnline
        ) {
            return
        }
        loadedSongId = song.id
        _currentLineIndex.value = -1
        _isEditing.value = false
        _saveError.value = null

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                LyricsRepository.loadFromCache(getApplication(), song)
            }
            if (cached != null && LyricsRepository.isComplete(cached)) {
                _lyricsContent.value = cached
                return@launch
            }

            _lyricsContent.value = LyricsContent.Loading

            val local = withContext(Dispatchers.IO) {
                LyricsRepository.loadLocalLyrics(getApplication(), song)
            }
            if (LyricsRepository.isComplete(local)) {
                _lyricsContent.value = local
                return@launch
            }

            _lyricsContent.value = LyricsContent.SearchingOnline

            val online = withContext(Dispatchers.IO) {
                LyricsRepository.fetchOnlineLyrics(getApplication(), song)
            }
            _lyricsContent.value = online
        }
    }

    fun startEditing() {
        _saveError.value = null
        _isEditing.value = true
    }

    fun cancelEditing() {
        _saveError.value = null
        _isEditing.value = false
    }

    fun saveLyrics(song: Song, rawText: String) {
        if (_isSaving.value) return
        _isSaving.value = true
        _saveError.value = null
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    LyricsRepository.saveLyrics(getApplication(), song, rawText)
                }
            }
            _isSaving.value = false
            result.fold(
                onSuccess = { content ->
                    _isEditing.value = false
                    _lyricsContent.value = content
                    _currentLineIndex.value = -1
                },
                onFailure = { error ->
                    _saveError.value = error.message ?: "Impossible d'enregistrer les paroles."
                }
            )
        }
    }

    /** À appeler régulièrement avec la position de lecture courante (ms). */
    fun updatePlaybackPosition(positionMs: Long) {
        val synced = _lyricsContent.value as? LyricsContent.Synced ?: run {
            if (_currentLineIndex.value != -1) _currentLineIndex.value = -1
            return
        }
        val index = synced.lines.indexOfLast { it.timeMs <= positionMs }
        if (index != _currentLineIndex.value) {
            _currentLineIndex.value = index
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }
}
