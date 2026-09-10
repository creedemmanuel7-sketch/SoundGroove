package com.credo.soundgroove.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStartPolicyTest {

    @Test
    fun playingIcon_neverFakesPlayback() {
        assertFalse(PlaybackStartPolicy.showPlayingIcon(false))
        assertTrue(PlaybackStartPolicy.showPlayingIcon(true))
    }

    @Test
    fun spinner_whileWantsPlayAndNotPlaying() {
        assertTrue(
            PlaybackStartPolicy.showBufferingSpinner(
                false,
                true,
                true,
                false,
                false,
            ),
        )
        assertTrue(
            PlaybackStartPolicy.showBufferingSpinner(
                false,
                true,
                false,
                false,
                false,
            ),
        )
        assertFalse(
            PlaybackStartPolicy.showBufferingSpinner(
                true,
                true,
                true,
                false,
                false,
            ),
        )
        assertFalse(
            PlaybackStartPolicy.showBufferingSpinner(
                false,
                true,
                true,
                false,
                true,
            ),
        )
        assertTrue(
            PlaybackStartPolicy.showBufferingSpinner(
                false,
                false,
                false,
                true,
                false,
            ),
        )
    }

    @Test
    fun toggle_doesNotPauseSilentPrepareWithoutSpinner() {
        assertFalse(PlaybackStartPolicy.shouldPauseOnToggle(false, true, false))
        assertTrue(PlaybackStartPolicy.shouldPauseOnToggle(true, true, false))
        assertTrue(PlaybackStartPolicy.shouldPauseOnToggle(false, true, true))
        assertFalse(PlaybackStartPolicy.shouldPauseOnToggle(false, false, false))
    }

    @Test
    fun firstAudio_requiresPlayingOrPosition() {
        assertFalse(PlaybackStartPolicy.isFirstAudioHeard(false, 0L))
        assertTrue(PlaybackStartPolicy.isFirstAudioHeard(true, 0L))
        assertTrue(PlaybackStartPolicy.isFirstAudioHeard(false, 12L))
    }

    @Test
    fun expand_onlyAfterFirstAudio() {
        assertTrue(PlaybackStartPolicy.shouldExpandAfterFirstAudio(true, true, true, true))
        assertFalse(PlaybackStartPolicy.shouldExpandAfterFirstAudio(true, false, true, true))
        assertFalse(PlaybackStartPolicy.shouldExpandAfterFirstAudio(true, true, false, true))
        assertFalse(PlaybackStartPolicy.shouldExpandAfterFirstAudio(true, true, true, false))
        assertFalse(PlaybackStartPolicy.shouldExpandAfterFirstAudio(false, true, true, true))
    }

    @Test
    fun expand_fallbackAfterDelayNotAtPlay() {
        assertFalse(
            PlaybackStartPolicy.shouldExpandFallback(
                true,
                0L,
                true,
                true,
            ),
        )
        assertFalse(
            PlaybackStartPolicy.shouldExpandFallback(
                true,
                PlaybackStartPolicy.EXPAND_FALLBACK_MS - 1,
                true,
                true,
            ),
        )
        assertTrue(
            PlaybackStartPolicy.shouldExpandFallback(
                true,
                PlaybackStartPolicy.EXPAND_FALLBACK_MS,
                true,
                true,
            ),
        )
        assertFalse(
            PlaybackStartPolicy.shouldExpandFallback(
                true,
                PlaybackStartPolicy.EXPAND_FALLBACK_MS,
                false,
                true,
            ),
        )
    }

    @Test
    fun expand_addOnlyOnSingleItem() {
        assertTrue(PlaybackStartPolicy.canExpandWithAddOnly(1))
        assertFalse(PlaybackStartPolicy.canExpandWithAddOnly(65))
        assertFalse(PlaybackStartPolicy.shouldResetPlaylistToExpand(1))
    }

    @Test
    fun preferInProcess_whenServicePlayerExists() {
        assertTrue(PlaybackStartPolicy.preferInProcessPlayer(true))
        assertFalse(PlaybackStartPolicy.preferInProcessPlayer(false))
    }

    @Test
    fun catalogQueue_whenTapOutsideCurrentQueue() {
        assertTrue(PlaybackStartPolicy.useCatalogAsLogicalQueue(false, 4000))
        assertFalse(PlaybackStartPolicy.useCatalogAsLogicalQueue(true, 4000))
        assertFalse(PlaybackStartPolicy.useCatalogAsLogicalQueue(false, 1))
        assertFalse(PlaybackStartPolicy.useCatalogAsLogicalQueue(false, 0))
    }

    @Test
    fun spinnerTimeout_isShortNotStickySilence() {
        assertTrue(PlaybackStartPolicy.BUFFERING_SPINNER_MAX_MS <= 3_000L)
        assertTrue(PlaybackStartPolicy.BUFFERING_SPINNER_MAX_MS >= 800L)
        assertTrue(PlaybackStartPolicy.EXPAND_FALLBACK_MS >= 800L)
        assertTrue(PlaybackStartPolicy.EXPAND_FALLBACK_MS < 5_000L)
    }
}
