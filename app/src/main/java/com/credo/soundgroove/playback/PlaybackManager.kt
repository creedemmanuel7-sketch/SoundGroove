package com.credo.soundgroove.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.credo.soundgroove.PlaybackService
import com.credo.soundgroove.auto.AutoMediaIds
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.library.LibraryManager
import com.credo.soundgroove.util.EqualizerManager
import com.credo.soundgroove.util.PlayLatencyTracker
import com.credo.soundgroove.util.PlaybackPreferences
import com.credo.soundgroove.util.PlaybackQueueOps
import com.credo.soundgroove.util.PlaybackSessionStore
import com.credo.soundgroove.util.PlayerCommandGate
import com.credo.soundgroove.util.PlayerGuards
import com.credo.soundgroove.util.PlayerUiCommand
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Moteur de lecture Media3 — extrait du God ViewModel.
 * Gère MediaController, file d'attente, commandes play/skip/seek et état UI de lecture.
 */
class PlaybackManager(
    private val application: Application,
    private val scope: CoroutineScope,
    private val library: LibraryManager,
    private val onTrackStarted: (Song) -> Unit = {},
    private val onListeningSecond: () -> Unit = {},
    private val onScrobbleProgress: (Song, Long, Long) -> Unit = { _, _, _ -> },
    private val onEqualizerTrackChanged: (Long) -> Unit = {},
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaControllerListener: Player.Listener? = null
    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController: StateFlow<MediaController?> = _mediaController.asStateFlow()

    private val _isControllerConnecting = MutableStateFlow(true)
    val isControllerConnecting: StateFlow<Boolean> = _isControllerConnecting.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<Song>>(emptyList())
    val playbackQueue: StateFlow<List<Song>> = _playbackQueue.asStateFlow()

    private val _playbackQueueIndex = MutableStateFlow(0)
    val playbackQueueIndex: StateFlow<Int> = _playbackQueueIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(PlaybackPreferences.isShuffleEnabled(application))
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlaybackPreferences.repeatMode(application))
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private var bufferingUiTimeoutJob: Job? = null
    private var lastLoggedPlaybackState: Int = Int.MIN_VALUE
    private var bufferingUiTimedOut = false
    private var queueExpandJob: Job? = null
    private var expandFallbackJob: Job? = null
    private var progressStarted = false
    private var playIssuedAtMs: Long = 0L
    private val invalidMediaIds = mutableSetOf<String>()
    private val playbackErrorRetries = AtomicInteger(0)
    private var consecutiveAutoSkips = 0
    private val playGeneration = AtomicInteger(0)

    @Volatile private var pendingPlayMediaId: String? = null
    @Volatile private var pendingPlayRequest: PendingPlayRequest? = null

    /** Fenêtre Media3 [start, endExclusive) dans la file logique — jamais la bibliothèque entière. */
    @Volatile private var playerWindowStart: Int = 0
    @Volatile private var playerWindowEndExclusive: Int = 0
    private var orderBeforeShuffle: List<Song>? = null

    private var pendingExpand: PendingExpand? = null
    private var localPlayerMirrorAttached = false

    private data class PendingPlayRequest(
        val generation: Int,
        val queue: List<Song>,
        val startSong: Song,
    )

    private data class PendingExpand(
        val queue: List<Song>,
        val index: Int,
        val generation: Int,
        val expectedMediaId: String,
    )

    private val playerCommandGate = PlayerCommandGate(
        scope = scope,
        onSkipDelta = { delta -> applySkipDeltaExclusive(delta) },
        onCommand = { command -> dispatchPlayerCommand(command) },
    )

    private var sessionRestoreAttempted = false
    private var lastSessionPersistAtMs = 0L

    var playbackSpeed: Float = 1f
    var playbackPitch: Float = 1f

    fun init() {
        PlaybackService.instance?.localPlayer()?.let { ensureEngineListener(it) }
        initMediaController()
        ensureProgressUpdate()
    }

    fun syncCurrentSongFromPlayer() {
        updateCurrentSongFromMediaItem(_mediaController.value?.currentMediaItem)
    }

    fun refreshDisplayFromLibrary() {
        _playbackQueue.value = _playbackQueue.value.map { library.displaySong(it) }
        _currentSong.value?.let { _currentSong.value = library.displaySong(it) }
    }

    fun onLibraryReloaded() {
        updateCurrentSongFromMediaItem(_mediaController.value?.currentMediaItem)
        tryRestorePlaybackSession()
        flushPendingPlayRequest()
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    fun reportPlaybackError(message: String) {
        _playbackError.value = message
    }

    fun playSong(song: Song) {
        val current = _playbackQueue.value
        val inQueue = current.indexOfFirst { it.id == song.id } >= 0
        if (inQueue) {
            playSongs(current, song)
            return
        }
        val catalog = library.allSongs.value
        if (PlaybackStartPolicy.useCatalogAsLogicalQueue(false, catalog.size) &&
            catalog.any { it.id == song.id }
        ) {
            playSongs(catalog, song)
            return
        }
        playSongs(listOf(song), song)
    }

    fun playSongs(queue: List<Song>, startSong: Song) {
        val safeQueue = queue.ifEmpty { listOf(startSong) }
        val index = safeQueue.indexOfFirst { it.id == startSong.id }.takeIf { it >= 0 }
            ?: safeQueue.indexOf(startSong)
        if (index == -1) return

        val generation = playGeneration.incrementAndGet()
        queueExpandJob?.cancel()
        expandFallbackJob?.cancel()
        pendingExpand = null
        pendingPlayRequest = null
        applyOptimisticPlayUi(safeQueue, index, startSong)
        PlayLatencyTracker.markTap("fast-start")
        val playBlock = { playSongsExclusive(safeQueue, startSong, index, generation) }
        if (!playerCommandGate.tryRunExclusive(playBlock)) {
            playerCommandGate.runExclusive(playBlock)
        }
    }

    fun playPlaylist(songs: List<Song>, startSong: Song? = null) {
        if (songs.isEmpty()) return
        playSongs(songs, startSong ?: songs.first())
    }

    fun togglePlayPause() = playerCommandGate.enqueue(PlayerUiCommand.PlayPause)
    fun skipNext() = playerCommandGate.enqueue(PlayerUiCommand.SkipNext)
    fun skipPrevious() = playerCommandGate.enqueue(PlayerUiCommand.SkipPrevious)
    fun toggleShuffle() = playerCommandGate.enqueue(PlayerUiCommand.ToggleShuffle)
    fun cycleRepeatMode() = playerCommandGate.enqueue(PlayerUiCommand.CycleRepeat)
    fun seekTo(position: Long) = playerCommandGate.enqueue(PlayerUiCommand.SeekToPosition(position))
    fun seekToQueueIndex(index: Int) = playerCommandGate.enqueue(PlayerUiCommand.SeekToQueueIndex(index))

    fun removeFromPlaybackQueue(index: Int) {
        playerCommandGate.runExclusive {
            val controller = _mediaController.value ?: return@runExclusive
            val queue = _playbackQueue.value
            if (index !in queue.indices) return@runExclusive
            val newQueue = PlaybackQueueOps.removeItemAt(queue, index)
            val newIndex = PlaybackQueueOps.adjustCurrentIndexAfterRemove(
                _playbackQueueIndex.value,
                index,
                newQueue.size,
            )
            _playbackQueue.value = newQueue
            _playbackQueueIndex.value = newIndex
            reshapePlayerWindow(controller, newQueue, newIndex, keepPlaying = controller.playWhenReady)
            persistPlaybackSession()
        }
    }

    fun moveInPlaybackQueue(from: Int, to: Int) {
        playerCommandGate.runExclusive {
            val controller = _mediaController.value ?: return@runExclusive
            val queue = _playbackQueue.value
            if (from !in queue.indices || to !in queue.indices || from == to) return@runExclusive
            val newQueue = PlaybackQueueOps.moveItems(queue, from, to)
            val newIndex = QueuePresentation.adjustCurrentAfterMove(from, to, _playbackQueueIndex.value)
                .coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
            _playbackQueue.value = newQueue
            _playbackQueueIndex.value = newIndex
            reshapePlayerWindow(controller, newQueue, newIndex, keepPlaying = controller.playWhenReady)
            persistPlaybackSession()
        }
    }

    fun clearUpcoming() {
        playerCommandGate.runExclusive {
            val controller = _mediaController.value ?: return@runExclusive
            val queue = _playbackQueue.value
            if (queue.isEmpty()) return@runExclusive
            val current = _playbackQueueIndex.value.coerceIn(0, queue.lastIndex)
            val trimmed = PlaybackQueueOps.clearUpcoming(queue, current)
            if (trimmed.size == queue.size) return@runExclusive
            _playbackQueue.value = trimmed
            _playbackQueueIndex.value = current.coerceIn(0, trimmed.lastIndex)
            reshapePlayerWindow(controller, trimmed, _playbackQueueIndex.value, keepPlaying = controller.playWhenReady)
            persistPlaybackSession()
        }
    }

    fun playNext(song: Song) {
        playerCommandGate.runExclusive {
            val controller = _mediaController.value ?: return@runExclusive
            val item = songToMediaItem(song)
            if (controller.mediaItemCount == 0) {
                setPlaybackContext(listOf(song), 0)
                rememberWindow(PlaybackWindowOps.compute(0, 1, EXPAND_RADIUS))
                controller.setMediaItems(listOf(item))
                controller.prepare()
            } else {
                val logicalInsert = (_playbackQueueIndex.value + 1).coerceAtMost(_playbackQueue.value.size)
                val newList = _playbackQueue.value.toMutableList()
                newList.add(logicalInsert, song)
                _playbackQueue.value = newList
                val playerInsert = currentWindow().toPlayerIndex(logicalInsert)
                if (playerInsert >= 0 && playerInsert <= controller.mediaItemCount) {
                    controller.addMediaItem(playerInsert, item)
                    playerWindowEndExclusive += 1
                } else {
                    reshapePlayerWindow(controller, newList, _playbackQueueIndex.value, keepPlaying = controller.playWhenReady)
                }
            }
            persistPlaybackSession()
        }
    }

    fun addToQueue(song: Song) {
        playerCommandGate.runExclusive {
            val controller = _mediaController.value ?: return@runExclusive
            val item = songToMediaItem(song)
            if (controller.mediaItemCount == 0) {
                setPlaybackContext(listOf(song), 0)
                rememberWindow(PlaybackWindowOps.compute(0, 1, EXPAND_RADIUS))
                controller.setMediaItems(listOf(item))
                controller.prepare()
            } else {
                val newList = _playbackQueue.value + song
                _playbackQueue.value = newList
                val lastLogical = newList.lastIndex
                if (currentWindow().containsLogical(lastLogical) || playerWindowEndExclusive == lastLogical) {
                    controller.addMediaItem(item)
                    playerWindowEndExclusive += 1
                }
            }
            persistPlaybackSession()
        }
    }

    fun patchCurrentAndQueueSong(songId: Long, transform: (Song) -> Song) {
        _currentSong.value?.takeIf { it.id == songId }?.let { _currentSong.value = transform(it) }
        _playbackQueue.value = _playbackQueue.value.map { s ->
            if (s.id == songId) transform(s) else s
        }
    }

    fun replaceCurrentMediaItemIfNeeded(song: Song) {
        val controller = _mediaController.value ?: return
        if (_currentSong.value?.id != song.id) return
        val index = controller.currentMediaItemIndex
        if (index in 0 until controller.mediaItemCount) {
            runCatching { controller.replaceMediaItem(index, songToMediaItem(song)) }
        }
    }

    fun persistSession() = persistPlaybackSession()

    fun release() {
        persistPlaybackSession()
        playerCommandGate.cancel()
        queueExpandJob?.cancel()
        expandFallbackJob?.cancel()
        pendingExpand = null
        mediaControllerListener?.let { listener ->
            _mediaController.value?.removeListener(listener)
            mediaControllerListener = null
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _mediaController.value = null
    }

    private fun initMediaController() {
        val connectAt = SystemClock.elapsedRealtime()
        _isControllerConnecting.value = true
        val sessionToken = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            _mediaController.value = controller
            _isControllerConnecting.value = false
            PlayLatencyTracker.markBindReady(SystemClock.elapsedRealtime() - connectAt)
            controller?.setPlaybackSpeed(playbackSpeed)
            controller?.playbackParameters = PlaybackParameters(playbackSpeed, playbackPitch)
            controller?.addListener(createPlayerListener(controller).also { mediaControllerListener = it })
            updateCurrentSongFromMediaItem(controller?.currentMediaItem)
            controller?.let { syncPlaybackUiFlags(it) }
            controller?.let { player ->
                val preferredShuffle = PlaybackPreferences.isShuffleEnabled(application)
                val preferredRepeat = PlaybackPreferences.repeatMode(application)
                if (player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) {
                    player.shuffleModeEnabled = false
                }
                applyPlayerRepeatMode(player, preferredRepeat)
                _shuffleEnabled.value = preferredShuffle
                _repeatMode.value = preferredRepeat
                syncLogicalIndexFromPlayer(player)
                maybeRestorePlaybackQueueFromPlayer(player)
            }
            tryRestorePlaybackSession()
            flushPendingPlayRequest()
            ensureProgressUpdate()
        }, MoreExecutors.directExecutor())
    }

    private fun createPlayerListener(controller: MediaController): Player.Listener =
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSongFromMediaItem(mediaItem)
                syncLogicalIndexFromPlayer(controller)
                persistPlaybackSession()
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    playbackErrorRetries.set(0)
                    maybeReexpandAfterTransition(controller)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncPlaybackUiFlags(controller)
                if (playbackState == Player.STATE_READY) {
                    publishPlaybackProgress(controller)
                }
                if (playbackState == Player.STATE_ENDED) {
                    handleLogicalQueueEnded(controller)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlaybackUiFlags(controller)
                if (isPlaying) {
                    PlayLatencyTracker.markIsPlaying()
                    maybeExpandQueue(controller, fallback = false)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                syncPlaybackUiFlags(controller)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleModeEnabled && controller.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) {
                    controller.shuffleModeEnabled = false
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                // REPEAT_ALL est logique (file complète) : le player ne doit pas boucler la fenêtre.
                if (repeatMode == Player.REPEAT_MODE_ALL) {
                    applyPlayerRepeatMode(controller, Player.REPEAT_MODE_ALL)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError(controller, error)
            }
        }

    private fun songToMediaItem(song: Song): MediaItem {
        val display = library.displaySong(song)
        val title = display.title.takeIf { it.isNotBlank() } ?: (display.uri.lastPathSegment ?: "Titre inconnu")
        val artist = display.artist.takeIf { it.isNotBlank() } ?: "Artiste inconnu"
        val album = display.albumName.takeIf { it.isNotBlank() } ?: "Album inconnu"
        return MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(display.albumArtUri)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()
    }

    private fun updateCurrentSongFromMediaItem(mediaItem: MediaItem?) {
        val item = mediaItem ?: return
        val mediaId = item.mediaId
        val uriFromAuto = AutoMediaIds.parseSongUri(mediaId)
        val uriStr = uriFromAuto ?: mediaId
        val pending = pendingPlayMediaId
        if (pending != null && pending != uriStr) return
        if (pending != null && pending == uriStr) pendingPlayMediaId = null

        val resolved = library.findSongByUri(uriStr)
            ?: item.localConfiguration?.uri?.let { uri -> library.allSongs.value.find { it.uri == uri } }

        resolved?.let { song ->
            if (_currentSong.value?.id != song.id) {
                _currentSong.value = library.displaySong(song)
                onEqualizerTrackChanged(song.id)
                onTrackStarted(song)
            }
        }
    }

    private fun applyOptimisticPlayUi(queue: List<Song>, index: Int, startSong: Song) {
        pendingPlayMediaId = startSong.uri.toString()
        setPlaybackContext(queue, index)
        _currentSong.value = library.displaySong(startSong)
        _playbackDuration.value = startSong.duration.takeIf { it > 0L } ?: 0L
        _playbackPosition.value = 0L
        _playbackError.value = null
        playbackErrorRetries.set(0)
        consecutiveAutoSkips = 0
        bufferingUiTimedOut = false
        _isPlaying.value = false
        setBufferingUi(true, "play-tap")
    }

    private fun playSongsExclusive(queue: List<Song>, startSong: Song, index: Int, generation: Int) {
        if (generation != playGeneration.get()) return
        val engine = playbackEngine()
        if (engine == null) {
            pendingPlayRequest = PendingPlayRequest(generation, queue, startSong)
            PlayLatencyTracker.markCommandPath("pending_no_engine", false, false, 0L)
            runCatching {
                application.startService(Intent(application, PlaybackService::class.java))
            }
            scope.launch {
                repeat(40) {
                    delay(50)
                    if (generation != playGeneration.get()) return@launch
                    if (playbackEngine() != null) {
                        flushPendingPlayRequest()
                        return@launch
                    }
                }
            }
            return
        }
        val path = if (PlaybackStartPolicy.preferInProcessPlayer(
                PlaybackService.instance?.localPlayer() != null,
            )
        ) {
            "in_process"
        } else {
            "controller"
        }
        PlayLatencyTracker.markCommandPath(
            path,
            engine.playWhenReady,
            engine.isPlaying,
            engine.currentPosition,
        )
        ensureAudibleVolume(engine)

        if (tryFastSeekPlayback(engine, queue, index, generation)) {
            armExpandAfterPlay(engine, queue, index, generation)
            schedulePersistPlaybackSession()
            return
        }

        val mediaItem = songToMediaItem(queue[index])
        PlayLatencyTracker.markSetMediaItem(mediaItem.mediaId, generation)
        engine.setMediaItem(mediaItem, /* resetPosition = */ true)
        rememberWindow(PlaybackWindowOps.Window(index, index + 1, index))
        if (generation != playGeneration.get()) return
        PlayLatencyTracker.markPrepare("cold-start", queue.size, index, 0L)
        engine.playWhenReady = true
        engine.prepare()
        ensureAudibleVolume(engine)
        PlayLatencyTracker.markPlayIssued(true)
        engine.play()
        syncPlaybackUiFlags(engine)
        armExpandAfterPlay(engine, queue, index, generation)
        schedulePersistPlaybackSession()
    }

    private fun playbackEngine(): Player? {
        val local = PlaybackService.instance?.localPlayer()
        if (PlaybackStartPolicy.preferInProcessPlayer(local != null) && local != null) {
            ensureLocalPlayerMirrored(local)
            return local
        }
        return _mediaController.value
    }

    private fun ensureLocalPlayerMirrored(player: Player) {
        if (localPlayerMirrorAttached) return
        localPlayerMirrorAttached = true
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlaybackUiFlags(player)
                if (isPlaying) {
                    PlayLatencyTracker.markIsPlaying()
                    maybeExpandQueue(player, fallback = false)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncPlaybackUiFlags(player)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                syncPlaybackUiFlags(player)
            }
        })
    }

    private fun ensureAudibleVolume(player: Player) {
        if (player.volume < 0.99f) player.volume = 1f
        PlaybackService.instance?.ensureAudibleVolume()
    }

    /**
     * Chemin rapide : 2–3 lectures binder (premier / dernier / courant), jamais un scan N.
     */
    private fun tryFastSeekPlayback(
        player: Player,
        queue: List<Song>,
        index: Int,
        generation: Int,
    ): Boolean {
        if (index !in queue.indices || player.mediaItemCount <= 0) return false
        val targetId = queue[index].uri.toString()
        val held = currentWindow()
        val firstId = peekPlayerMediaId(player, 0)
        val lastId = peekPlayerMediaId(player, player.mediaItemCount - 1)
        val holding = PlaybackWindowOps.isPlayerHoldingWindow(
            player.mediaItemCount,
            firstId,
            lastId,
            queue.getOrNull(held.start)?.uri?.toString(),
            queue.getOrNull(held.endExclusive - 1)?.uri?.toString(),
            held.size(),
        )
        val playerIndex = when {
            holding -> held.toPlayerIndex(index)
            player.currentMediaItem?.mediaId == targetId &&
                held.containsLogical(index) -> player.currentMediaItemIndex
            else -> {
                val windowIdx = held.toPlayerIndex(index)
                if (windowIdx >= 0 && peekPlayerMediaId(player, windowIdx) == targetId) windowIdx else -1
            }
        }
        if (playerIndex < 0) return false
        if (peekPlayerMediaId(player, playerIndex) != targetId &&
            player.currentMediaItem?.mediaId != targetId
        ) {
            return false
        }
        PlayLatencyTracker.markPrepare("fast-seek-window", queue.size, index, 0L)
        player.seekTo(playerIndex, 0L)
        ensureAudibleVolume(player)
        PlayLatencyTracker.markPlayIssued(true)
        player.playWhenReady = true
        player.play()
        syncPlaybackUiFlags(player)
        _playbackQueueIndex.value = index
        return true
    }

    private fun peekPlayerMediaId(player: Player, index: Int): String? {
        if (index < 0 || index >= player.mediaItemCount) return null
        return runCatching { player.getMediaItemAt(index).mediaId }.getOrNull()
    }

    private fun currentWindow(): PlaybackWindowOps.Window =
        PlaybackWindowOps.Window(playerWindowStart, playerWindowEndExclusive, _playbackQueueIndex.value)

    private fun rememberWindow(window: PlaybackWindowOps.Window) {
        playerWindowStart = window.start
        playerWindowEndExclusive = window.endExclusive
    }

    private fun armExpandAfterPlay(
        player: Player,
        queue: List<Song>,
        index: Int,
        generation: Int,
    ) {
        expandFallbackJob?.cancel()
        playIssuedAtMs = SystemClock.elapsedRealtime()
        if (queue.size <= 1) {
            pendingExpand = null
            return
        }
        val mediaId = queue.getOrNull(index)?.uri?.toString() ?: return
        pendingExpand = PendingExpand(queue, index, generation, mediaId)
        maybeExpandQueue(player, fallback = false)
        expandFallbackJob = scope.launch {
            delay(PlaybackStartPolicy.EXPAND_FALLBACK_MS)
            val live = playbackEngine() ?: return@launch
            maybeExpandQueue(live, fallback = true)
        }
    }

    private fun maybeExpandQueue(player: Player, fallback: Boolean) {
        val pending = pendingExpand ?: return
        if (queueExpandJob?.isActive == true) return
        val genOk = pending.generation == playGeneration.get()
        val idOk = player.currentMediaItem?.mediaId == pending.expectedMediaId
            || pending.expectedMediaId == pendingPlayMediaId
        val heard = PlaybackStartPolicy.isFirstAudioHeard(player.isPlaying, player.currentPosition)
        val ok = if (fallback) {
            val elapsed = if (playIssuedAtMs == 0L) {
                PlaybackStartPolicy.EXPAND_FALLBACK_MS
            } else {
                SystemClock.elapsedRealtime() - playIssuedAtMs
            }
            PlaybackStartPolicy.shouldExpandFallback(true, elapsed, genOk, idOk)
        } else {
            PlaybackStartPolicy.shouldExpandAfterPlayIssued(true, true, genOk, idOk)
                || PlaybackStartPolicy.shouldExpandAfterFirstAudio(true, heard, genOk, idOk)
        }
        if (!ok) return
        val window = PlaybackWindowOps.compute(pending.index, pending.queue.size, EXPAND_RADIUS)
        PlayLatencyTracker.markExpand(
            when {
                fallback -> "fallback"
                heard -> "first-audio"
                else -> "play-issued"
            },
            pending.index - window.start,
            window.endExclusive - pending.index - 1,
        )
        expandQueueAroundCurrent(
            player,
            pending.queue,
            pending.index,
            keepPlaying = true,
            generation = pending.generation,
            addOnly = true,
        )
    }

    private fun isSamePlaybackContext(queue: List<Song>): Boolean {
        val current = _playbackQueue.value
        if (current.isEmpty() || current.size != queue.size) return false
        if (current.size <= LARGE_QUEUE_COMPARE_THRESHOLD) {
            for (i in current.indices) {
                if (current[i].id != queue[i].id) return false
            }
            return true
        }
        return current.first().id == queue.first().id && current.last().id == queue.last().id
    }

    private fun schedulePersistPlaybackSession() {
        scope.launch(Dispatchers.Main.immediate) {
            persistPlaybackSession()
        }
    }

    private fun flushPendingPlayRequest() {
        val pending = pendingPlayRequest ?: return
        pendingPlayRequest = null
        if (pending.generation != playGeneration.get()) return
        val index = pending.queue.indexOfFirst { it.id == pending.startSong.id }.takeIf { it >= 0 }
            ?: pending.queue.indexOf(pending.startSong)
        if (index < 0) return
        playerCommandGate.runExclusive {
            playSongsExclusive(pending.queue, pending.startSong, index, pending.generation)
        }
    }

    private fun expandQueueAroundCurrent(
        player: Player,
        queue: List<Song>,
        startIndex: Int,
        keepPlaying: Boolean,
        generation: Int,
        addOnly: Boolean = false,
    ) {
        queueExpandJob?.cancel()
        val expectedMediaId = queue.getOrNull(startIndex)?.uri?.toString() ?: return
        val window = PlaybackWindowOps.compute(startIndex, queue.size, EXPAND_RADIUS)
        queueExpandJob = scope.launch(Dispatchers.Default) {
            val before = queue.subList(window.start, window.logicalIndex).map { songToMediaItem(it) }
            val after = queue.subList(window.logicalIndex + 1, window.endExclusive).map { songToMediaItem(it) }
            withContext(Dispatchers.Main) {
                if (!isActive || generation != playGeneration.get()) return@withContext
                val live = playbackEngine() ?: return@withContext
                if (live.currentMediaItem?.mediaId != expectedMediaId
                    && expectedMediaId != pendingPlayMediaId
                ) {
                    return@withContext
                }
                applyExpandWithRetry(
                    live = live,
                    before = before,
                    after = after,
                    window = window,
                    keepPlaying = keepPlaying,
                    expectedMediaId = expectedMediaId,
                    logicalIndex = startIndex,
                    addOnly = addOnly,
                )
            }
        }
    }

    private suspend fun applyExpandWithRetry(
        live: Player,
        before: List<MediaItem>,
        after: List<MediaItem>,
        window: PlaybackWindowOps.Window,
        keepPlaying: Boolean,
        expectedMediaId: String,
        logicalIndex: Int,
        addOnly: Boolean = false,
    ) {
        fun apply(): Boolean {
            val currentId = live.currentMediaItem?.mediaId
            if (currentId != null && currentId != expectedMediaId) return true
            if (currentId == null) return false
            val playWhenReadyBefore = live.playWhenReady
            if (PlaybackStartPolicy.canExpandWithAddOnly(live.mediaItemCount)) {
                if (before.isNotEmpty()) live.addMediaItems(0, before)
                if (after.isNotEmpty()) live.addMediaItems(after)
                if ((keepPlaying || playWhenReadyBefore) && !live.playWhenReady) {
                    live.playWhenReady = true
                    live.play()
                }
            } else if (addOnly || PlaybackStartPolicy.shouldResetPlaylistToExpand(live.mediaItemCount)) {
                pendingExpand = null
                expandFallbackJob?.cancel()
                return true
            } else if (live.mediaItemCount != window.size() ||
                (before.isNotEmpty() && peekPlayerMediaId(live, 0) != before.first().mediaId)
            ) {
                val position = live.currentPosition.coerceAtLeast(0L)
                val items = ArrayList<MediaItem>(window.size())
                items.addAll(before)
                live.currentMediaItem?.let { items.add(it) } ?: return false
                items.addAll(after)
                live.setMediaItems(items, before.size, position)
                live.prepare()
                if (keepPlaying || playWhenReadyBefore) {
                    ensureAudibleVolume(live)
                    live.playWhenReady = true
                    live.play()
                }
            }
            if (keepPlaying && playWhenReadyBefore && !live.playWhenReady) {
                ensureAudibleVolume(live)
                live.playWhenReady = true
                live.play()
            } else if (keepPlaying && !addOnly && playWhenReadyBefore && !live.isPlaying) {
                ensureAudibleVolume(live)
                live.play()
            }
            rememberWindow(window)
            _playbackQueueIndex.value = logicalIndex
            pendingExpand = null
            expandFallbackJob?.cancel()
            return true
        }

        repeat(EXPAND_LOCK_RETRIES) { attempt ->
            val locked = playerCommandGate.tryWithLock { apply() }
            if (locked) return
            delay(EXPAND_LOCK_RETRY_MS * (attempt + 1))
        }
        playerCommandGate.withLock { apply() }
    }

    private fun reshapePlayerWindow(
        player: Player,
        queue: List<Song>,
        logicalIndex: Int,
        keepPlaying: Boolean,
        keepPosition: Boolean = true,
    ) {
        if (queue.isEmpty()) {
            runCatching { player.clearMediaItems() }
            rememberWindow(PlaybackWindowOps.Window(0, 0, 0))
            return
        }
        val safeIndex = logicalIndex.coerceIn(0, queue.lastIndex)
        val window = PlaybackWindowOps.compute(safeIndex, queue.size, EXPAND_RADIUS)
        val items = queue.subList(window.start, window.endExclusive).map { songToMediaItem(it) }
        val position = if (keepPosition) player.currentPosition.coerceAtLeast(0L) else 0L
        val playWhenReady = player.playWhenReady
        runCatching {
            player.setMediaItems(items, window.playerIndex, position)
            player.prepare()
            if (keepPlaying || playWhenReady) {
                ensureAudibleVolume(player)
                player.play()
            }
        }
        rememberWindow(window)
        _playbackQueueIndex.value = safeIndex
    }

    private fun maybeReexpandAfterTransition(controller: MediaController) {
        val queue = _playbackQueue.value
        if (queue.size <= 1) return
        val logical = _playbackQueueIndex.value
        if (!PlaybackWindowOps.shouldReExpand(currentWindow(), logical, queue.size, EXPAND_RADIUS, EXPAND_HYSTERESIS)) {
            return
        }
        expandQueueAroundCurrent(
            controller,
            queue,
            logical,
            keepPlaying = controller.playWhenReady,
            generation = playGeneration.get(),
            addOnly = false,
        )
    }

    private fun handleLogicalQueueEnded(controller: MediaController) {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return
        val logical = _playbackQueueIndex.value
        if (_repeatMode.value == Player.REPEAT_MODE_ONE) return
        val next = logical + 1
        if (next < queue.size) {
            seekLogicalIndex(controller, queue, next, play = true)
            return
        }
        if (_repeatMode.value == Player.REPEAT_MODE_ALL && queue.size > 1) {
            seekLogicalIndex(controller, queue, 0, play = true)
        }
    }

    private fun seekLogicalIndex(
        player: Player,
        queue: List<Song>,
        logicalIndex: Int,
        play: Boolean,
    ) {
        if (logicalIndex !in queue.indices) return
        val target = queue[logicalIndex]
        pendingPlayMediaId = target.uri.toString()
        _currentSong.value = library.displaySong(target)
        _playbackDuration.value = target.duration.takeIf { it > 0L } ?: 0L
        _playbackPosition.value = 0L
        _playbackQueueIndex.value = logicalIndex
        val playerIndex = currentWindow().toPlayerIndex(logicalIndex)
        val inWindow = playerIndex >= 0 && peekPlayerMediaId(player, playerIndex) == target.uri.toString()
        if (inWindow) {
            runCatching { player.seekTo(playerIndex, 0L) }
            if (play) {
                ensureAudibleVolume(player)
                beginPlayUiPending("seek-logical")
                player.play()
            }
            if (PlaybackWindowOps.shouldReExpand(currentWindow(), logicalIndex, queue.size, EXPAND_RADIUS, EXPAND_HYSTERESIS)) {
                expandQueueAroundCurrent(player, queue, logicalIndex, keepPlaying = play, generation = playGeneration.get())
            }
        } else {
            playGeneration.incrementAndGet()
            queueExpandJob?.cancel()
            expandFallbackJob?.cancel()
            pendingExpand = null
            reshapePlayerWindow(player, queue, logicalIndex, keepPlaying = play, keepPosition = false)
            if (play) beginPlayUiPending("seek-logical-reshape")
        }
    }

    private fun setPlaybackContext(queue: List<Song>, startIndex: Int) {
        val safeIndex = startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        _playbackQueueIndex.value = safeIndex
        if (queue.size <= QUEUE_EAGER_DISPLAY_MAX) {
            _playbackQueue.value = queue.map { library.displaySong(it) }
            return
        }
        // Grande bibliothèque : UI immédiate sans mapper des milliers de titres sur le Main.
        _playbackQueue.value = queue
        val expectedIndex = safeIndex
        val queueHeadId = queue.firstOrNull()?.id
        scope.launch(Dispatchers.Default) {
            val displayed = queue.map { library.displaySong(it) }
            withContext(Dispatchers.Main.immediate) {
                if (_playbackQueueIndex.value == expectedIndex &&
                    _playbackQueue.value.size == queue.size &&
                    _playbackQueue.value.firstOrNull()?.id == queueHeadId
                ) {
                    _playbackQueue.value = displayed
                }
            }
        }
    }

    /** Index logique via mediaId — jamais l'index player brut (fenêtre ±radius). */
    private fun syncLogicalIndexFromPlayer(player: Player) {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return
        val mediaId = player.currentMediaItem?.mediaId
        val hint = playerWindowStart + PlayerGuards.safeCurrentIndex(player)
        val ids = queue.map { it.uri.toString() }
        val logical = PlaybackWindowOps.resolveLogicalIndex(ids, mediaId, hint)
        _playbackQueueIndex.value = logical
    }

    private fun maybeRestorePlaybackQueueFromPlayer(player: Player) {
        if (_playbackQueue.value.isNotEmpty() || player.mediaItemCount <= 0) return
        scope.launch(Dispatchers.Default) {
            val rebuilt = PlayerGuards.rebuildPlaylistFromPlayer(player, library.allSongs.value)
            if (_playbackQueue.value.isEmpty() && rebuilt.isNotEmpty()) {
                val playerIndex = PlayerGuards.safeCurrentIndex(player)
                _playbackQueue.value = rebuilt.map { library.displaySong(it) }
                rememberWindow(PlaybackWindowOps.Window(0, rebuilt.size, playerIndex))
                _playbackQueueIndex.value = playerIndex.coerceIn(0, rebuilt.lastIndex)
            }
        }
    }

    private fun tryRestorePlaybackSession() {
        if (sessionRestoreAttempted) return
        if (playGeneration.get() > 0 || pendingPlayRequest != null || pendingPlayMediaId != null) {
            sessionRestoreAttempted = true
            return
        }
        val controller = _mediaController.value ?: return
        if (library.allSongs.value.isEmpty()) return

        if (controller.mediaItemCount > 0) {
            sessionRestoreAttempted = true
            updateCurrentSongFromMediaItem(controller.currentMediaItem)
            maybeRestorePlaybackQueueFromPlayer(controller)
            syncPlaybackUiFlags(controller)
            _playbackPosition.value = controller.currentPosition.coerceAtLeast(0L)
            return
        }
        if (_currentSong.value != null) {
            sessionRestoreAttempted = true
            return
        }

        val snapshot = PlaybackSessionStore.read(application) ?: run {
            sessionRestoreAttempted = true
            return
        }

        val all = library.allSongs.value
        val queue = snapshot.queueIds.mapNotNull { id -> all.find { it.id == id } }
        val song = all.find { it.id == snapshot.songId } ?: queue.firstOrNull()
        if (song == null) {
            PlaybackSessionStore.clear(application)
            sessionRestoreAttempted = true
            return
        }

        val safeQueue = queue.ifEmpty { listOf(song) }
        val index = safeQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val position = snapshot.positionMs.coerceAtLeast(0L)
        sessionRestoreAttempted = true
        val restoreGen = playGeneration.get()

        playerCommandGate.runExclusive {
            if (playGeneration.get() != restoreGen || pendingPlayMediaId != null) return@runExclusive
            runCatching {
                setPlaybackContext(safeQueue, index)
                controller.volume = 1f
                controller.setMediaItem(songToMediaItem(safeQueue[index]), position)
                controller.prepare()
                controller.playWhenReady = false
                controller.pause()
                _currentSong.value = library.displaySong(safeQueue[index])
                _playbackPosition.value = position
                _playbackDuration.value = safeQueue[index].duration.takeIf { it > 0L } ?: 0L
                _isPlaying.value = false
                setBufferingUi(false, "session-restore")
                if (safeQueue.size > 1) {
                    expandQueueAroundCurrent(controller, safeQueue, index, keepPlaying = false, generation = restoreGen)
                }
                onEqualizerTrackChanged(safeQueue[index].id)
            }.onFailure {
                PlaybackSessionStore.clear(application)
            }
        }
    }

    private fun persistPlaybackSession() {
        val song = _currentSong.value ?: return
        val queueIds = _playbackQueue.value
            .asSequence()
            .map { it.id }
            .take(PlaybackSessionStore.MAX_QUEUE_IDS)
            .toList()
            .ifEmpty { listOf(song.id) }
        val position = playbackEngine()?.currentPosition ?: _playbackPosition.value
        PlaybackSessionStore.save(application, song.id, position, queueIds)
    }

    private fun ensureEngineListener(player: Player) {
        ensureLocalPlayerMirrored(player)
    }

    private fun ensureProgressUpdate() {
        if (progressStarted) return
        progressStarted = true
        startProgressUpdate()
    }

    private fun startProgressUpdate() {
        scope.launch {
            while (true) {
                val player = playbackEngine()
                publishPlaybackProgress(player)
                if (player != null && pendingExpand != null) {
                    maybeExpandQueue(player, fallback = false)
                }
                if (player?.isPlaying == true) {
                    val now = System.currentTimeMillis()
                    onListeningSecond()
                    _currentSong.value?.let { song ->
                        val duration = _playbackDuration.value.takeIf { it > 0L }
                            ?: song.duration.takeIf { it > 0L } ?: 0L
                        onScrobbleProgress(song, player.currentPosition, duration)
                    }
                    PlayLatencyTracker.markFirstNonZeroPosition(player.currentPosition)
                    if (now - lastSessionPersistAtMs >= SESSION_PERSIST_INTERVAL_MS) {
                        lastSessionPersistAtMs = now
                        persistPlaybackSession()
                    }
                    delay(PROGRESS_TICK_PLAYING_MS)
                } else if (player?.playWhenReady == true || _isBuffering.value) {
                    delay(PROGRESS_TICK_PENDING_MS)
                } else {
                    delay(PROGRESS_TICK_IDLE_MS)
                }
            }
        }
    }

    private fun syncPlaybackUiFlags(player: Player?) {
        if (player == null) {
            setBufferingUi(false, "no-player")
            _isPlaying.value = false
            return
        }
        val playing = player.isPlaying
        val wantsPlay = player.playWhenReady
        val state = player.playbackState
        if (state != lastLoggedPlaybackState) {
            lastLoggedPlaybackState = state
            PlayLatencyTracker.markPlaybackState(
                playbackStateLabel(state),
                wantsPlay,
                playing,
            )
        }
        if (playing) bufferingUiTimedOut = false
        _isPlaying.value = PlaybackStartPolicy.showPlayingIcon(playing)
        val exoBuffering = PlaybackStartPolicy.showBufferingSpinner(
            playing,
            wantsPlay,
            state == Player.STATE_BUFFERING,
            _isControllerConnecting.value && PlaybackService.instance?.localPlayer() == null,
            bufferingUiTimedOut,
        )
        setBufferingUi(exoBuffering, if (exoBuffering) "STATE_BUFFERING" else "clear")
    }

    private fun setBufferingUi(buffering: Boolean, reason: String) {
        if (buffering && bufferingUiTimedOut) return
        if (_isBuffering.value == buffering) {
            if (!buffering) bufferingUiTimeoutJob?.cancel()
            return
        }
        _isBuffering.value = buffering
        PlayLatencyTracker.markBuffering(buffering, reason)
        bufferingUiTimeoutJob?.cancel()
        if (buffering) {
            bufferingUiTimeoutJob = scope.launch {
                delay(PlaybackStartPolicy.BUFFERING_SPINNER_MAX_MS)
                if (_isBuffering.value) {
                    bufferingUiTimedOut = true
                    _isBuffering.value = false
                }
            }
        }
    }

    private fun beginPlayUiPending(reason: String) {
        bufferingUiTimedOut = false
        _isPlaying.value = false
        setBufferingUi(true, reason)
    }

    private fun publishPlaybackProgress(controller: Player?) {
        if (controller == null) return
        val pos = controller.currentPosition.coerceAtLeast(0L)
        _playbackPosition.value = pos
        val playerDur = controller.duration
        val songDur = _currentSong.value?.duration?.takeIf { it > 0L } ?: 0L
        _playbackDuration.value = when {
            playerDur > 0L -> playerDur
            songDur > 0L -> songDur
            else -> 0L
        }
    }

    private fun controllerNotReadyMessage(): String =
        if (_isControllerConnecting.value) "Connexion au lecteur…"
        else "Lecteur non prêt — réessayez dans un instant"

    private fun dispatchPlayerCommand(command: PlayerUiCommand) {
        val controller = _mediaController.value
        when (command) {
            PlayerUiCommand.PlayPause -> {
                val engine = playbackEngine()
                if (engine == null) {
                    _playbackError.value = controllerNotReadyMessage()
                    return
                }
                val pause = PlaybackStartPolicy.shouldPauseOnToggle(
                    engine.isPlaying,
                    engine.playWhenReady,
                    _isBuffering.value,
                )
                if (pause) {
                    engine.pause()
                    _isPlaying.value = false
                    setBufferingUi(false, "user-pause")
                } else {
                    ensureAudibleVolume(engine)
                    beginPlayUiPending("play-pause")
                    engine.playWhenReady = true
                    engine.play()
                }
            }
            PlayerUiCommand.SkipNext, PlayerUiCommand.SkipPrevious -> Unit
            PlayerUiCommand.ToggleShuffle -> {
                if (controller == null) {
                    _playbackError.value = controllerNotReadyMessage()
                    return
                }
                toggleLogicalShuffle(controller)
            }
            PlayerUiCommand.CycleRepeat -> {
                val current = _repeatMode.value
                val next = when (current) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                _repeatMode.value = next
                PlaybackPreferences.setRepeatMode(application, next)
                controller?.let { applyPlayerRepeatMode(it, next) }
            }
            is PlayerUiCommand.SeekToQueueIndex -> {
                val engine = playbackEngine() ?: return
                seekLogicalIndex(engine, _playbackQueue.value, command.index, play = true)
            }
            is PlayerUiCommand.SeekToPosition -> PlayerGuards.safeSeekToPosition(playbackEngine(), command.positionMs)
        }
    }

    private fun applySkipDeltaExclusive(delta: Int) {
        val engine = playbackEngine() ?: return
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return
        if (delta == -1 && engine.currentPosition > PlayerGuards.PREVIOUS_RESTART_THRESHOLD_MS) {
            runCatching { engine.seekTo(0) }
            return
        }
        val from = _playbackQueueIndex.value.coerceIn(0, queue.lastIndex)
        var targetIndex = from + delta
        if (_repeatMode.value == Player.REPEAT_MODE_ALL && queue.size > 1) {
            targetIndex = Math.floorMod(targetIndex, queue.size)
        } else {
            targetIndex = targetIndex.coerceIn(0, queue.lastIndex)
        }
        if (targetIndex == from && delta != 0) return
        playGeneration.incrementAndGet()
        queueExpandJob?.cancel()
        expandFallbackJob?.cancel()
        pendingExpand = null
        beginPlayUiPending("skip")
        seekLogicalIndex(engine, queue, targetIndex, play = true)
        syncPlaybackUiFlags(engine)
    }

    private fun toggleLogicalShuffle(controller: MediaController) {
        val enabling = !_shuffleEnabled.value
        val queue = _playbackQueue.value
        val current = _playbackQueueIndex.value.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        if (enabling) {
            orderBeforeShuffle = queue
            if (queue.size > 1 && current in queue.indices) {
                val head = queue.take(current + 1)
                val rest = queue.drop(current + 1).shuffled()
                _playbackQueue.value = head + rest
            }
        } else {
            val original = orderBeforeShuffle
            orderBeforeShuffle = null
            if (original != null && queue.isNotEmpty()) {
                val song = queue[current]
                val restoredIndex = original.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                _playbackQueue.value = original
                _playbackQueueIndex.value = restoredIndex
            }
        }
        _shuffleEnabled.value = enabling
        PlaybackPreferences.setShuffleEnabled(application, enabling)
        if (controller.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) {
            controller.shuffleModeEnabled = false
        }
        if (queue.size > 1) {
            reshapePlayerWindow(
                controller,
                _playbackQueue.value,
                _playbackQueueIndex.value,
                keepPlaying = controller.playWhenReady,
            )
        }
    }

    private fun applyPlayerRepeatMode(player: Player, logicalMode: Int) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) return
        val playerMode = when (logicalMode) {
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        if (player.repeatMode != playerMode) {
            player.repeatMode = playerMode
        }
    }

    private fun handlePlaybackError(controller: MediaController?, error: PlaybackException) {
        val mediaId = controller?.currentMediaItem?.mediaId
        val title = _currentSong.value?.title ?: "ce titre"
        val baseMessage = PlayerGuards.userMessageForPlaybackError(error.errorCode, error.errorCodeName)
        if (mediaId != null) {
            if (invalidMediaIds.size >= MAX_INVALID_MEDIA_IDS) invalidMediaIds.clear()
            invalidMediaIds.add(mediaId)
        }
        setBufferingUi(false, "playback-error")
        _isPlaying.value = false
        PlaybackService.instance?.ensureAudibleVolume()

        val retries = playbackErrorRetries.incrementAndGet()
        if (retries <= MAX_PLAYBACK_RETRIES && controller != null) {
            _playbackError.value = "$baseMessage — nouvelle tentative…"
            scope.launch(Dispatchers.Main.immediate) {
                delay(250)
                playerCommandGate.runExclusive {
                    val live = _mediaController.value ?: return@runExclusive
                    runCatching {
                        live.prepare()
                        live.play()
                        beginPlayUiPending("retry")
                    }
                }
            }
            return
        }

        playbackErrorRetries.set(0)
        if (consecutiveAutoSkips >= MAX_CONSECUTIVE_AUTO_SKIPS) {
            _playbackError.value = "$baseMessage — lecture interrompue (« $title »)"
            controller?.pause()
            return
        }

        playerCommandGate.runExclusive {
            val live = _mediaController.value ?: return@runExclusive
            val queue = _playbackQueue.value
            val nextLogical = (_playbackQueueIndex.value + 1).coerceAtMost(queue.lastIndex)
            if (queue.isNotEmpty() && nextLogical != _playbackQueueIndex.value) {
                consecutiveAutoSkips++
                _playbackError.value = "$baseMessage — passage au suivant (« $title »)"
                seekLogicalIndex(live, queue, nextLogical, play = true)
            } else {
                _playbackError.value = "$baseMessage — impossible de lire « $title »"
                live.pause()
            }
        }
    }

    private fun playbackStateLabel(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "STATE_$state"
    }

    companion object {
        private const val TAG = "PlaybackManager"
        private const val EXPAND_RADIUS = 32
        private const val EXPAND_HYSTERESIS = 8
        private const val EXPAND_LOCK_RETRIES = 3
        private const val EXPAND_LOCK_RETRY_MS = 40L
        private const val PROGRESS_TICK_PLAYING_MS = 250L
        private const val PROGRESS_TICK_PENDING_MS = 200L
        private const val PROGRESS_TICK_IDLE_MS = 1_000L
        private const val SESSION_PERSIST_INTERVAL_MS = 5_000L
        private const val MAX_PLAYBACK_RETRIES = 1
        private const val MAX_CONSECUTIVE_AUTO_SKIPS = 5
        private const val MAX_INVALID_MEDIA_IDS = 64
        /** Au-delà, le mapping displaySong est différé (Default) pour ne pas bloquer le tap. */
        private const val QUEUE_EAGER_DISPLAY_MAX = 128
        /** Comparaison O(n) du contexte file seulement pour les files raisonnables. */
        private const val LARGE_QUEUE_COMPARE_THRESHOLD = 256
    }
}
