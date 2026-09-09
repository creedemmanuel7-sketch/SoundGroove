package com.credo.soundgroove.widget

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player

enum class WidgetSkin {
    DARK,
    CYAN;

    companion object {
        fun fromName(value: String?): WidgetSkin =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: DARK
    }
}

data class WidgetQueueItem(
    val title: String = "",
    val artist: String = "",
    val albumArtUri: Uri? = null,
)

data class WidgetPlaybackState(
    val title: String = "",
    val artist: String = "",
    val albumArtUri: Uri? = null,
    val mediaUri: String = "",
    val songId: Long = -1L,
    val isPlaying: Boolean = false,
    val isFavorite: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val upNext: List<WidgetQueueItem> = emptyList(),
    val skin: WidgetSkin = WidgetSkin.DARK,
) {
    val progressPercent: Int
        get() {
            if (durationMs <= 0L) return 0
            return ((positionMs.coerceAtLeast(0L) * 100L) / durationMs).toInt().coerceIn(0, 100)
        }
}

object WidgetState {
    private const val PREFS = "soundgroove_widget_state"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ART_URI = "art_uri"
    private const val KEY_MEDIA_URI = "media_uri"
    private const val KEY_SONG_ID = "song_id"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_IS_FAVORITE = "is_favorite"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_REPEAT = "repeat"
    private const val KEY_POSITION = "position_ms"
    private const val KEY_DURATION = "duration_ms"
    private const val KEY_SKIN = "skin"
    private const val KEY_UPNEXT1_TITLE = "up1_title"
    private const val KEY_UPNEXT1_ARTIST = "up1_artist"
    private const val KEY_UPNEXT1_ART = "up1_art"
    private const val KEY_UPNEXT2_TITLE = "up2_title"
    private const val KEY_UPNEXT2_ARTIST = "up2_artist"
    private const val KEY_UPNEXT2_ART = "up2_art"

    fun save(context: Context, state: WidgetPlaybackState) {
        val up1 = state.upNext.getOrNull(0)
        val up2 = state.upNext.getOrNull(1)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TITLE, state.title)
            .putString(KEY_ARTIST, state.artist)
            .putString(KEY_ART_URI, state.albumArtUri?.toString())
            .putString(KEY_MEDIA_URI, state.mediaUri)
            .putLong(KEY_SONG_ID, state.songId)
            .putBoolean(KEY_IS_PLAYING, state.isPlaying)
            .putBoolean(KEY_IS_FAVORITE, state.isFavorite)
            .putBoolean(KEY_SHUFFLE, state.shuffleEnabled)
            .putInt(KEY_REPEAT, state.repeatMode)
            .putLong(KEY_POSITION, state.positionMs)
            .putLong(KEY_DURATION, state.durationMs)
            .putString(KEY_SKIN, state.skin.name)
            .putString(KEY_UPNEXT1_TITLE, up1?.title.orEmpty())
            .putString(KEY_UPNEXT1_ARTIST, up1?.artist.orEmpty())
            .putString(KEY_UPNEXT1_ART, up1?.albumArtUri?.toString())
            .putString(KEY_UPNEXT2_TITLE, up2?.title.orEmpty())
            .putString(KEY_UPNEXT2_ARTIST, up2?.artist.orEmpty())
            .putString(KEY_UPNEXT2_ART, up2?.albumArtUri?.toString())
            .apply()
    }

    fun read(context: Context): WidgetPlaybackState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val artUri = prefs.getString(KEY_ART_URI, null)?.let(Uri::parse)
        val upNext = buildList {
            val t1 = prefs.getString(KEY_UPNEXT1_TITLE, "").orEmpty()
            if (t1.isNotBlank()) {
                add(
                    WidgetQueueItem(
                        title = t1,
                        artist = prefs.getString(KEY_UPNEXT1_ARTIST, "").orEmpty(),
                        albumArtUri = prefs.getString(KEY_UPNEXT1_ART, null)?.let(Uri::parse),
                    )
                )
            }
            val t2 = prefs.getString(KEY_UPNEXT2_TITLE, "").orEmpty()
            if (t2.isNotBlank()) {
                add(
                    WidgetQueueItem(
                        title = t2,
                        artist = prefs.getString(KEY_UPNEXT2_ARTIST, "").orEmpty(),
                        albumArtUri = prefs.getString(KEY_UPNEXT2_ART, null)?.let(Uri::parse),
                    )
                )
            }
        }
        return WidgetPlaybackState(
            title = prefs.getString(KEY_TITLE, "") ?: "",
            artist = prefs.getString(KEY_ARTIST, "") ?: "",
            albumArtUri = artUri,
            mediaUri = prefs.getString(KEY_MEDIA_URI, "") ?: "",
            songId = prefs.getLong(KEY_SONG_ID, -1L),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            isFavorite = prefs.getBoolean(KEY_IS_FAVORITE, false),
            shuffleEnabled = prefs.getBoolean(KEY_SHUFFLE, false),
            repeatMode = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            durationMs = prefs.getLong(KEY_DURATION, 0L),
            upNext = upNext,
            skin = WidgetSkin.fromName(prefs.getString(KEY_SKIN, WidgetSkin.DARK.name)),
        )
    }

    fun updateFavorite(context: Context, isFavorite: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_FAVORITE, isFavorite)
            .apply()
    }
}
