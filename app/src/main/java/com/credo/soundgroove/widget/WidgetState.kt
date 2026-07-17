package com.credo.soundgroove.widget

import android.content.Context
import android.net.Uri

enum class WidgetSkin {
    DARK,
    CYAN;

    companion object {
        fun fromName(value: String?): WidgetSkin =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: DARK
    }
}

data class WidgetPlaybackState(
    val title: String = "",
    val artist: String = "",
    val albumArtUri: Uri? = null,
    val isPlaying: Boolean = false,
    val skin: WidgetSkin = WidgetSkin.DARK
)

object WidgetState {
    private const val PREFS = "soundgroove_widget_state"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ART_URI = "art_uri"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_SKIN = "skin"

    fun save(context: Context, state: WidgetPlaybackState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TITLE, state.title)
            .putString(KEY_ARTIST, state.artist)
            .putString(KEY_ART_URI, state.albumArtUri?.toString())
            .putBoolean(KEY_IS_PLAYING, state.isPlaying)
            .putString(KEY_SKIN, state.skin.name)
            .apply()
    }

    fun read(context: Context): WidgetPlaybackState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val artUri = prefs.getString(KEY_ART_URI, null)?.let(Uri::parse)
        return WidgetPlaybackState(
            title = prefs.getString(KEY_TITLE, "") ?: "",
            artist = prefs.getString(KEY_ARTIST, "") ?: "",
            albumArtUri = artUri,
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            skin = WidgetSkin.fromName(prefs.getString(KEY_SKIN, WidgetSkin.DARK.name))
        )
    }
}
