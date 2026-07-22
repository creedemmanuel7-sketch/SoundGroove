package com.credo.soundgroove.auto

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Callback [MediaLibrarySession] : browse Playlists / Dossiers / Favoris + commandes Auto.
 */
class AutoLibrarySessionCallback(
    private val player: Player,
    private val catalog: AutoLibraryCatalog,
    private val scope: CoroutineScope,
    private val previousRestartThresholdMs: Long = 3_000L,
) : MediaLibraryService.MediaLibrarySession.Callback {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val base = super.onConnect(session, controller)
        val commandsBuilder = SessionCommands.Builder()
        base.availableSessionCommands.commands.forEach { commandsBuilder.add(it) }
        commandsBuilder.add(
            SessionCommand(AutoSessionExtras.ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY),
        )
        return MediaSession.ConnectionResult.accept(
            commandsBuilder.build(),
            base.availablePlayerCommands,
        )
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> =
        AutoSessionExtras.handleCustomCommand(controller, customCommand, player)

    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int,
    ): Int {
        when (playerCommand) {
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                seekPreviousTrack()
                return SessionResult.RESULT_SUCCESS
            }
        }
        return super.onPlayerCommandRequest(session, controller, playerCommand)
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val root = catalog.item(AutoMediaIds.ROOT)
            ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
        return Futures.immediateFuture(LibraryResult.ofItem(root, params))
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            runCatching {
                catalog.refresh()
                val children = catalog.childrenOf(parentId)
                val pageItems = children
                    .drop(page * pageSize)
                    .take(pageSize)
                LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), params)
            }.onSuccess { future.set(it) }
                .onFailure {
                    future.set(
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN),
                    )
                }
        }
        return future
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        scope.launch {
            runCatching {
                catalog.refresh()
                catalog.item(mediaId)
                    ?.let { LibraryResult.ofItem(it, null) }
                    ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }.onSuccess { future.set(it) }
                .onFailure {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
        }
        return future
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        val future = SettableFuture.create<List<MediaItem>>()
        scope.launch {
            runCatching {
                catalog.refresh()
                mediaItems.flatMap { item ->
                    val resolved = catalog.resolvePlayable(item.mediaId)
                    when {
                        resolved.isNotEmpty() -> resolved
                        item.localConfiguration?.uri != null -> listOf(item)
                        else -> emptyList()
                    }
                }.ifEmpty { mediaItems }
            }.onSuccess { future.set(it) }
                .onFailure { future.set(mediaItems) }
        }
        return future
    }

    private fun seekPreviousTrack() {
        if (player.currentPosition > previousRestartThresholdMs) {
            player.seekTo(0)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0)
        }
    }
}
