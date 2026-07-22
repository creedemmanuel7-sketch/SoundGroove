package com.credo.soundgroove.lyrics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.util.LyricsPreferences
import kotlinx.coroutines.CancellationException
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

    private val _editingDraft = MutableStateFlow("")
    val editingDraft: StateFlow<String> = _editingDraft.asStateFlow()

    /**
     * Décalage de synchronisation (ms) appliqué avant de déterminer la ligne active.
     * Négatif = les lignes s'illuminent plus tôt, ce qui corrige un ressenti de
     * paroles "en retard" (latence d'affichage + décalage de perception). Volontairement
     * gardé configurable ici plutôt qu'en dur, sans dépendre du fichier LRC source.
     */
    private val _syncOffsetMs = MutableStateFlow(
        LyricsPreferences.syncOffsetMs(application)
    )
    val syncOffsetMs: StateFlow<Long> = _syncOffsetMs.asStateFlow()

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
            try {
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _lyricsContent.value = LyricsContent.NotFound
            }
        }
    }

    fun startEditing(initialText: String = "") {
        _saveError.value = null
        _editingDraft.value = initialText
        _isEditing.value = true
    }

    /** Ouvre l'éditeur avec le texte déjà associé au morceau (LRC brut si disponible). */
    fun startEditingExisting(song: Song) {
        if (_isSaving.value) return
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                LyricsRepository.readRawText(getApplication(), song)
            } ?: when (val content = _lyricsContent.value) {
                is LyricsContent.Synced -> LrcParser.format(content.lines)
                is LyricsContent.PlainText -> content.text
                else -> ""
            }
            startEditing(raw)
        }
    }

    fun cancelEditing() {
        _saveError.value = null
        _editingDraft.value = ""
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

    /**
     * Supprime les paroles associées au morceau et affiche l'état vide
     * (sans relancer la recherche LRCLIB automatique).
     */
    fun deleteLyrics(song: Song) {
        if (_isSaving.value) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LyricsRepository.deleteLyrics(getApplication(), song)
            }
            _isEditing.value = false
            _editingDraft.value = ""
            _saveError.value = null
            _currentLineIndex.value = -1
            _lyricsContent.value = LyricsContent.NotFound
        }
    }

    /** Ajuste le décalage de synchronisation à la volée (Options ou réglages paroles). */
    fun setSyncOffsetMs(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(LyricsPreferences.MIN_OFFSET_MS, LyricsPreferences.MAX_OFFSET_MS)
        _syncOffsetMs.value = clamped
        LyricsPreferences.setSyncOffsetMs(getApplication(), clamped)
    }

    /** À appeler régulièrement avec la position de lecture courante (ms). */
    fun updatePlaybackPosition(positionMs: Long) {
        val synced = _lyricsContent.value as? LyricsContent.Synced ?: run {
            if (_currentLineIndex.value != -1) _currentLineIndex.value = -1
            return
        }
        val adjustedPositionMs = (positionMs - _syncOffsetMs.value).coerceAtLeast(0L)
        val index = synced.lines.indexOfLast { it.timeMs <= adjustedPositionMs }
        if (index != _currentLineIndex.value) {
            _currentLineIndex.value = index
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }

    companion object {
        /** Léger décalage par défaut suggéré : compense un ressenti de paroles en retard. */
        const val DEFAULT_SYNC_OFFSET_MS = -200L
    }
}
