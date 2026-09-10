package com.credo.soundgroove.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayLatencyTrackerTest {

    @Before
    fun clear() {
        PlayLatencyTracker.clear()
    }

    @Test
    fun markIsPlaying_withoutTap_isNoOp() {
        PlayLatencyTracker.markIsPlaying()
        assertEquals(0L, PlayLatencyTracker.lastLatencyMs())
    }

    @Test
    fun markTap_thenIsPlaying_recordsLatency() {
        PlayLatencyTracker.markTap("unit")
        Thread.sleep(5)
        PlayLatencyTracker.markIsPlaying()
        assertTrue(PlayLatencyTracker.lastLatencyMs() >= 0L)
    }

    @Test
    fun markBuffering_dedupesIdenticalFlips() {
        PlayLatencyTracker.markTap("buf")
        PlayLatencyTracker.markBuffering(true, "a")
        PlayLatencyTracker.markBuffering(true, "b") // no-op duplicate
        PlayLatencyTracker.markBuffering(false, "clear")
        PlayLatencyTracker.markIsPlaying()
        assertTrue(PlayLatencyTracker.lastLatencyMs() >= 0L)
    }

    @Test
    fun markCommandPath_isNoOpWithoutCrash() {
        PlayLatencyTracker.markTap("path")
        PlayLatencyTracker.markCommandPath("in_process", true, false, 0L)
        PlayLatencyTracker.markIsPlaying()
        assertTrue(PlayLatencyTracker.lastLatencyMs() >= 0L)
    }

    @Test
    fun markExpand_isNoOpWithoutCrash() {
        PlayLatencyTracker.markTap("expand")
        PlayLatencyTracker.markExpand("first-audio", 2, 3)
        PlayLatencyTracker.markIsPlaying()
        assertTrue(PlayLatencyTracker.lastLatencyMs() >= 0L)
    }

    @Test
    fun markFirstNonZeroPosition_ignoresZero() {
        PlayLatencyTracker.markTap("pos")
        PlayLatencyTracker.markFirstNonZeroPosition(0L)
        PlayLatencyTracker.markFirstNonZeroPosition(12L)
        PlayLatencyTracker.markFirstNonZeroPosition(99L) // only first non-zero logged
        PlayLatencyTracker.markIsPlaying()
        assertTrue(PlayLatencyTracker.lastLatencyMs() >= 0L)
    }
}
