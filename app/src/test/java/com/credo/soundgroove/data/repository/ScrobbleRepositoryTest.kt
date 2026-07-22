package com.credo.soundgroove.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleRepositoryTest {

    @Test
    fun scrobbleThreshold_shortTrack_usesMinimum30s() {
        assertEquals(30_000L, ScrobbleRepository.scrobbleThresholdMs(45_000L))
    }

    @Test
    fun scrobbleThreshold_longTrack_capsAtFourMinutes() {
        assertEquals(240_000L, ScrobbleRepository.scrobbleThresholdMs(600_000L))
    }

    @Test
    fun scrobbleThreshold_mediumTrack_usesHalfDuration() {
        assertEquals(90_000L, ScrobbleRepository.scrobbleThresholdMs(180_000L))
    }

    @Test
    fun shouldScrobble_falseBeforeThreshold() {
        assertFalse(ScrobbleRepository.shouldScrobble(20_000L, 180_000L))
    }

    @Test
    fun shouldScrobble_trueAtThreshold() {
        assertTrue(ScrobbleRepository.shouldScrobble(90_000L, 180_000L))
    }
}
