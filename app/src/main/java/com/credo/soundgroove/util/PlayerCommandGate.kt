package com.credo.soundgroove.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sérialise les mutations player sur le Main thread et coalesce les skip next/prev
 * rapides (ex. 10 taps) en un seul déplacement net dans la file Media3.
 */
class PlayerCommandGate(
    private val scope: CoroutineScope,
    private val onSkipDelta: (Int) -> Unit,
    private val onCommand: (PlayerUiCommand) -> Unit,
) {
    private val mutex = Mutex()
    private val channel = Channel<PlayerUiCommand>(Channel.UNLIMITED)
    private val pendingSkipDelta = AtomicInteger(0)
    private var skipFlushJob: Job? = null
    private val workerJob: Job

    init {
        workerJob = scope.launch(Dispatchers.Main.immediate) {
            for (command in channel) {
                if (!isActive) break
                mutex.withLock {
                    runCatching { onCommand(command) }
                }
            }
        }
    }

    fun enqueue(command: PlayerUiCommand) {
        when (command) {
            PlayerUiCommand.SkipNext -> {
                pendingSkipDelta.incrementAndGet()
                scheduleSkipFlush()
            }
            PlayerUiCommand.SkipPrevious -> {
                pendingSkipDelta.decrementAndGet()
                scheduleSkipFlush()
            }
            else -> channel.trySend(command)
        }
    }

    /**
     * Exécute une mutation player hors channel (playSongs, restore…) en respectant
     * le même mutex — évite les races avec skip/playPause en cours.
     */
    fun runExclusive(block: () -> Unit) {
        scope.launch(Dispatchers.Main.immediate) {
            mutex.withLock {
                runCatching(block)
            }
        }
    }

    /**
     * Variante synchrone quand le mutex est libre — évite un tour de boucle Main
     * supplémentaire sur le chemin tap → play (latence perceptible).
     * @return false si le mutex est déjà tenu (l'appelant doit alors [runExclusive]).
     */
    fun tryRunExclusive(block: () -> Unit): Boolean {
        if (!mutex.tryLock()) return false
        try {
            runCatching(block)
        } finally {
            mutex.unlock()
        }
        return true
    }

    /** Variante suspendue pour les jobs déjà sur le Main. */
    suspend fun withLock(block: () -> Unit) {
        mutex.withLock {
            runCatching(block)
        }
    }

    /**
     * Tentative non bloquante — l'expand file ne doit jamais retarder un play/skip.
     * @return false si le mutex est déjà tenu.
     */
    suspend fun tryWithLock(block: () -> Unit): Boolean {
        if (!mutex.tryLock()) return false
        try {
            runCatching(block)
        } finally {
            mutex.unlock()
        }
        return true
    }

    fun cancel() {
        skipFlushJob?.cancel()
        workerJob.cancel()
        channel.close()
    }

    private fun scheduleSkipFlush() {
        skipFlushJob?.cancel()
        skipFlushJob = scope.launch(Dispatchers.Main.immediate) {
            delay(SKIP_COALESCE_MS)
            val delta = pendingSkipDelta.getAndSet(0)
            if (delta == 0) return@launch
            mutex.withLock {
                runCatching { onSkipDelta(delta) }
            }
        }
    }

    companion object {
        /** Fenêtre courte : absorbe next×N sans retarder un skip unique. */
        const val SKIP_COALESCE_MS = 45L
    }
}

sealed class PlayerUiCommand {
    data object PlayPause : PlayerUiCommand()
    data object SkipNext : PlayerUiCommand()
    data object SkipPrevious : PlayerUiCommand()
    data object ToggleShuffle : PlayerUiCommand()
    data object CycleRepeat : PlayerUiCommand()
    data class SeekToQueueIndex(val index: Int) : PlayerUiCommand()
    data class SeekToPosition(val positionMs: Long) : PlayerUiCommand()
}
