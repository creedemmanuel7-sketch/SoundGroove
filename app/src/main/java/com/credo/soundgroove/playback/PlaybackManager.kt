package com.credo.soundgroove.playback

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.SystemClock
import android.util.Log
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
import com.credo.soundgroove.util.EqualizerPreset
import com.credo.soundgroove.util.PlayLatencyTracker
import com.credo.soundgroove.util.PlaybackPreferences
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
    private val invalidMediaIds = mutableSetOf<String>()
    private val playbackErrorRetries = AtomicInteger(0)
    private var consecutiveAutoSkips = 0
    private val playGeneration = AtomicInteger(0)

    @Volatile private var pendingPlayMediaId: String? = null
    @Volatile private var pendingPlayRequest: PendingPlayRequest? = null

    private data class PendingPlayRequest(
        val generation: Int,
        val queue: List<Song>,
        val startSong: Song,
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
        initMediaController()
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
        playSongs(library.songs.value, song)
    }

    fun playSongs(queue: List<Song>, startSong: Song) {
        val safeQueue = queue.ifEmpty { listOf(startSong) }
        val index = safeQueue.indexOfFirst { it.id == startSong.id }.takeIf { it >= 0 }
            ?: safeQueue.indexOf(startSong)
        if (index == -1) return

        val generation = playGeneration.incrementAndGet()
        queueExpandJob?.cancel()
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
            if (!PlayerGuards.safeRemoveMediaItem(controller, index)) return@runExclusive
            _playbackQueue.value = com.credo.soundgroove.util.PlaybackQueueOps.removeItemAt(queue, index)
            syncPlaybackQueueIndex(PlayerGuards.safeCurrentIndex(controller))
            persistPlaybackSession()
        }
    }

    fun moveInPlaybackQueue(from: Int, to: Int) {
        playerCommandGate.runExclusive {
            val controller = _mediaController.value ?: return@runExclusive
            val queue = _playbackQueue.value
            if (from !in queue.indices || to !in queue.indices || from == to) return@runExclusive
            if (!PlayerGuards.safeMoveMediaItem(controller, from, to)) return@runExclusive
            _playbackQueue.value = com.credo.soundgroove.util.PlaybackQueueOps.moveItems(queue, from, to)
            syncPlaybackQueueIndex(PlayerGuards.safeCurrentIndex(controller))
            persistPlaybackSession()
        }
    }

    fun playNext(song: Song) {
        playerCommandGate.runExclusive {
            val controller = _mediaController.value ?: return@runExclusive
            val item = songToMediaItem(song)
            if (controller.mediaItemCount == 0) {
                setPlaybackContext(listOf(song), 0)
                controller.setMediaItems(listOf(item))
                controller.prepare()
            } else {
                val insertAt = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
                controller.addMediaItem(insertAt, item)
                val newList = _playbackQueue.value.toMutableList()
                newList.add(insertAt.coerceIn(0, newList.size), song)
                _playbackQueue.value = newList
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
                controller.setMediaItems(listOf(item))
                controller.prepare()
            } else {
                controller.addMediaItem(item)
                _playbackQueue.value = _playbackQueue.value + song
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
                if (player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE) &&
                    player.shuffleModeEnabled != preferredShuffle
                ) {
                    player.shuffleModeEnabled = preferredShuffle
                }
                if (player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE) &&
                    player.repeatMode != preferredRepeat
                ) {
                    player.repeatMode = preferredRepeat
                }
                _shuffleEnabled.value = player.shuffleModeEnabled
                _repeatMode.value = player.repeatMode
                syncPlaybackQueueIndex(player.currentMediaItemIndex)
                maybeRestorePlaybackQueueFromPlayer(player)
            }
            tryRestorePlaybackSession()
            flushPendingPlayRequest()
            startProgressUpdate()
        }, MoreExecutors.directExecutor())
    }

    private fun createPlayerListener(controller: MediaController): Player.Listener =
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSongFromMediaItem(mediaItem)
                syncPlaybackQueueIndex(controller.currentMediaItemIndex)
                persistPlaybackSession()
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    playbackErrorRetries.set(0)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlaybackUiFlags(controller)
                if (isPlaying) {
                    PlayLatencyTracker.markIsPlaying()
                    consecutiveAutoSkips = 0
                    playbackErrorRetries.set(0)
                    publishPlaybackProgress(controller)
                } else {
                    persistPlaybackSession()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncPlaybackUiFlags(controller)
                if (playbackState == Player.STATE_READY) publishPlaybackProgress(controller)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                syncPlaybackUiFlags(controller)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleEnabled.value = shuffleModeEnabled
                PlaybackPreferences.setShuffleEnabled(application, shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
                PlaybackPreferences.setRepeatMode(application, repeatMode)
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
        beginPlayUiPending("play-tap")
        _isPlaying.value = false
    }

    private fun playSongsExclusive(queue: List<Song>, startSong: Song, index: Int, generation: Int) {
        if (generation != playGeneration.get()) return
        val controller = _mediaController.value
        if (controller == null) {
            pendingPlayRequest = PendingPlayRequest(generation, queue, startSong)
            return
        }
        ensureAudibleVolume(controller)

        if (tryFastSeekPlayback(controller, queue, index, generation)) {
            schedulePersistPlaybackSession()
            return
        }

        val mediaItem = songToMediaItem(queue[index])
        PlayLatencyTracker.markSetMediaItem(mediaItem.mediaId, generation)
        controller.setMediaItem(mediaItem, true)
        if (generation != playGeneration.get()) return
        PlayLatencyTracker.markPrepare("cold-start", queue.size, index, 0L)
        controller.prepare()
        ensureAudibleVolume(controller)
        PlayLatencyTracker.markPlayIssued(true)
        controller.play()
        if (queue.size > 1) {
            expandQueueAroundCurrent(controller, queue, index, keepPlaying = true, generation = generation)
        }
        schedulePersistPlaybackSession()
    }

    /**
     * Chemin rapide inspiré de Rivage : si ExoPlayer a déjà la file demandée (ou le
     * titre cible dans le même contexte), seek au lieu de setMediaItem + prepare.
     */
    private fun tryFastSeekPlayback(
        controller: MediaController,
        queue: List<Song>,
        index: Int,
        generation: Int,
    ): Boolean {
        if (index !in queue.indices) return false
        val target = queue[index]

        if (canFastSeek(controller, queue, index)) {
            PlayLatencyTracker.markPrepare("fast-seek-full", queue.size, index, 0L)
            controller.seekTo(index, 0L)
            ensureAudibleVolume(controller)
            PlayLatencyTracker.markPlayIssued(true)
            controller.play()
            syncPlaybackQueueIndex(index)
            return true
        }

        if (!isSamePlaybackContext(queue)) return false
        val playerIndex = findTrackInPlayer(controller, target)
        if (playerIndex < 0) return false

        PlayLatencyTracker.markPrepare("fast-seek-partial", queue.size, index, 0L)
        controller.seekTo(playerIndex, 0L)
        ensureAudibleVolume(controller)
        PlayLatencyTracker.markPlayIssued(true)
        controller.play()
        syncPlaybackQueueIndex(index)
        if (queue.size > controller.mediaItemCount) {
            expandQueueAroundCurrent(controller, queue, index, keepPlaying = true, generation = generation)
        }
        return true
    }

    /** Skip rebuild when ExoPlayer already holds the same ordered queue. */
    private fun canFastSeek(controller: MediaController, queue: List<Song>, index: Int): Boolean {
        if (controller.mediaItemCount != queue.size || index !in queue.indices) return false
        for (i in queue.indices) {
            val item = runCatching { controller.getMediaItemAt(i) }.getOrNull() ?: return false
            if (item.mediaId != queue[i].uri.toString()) return false
        }
        return true
    }

    private fun findTrackInPlayer(controller: MediaController, song: Song): Int {
        val mediaId = song.uri.toString()
        for (i in 0 until controller.mediaItemCount) {
            if (runCatching { controller.getMediaItemAt(i).mediaId }.getOrNull() == mediaId) return i
        }
        return -1
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
        controller: MediaController,
        queue: List<Song>,
        startIndex: Int,
        keepPlaying: Boolean,
        generation: Int,
    ) {
        queueExpandJob?.cancel()
        val expectedMediaId = queue.getOrNull(startIndex)?.uri?.toString() ?: return
        val windowStart = (startIndex - EXPAND_RADIUS).coerceAtLeast(0)
        val windowEnd = (startIndex + EXPAND_RADIUS + 1).coerceAtMost(queue.size)
        queueExpandJob = scope.launch(Dispatchers.Default) {
            val before = queue.subList(windowStart, startIndex).map { songToMediaItem(it) }
            val after = queue.subList(startIndex + 1, windowEnd).map { songToMediaItem(it) }
            withContext(Dispatchers.Main.immediate) {
                if (!isActive || generation != playGeneration.get()) return@withContext
                val live = _mediaController.value ?: return@withContext
                if (live !== controller || live.currentMediaItem?.mediaId != expectedMediaId) return@withContext
                playerCommandGate.tryWithLock {
                    if (before.isNotEmpty()) live.addMediaItems(0, before)
                    if (after.isNotEmpty()) live.addMediaItems(after)
                    if (keepPlaying && live.playWhenReady && !live.isPlaying) {
                        ensureAudibleVolume(live)
                        live.play()
                    }
                    syncPlaybackQueueIndex(live.currentMediaItemIndex)
                }
            }
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

    private fun syncPlaybackQueueIndex(index: Int) {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return
        _playbackQueueIndex.value = index.coerceIn(0, queue.lastIndex)
    }

    private fun maybeRestorePlaybackQueueFromPlayer(player: Player) {
        if (_playbackQueue.value.isNotEmpty() || player.mediaItemCount <= 0) return
        scope.launch(Dispatchers.Default) {
            val rebuilt = PlayerGuards.rebuildPlaylistFromPlayer(player, library.allSongs.value)
            if (_playbackQueue.value.isEmpty() && rebuilt.isNotEmpty()) {
                _playbackQueue.value = rebuilt.map { library.displaySong(it) }
                _playbackQueueIndex.value = PlayerGuards.safeCurrentIndex(player)
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
        val position = _mediaController.value?.currentPosition ?: _playbackPosition.value
        PlaybackSessionStore.save(application, song.id, position, queueIds)
    }

    private fun startProgressUpdate() {
        scope.launch {
            while (true) {
                val controller = _mediaController.value
                publishPlaybackProgress(controller)
                if (controller?.isPlaying == true) {
                    val now = System.currentTimeMillis()
                    onListeningSecond()
                    _currentSong.value?.let { song ->
                        val duration = _playbackDuration.value.takeIf { it > 0L }
                            ?: song.duration.takeIf { it > 0L } ?: 0L
                        onScrobbleProgress(song, controller.currentPosition, duration)
                    }
                    if (now - lastSessionPersistAtMs >= SESSION_PERSIST_INTERVAL_MS) {
                        lastSessionPersistAtMs = now
                        persistPlaybackSession()
                    }
                    delay(PROGRESS_TICK_PLAYING_MS)
                } else if (controller?.playWhenReady == true || _isBuffering.value) {
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
        if (state != lastLoggedPlaybackState) lastLoggedPlaybackState = state
        _isPlaying.value = playing
        if (playing) bufferingUiTimedOut = false
        val exoBuffering = wantsPlay && !playing && state == Player.STATE_BUFFERING
        setBufferingUi(exoBuffering, if (exoBuffering) "STATE_BUFFERING" else "clear")
    }

    private fun setBufferingUi(buffering: Boolean, reason: String) {
        if (buffering && bufferingUiTimedOut) return
        if (_isBuffering.value == buffering) {
            if (!buffering) bufferingUiTimeoutJob?.cancel()
            return
        }
        _isBuffering.value = buffering
        bufferingUiTimeoutJob?.cancel()
        if (buffering) {
            bufferingUiTimeoutJob = scope.launch {
                delay(BUFFERING_UI_TIMEOUT_MS)
                if (_isBuffering.value && !_isPlaying.value) {
                    bufferingUiTimedOut = true
                    _isBuffering.value = false
                }
            }
        }
    }

    private fun beginPlayUiPending(reason: String) {
        bufferingUiTimedOut = false
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
                if (controller == null) {
                    _playbackError.value = controllerNotReadyMessage()
                    return
                }
                if (controller.isPlaying || (controller.playWhenReady && _isBuffering.value)) {
                    controller.pause()
                } else {
                    ensureAudibleVolume(controller)
                    beginPlayUiPending("play-pause")
                    controller.play()
                }
            }
            PlayerUiCommand.SkipNext, PlayerUiCommand.SkipPrevious -> Unit
            PlayerUiCommand.ToggleShuffle -> {
                when (val next = PlayerGuards.safeToggleShuffle(controller)) {
                    null -> if (controller == null) _playbackError.value = controllerNotReadyMessage()
                    else -> {
                        _shuffleEnabled.value = next
                        PlaybackPreferences.setShuffleEnabled(application, next)
                    }
                }
            }
            PlayerUiCommand.CycleRepeat -> {
                if (controller == null) {
                    _playbackError.value = controllerNotReadyMessage()
                    return
                }
                val mode = PlayerGuards.safeCycleRepeat(controller)
                _repeatMode.value = mode
                PlaybackPreferences.setRepeatMode(application, mode)
            }
            is PlayerUiCommand.SeekToQueueIndex -> {
                if (controller == null) return
                val target = _playbackQueue.value.getOrNull(command.index)
                if (target != null) {
                    playGeneration.incrementAndGet()
                    queueExpandJob?.cancel()
                    pendingPlayMediaId = target.uri.toString()
                    _currentSong.value = target
                    _playbackDuration.value = target.duration.takeIf { it > 0L } ?: 0L
                    _playbackPosition.value = 0L
                    syncPlaybackQueueIndex(command.index)
                }
                if (!PlayerGuards.safeSeekToIndex(controller, command.index)) return
                ensureAudibleVolume(controller)
                beginPlayUiPending("seek-index")
                controller.play()
                syncPlaybackQueueIndex(command.index)
            }
            is PlayerUiCommand.SeekToPosition -> PlayerGuards.safeSeekToPosition(controller, command.positionMs)
        }
    }

    private fun applySkipDeltaExclusive(delta: Int) {
        val controller = _mediaController.value ?: return
        val queue = _playbackQueue.value
        if (queue.isNotEmpty()) {
            val from = _playbackQueueIndex.value.coerceIn(0, queue.lastIndex)
            val targetIndex = (from + delta).coerceIn(0, queue.lastIndex)
            val target = queue[targetIndex]
            playGeneration.incrementAndGet()
            queueExpandJob?.cancel()
            pendingPlayMediaId = target.uri.toString()
            _currentSong.value = target
            _playbackDuration.value = target.duration.takeIf { it > 0L } ?: 0L
            _playbackPosition.value = 0L
            _playbackQueueIndex.value = targetIndex
            beginPlayUiPending("skip")
        }
        if (!PlayerGuards.applySkipDelta(controller, delta)) return
        ensureAudibleVolume(controller)
        syncPlaybackQueueIndex(PlayerGuards.safeCurrentIndex(controller))
        syncPlaybackUiFlags(controller)
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
            val skipped = PlayerGuards.safeSeekToNextSkippingInvalid(live, invalidMediaIds)
            if (skipped) {
                consecutiveAutoSkips++
                _playbackError.value = "$baseMessage — passage au suivant (« $title »)"
                ensureAudibleVolume(live)
                beginPlayUiPending("auto-skip")
                live.play()
                syncPlaybackQueueIndex(PlayerGuards.safeCurrentIndex(live))
            } else {
                _playbackError.value = "$baseMessage — impossible de lire « $title »"
                live.pause()
            }
        }
    }

    private fun ensureAudibleVolume(controller: MediaController) {
        if (controller.volume < 0.99f) controller.volume = 1f
        PlaybackService.instance?.ensureAudibleVolume()
    }

    companion object {
        private const val TAG = "PlaybackManager"
        private const val BUFFERING_UI_TIMEOUT_MS = 2_500L
        private const val EXPAND_RADIUS = 32
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
