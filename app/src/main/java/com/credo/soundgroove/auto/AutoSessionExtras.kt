package com.credo.soundgroove.auto

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.credo.soundgroove.data.model.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Enrichissements Android Auto / Automotive : métadonnées étendues et commandes shuffle.
 * Browse : [AutoLibrarySessionCallback] via [com.credo.soundgroove.PlaybackService]
 * ([androidx.media3.session.MediaLibraryService]).
 */
object AutoSessionExtras {

    const val ACTION_TOGGLE_SHUFFLE = "com.credo.soundgroove.auto.TOGGLE_SHUFFLE"
    const val EXTRA_FOLDER_PATH = "com.credo.soundgroove.extra.FOLDER_PATH"
    const val EXTRA_SOURCE = "com.credo.soundgroove.extra.SOURCE"

    fun customSessionCommands(): SessionCommands =
        SessionCommands.Builder()
            .add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .build()

    fun enrichMediaItem(base: MediaItem, song: Song): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_FOLDER_PATH, song.folderPath)
            putString(EXTRA_SOURCE, if (song.id < 0) "saf_folder" else "mediastore")
        }
        return base.buildUpon()
            .setMediaMetadata(
                base.mediaMetadata.buildUpon()
                    .setAlbumTitle(song.albumName.ifBlank { song.folderPath.ifBlank { "SoundGroove" } })
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    fun handleCustomCommand(
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        player: androidx.media3.common.Player,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction == ACTION_TOGGLE_SHUFFLE) {
            player.shuffleModeEnabled = !player.shuffleModeEnabled
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
    }
}
