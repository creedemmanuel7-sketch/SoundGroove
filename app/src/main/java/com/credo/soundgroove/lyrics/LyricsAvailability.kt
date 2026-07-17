package com.credo.soundgroove.lyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Signal partagé pour reconstruire la smart playlist « Avec paroles »
 * quand le cache / les fichiers locaux de paroles changent.
 */
object LyricsAvailability {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun notifyChanged() {
        _revision.value = _revision.value + 1
    }
}
