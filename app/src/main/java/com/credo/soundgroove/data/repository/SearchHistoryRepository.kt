package com.credo.soundgroove.data.repository

import android.content.Context

class SearchHistoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun addSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val updated = (listOf(trimmed) + getRecentSearches().filter { !it.equals(trimmed, ignoreCase = true) })
            .take(MAX_ENTRIES)
        prefs.edit().putString(KEY_HISTORY, updated.joinToString(SEPARATOR)).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    companion object {
        private const val PREFS_NAME = "soundgroove_search_history"
        private const val KEY_HISTORY = "recent_searches"
        private const val SEPARATOR = "\u001E"
        private const val MAX_ENTRIES = 12
    }
}
