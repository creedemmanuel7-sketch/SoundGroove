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
 * [com.credo.soundgroove.viewmodel.SoundGrooveViewModel] pour ne pas alourdir
 * ce dernier : la position de lecture est poussée depuis l'UI (LyricsScreen)
 * via [updatePlaybackPosition].
 */
class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val _lyricsContent = MutableStateFlow<LyricsContent>(LyricsContent.Loading)
    val lyricsContent: StateFlow<LyricsContent> = _lyricsContent.asStateFlow()

    private val _currentLineIndex = MutableStateFlow(-1)
    val currentLineIndex: StateFlow<Int> = _currentLineIndex.asStateFlow()

    private var loadedSongId: Long? = null
    private var loadJob: Job? = null

    fun loadLyricsForSong(song: Song) {
        if (loadedSongId == song.id) return
        loadedSongId = song.id
        _currentLineIndex.value = -1
        _lyricsContent.value = LyricsContent.Loading
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                LyricsRepository.loadLyrics(getApplication(), song)
            }
            _lyricsContent.value = content
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
