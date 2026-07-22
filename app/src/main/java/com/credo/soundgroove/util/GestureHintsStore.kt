package com.credo.soundgroove.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gestureHintsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "soundgroove_gesture_hints"
)

object GestureHintIds {
    const val PLAYER_DISMISS = "player_dismiss"
    const val PLAYER_QUEUE = "player_queue"
    const val PLAYER_LYRICS = "player_lyrics"
    const val LYRICS_BACK = "lyrics_back"
    const val QUEUE_CLOSE = "queue_close"
    const val DETAIL_BACK = "detail_back"
}

class GestureHintsStore(context: Context) {
    private val dataStore = context.gestureHintsDataStore

    val seenHints: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[SEEN_HINTS] ?: emptySet()
    }

    suspend fun markSeen(hintId: String) {
        dataStore.edit { prefs ->
            val current = prefs[SEEN_HINTS] ?: emptySet()
            prefs[SEEN_HINTS] = current + hintId
        }
    }

    companion object {
        val SEEN_HINTS = stringSetPreferencesKey("gesture_hints_seen")
    }
}
