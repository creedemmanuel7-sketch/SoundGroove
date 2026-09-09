package com.credo.soundgroove.util

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Instrumente la latence tap → `Player.isPlaying == true` (tag logcat `SG_AUDIO`).
 * Horloge = [System.currentTimeMillis] (testable JVM, pas [android.os.SystemClock]).
 */
object PlayLatencyTracker {
    private const val TAG = "SG_AUDIO"
    private val tapAtMs = AtomicLong(0L)
    private val lastLatencyMs = AtomicLong(0L)
    private val firstNonZeroLogged = AtomicBoolean(false)
    private val lastBuffering = AtomicBoolean(false)

    @Volatile
    private var pendingLabel: String = ""

    fun markTap(label: String = "play") {
        pendingLabel = label
        firstNonZeroLogged.set(false)
        val now = System.currentTimeMillis()
        tapAtMs.set(now)
        logI("play_tap label=$label t=$now")
    }

    fun markBindReady(elapsedMs: Long) {
        logI("bindReady elapsedMs=$elapsedMs" + sinceTapSuffix())
    }

    fun markSetMediaItem(mediaId: String, generation: Int) {
        logI("setMediaItem gen=$generation mediaId=${mediaId.takeLast(48)}" + sinceTapSuffix())
    }

    fun markPrepare(mode: String, queueSize: Int, index: Int, issuedMs: Long) {
        logI("prepare mode=$mode size=$queueSize idx=$index issuedMs=$issuedMs" + sinceTapSuffix())
    }

    fun markPlayIssued(playWhenReady: Boolean) {
        logI("playIssued playWhenReady=$playWhenReady" + sinceTapSuffix())
    }

    fun markPlaybackState(stateLabel: String, playWhenReady: Boolean, isPlaying: Boolean) {
        logI(
            "state=$stateLabel playWhenReady=$playWhenReady isPlaying=$isPlaying" +
                sinceTapSuffix(),
        )
    }

    fun markStaleIgnored(reason: String, generation: Int) {
        logI("stale_ignored reason=$reason gen=$generation" + sinceTapSuffix())
    }

    fun markBuffering(isBuffering: Boolean, reason: String) {
        val prev = lastBuffering.getAndSet(isBuffering)
        if (prev == isBuffering) return
        logI("isBuffering=$isBuffering reason=$reason" + sinceTapSuffix())
    }

    /** Appeler quand le player passe réellement en lecture (`isPlaying`). */
    fun markIsPlaying() {
        val start = tapAtMs.getAndSet(0L)
        if (start <= 0L) return
        val ms = (System.currentTimeMillis() - start).coerceAtLeast(0L)
        lastLatencyMs.set(ms)
        logI("play_latency_ms=$ms label=$pendingLabel (tap→isPlaying)")
        pendingLabel = ""
    }

    fun markFirstNonZeroPosition(positionMs: Long) {
        if (positionMs <= 0L) return
        if (!firstNonZeroLogged.compareAndSet(false, true)) return
        logI("firstNonZeroPosition posMs=$positionMs" + sinceTapSuffix())
    }

    fun lastLatencyMs(): Long = lastLatencyMs.get()

    fun clear() {
        tapAtMs.set(0L)
        lastLatencyMs.set(0L)
        firstNonZeroLogged.set(false)
        lastBuffering.set(false)
        pendingLabel = ""
    }

    private fun sinceTapMs(): Long {
        val start = tapAtMs.get()
        if (start <= 0L) return -1L
        return (System.currentTimeMillis() - start).coerceAtLeast(0L)
    }

    private fun sinceTapSuffix(): String {
        val sinceTap = sinceTapMs()
        return if (sinceTap >= 0L) " sinceTapMs=$sinceTap" else ""
    }

    private fun logI(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {
            // JVM unit tests : android.util.Log non stubbé
        }
    }
}
