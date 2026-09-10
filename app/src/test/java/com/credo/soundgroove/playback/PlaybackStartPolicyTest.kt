package com.credo.soundgroove.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStartPolicyTest {

    @Test
    fun tap_showsPlayingIconImmediatelyEvenIfPlayerSilent() {
        assertTrue(PlaybackStartPolicy.showPlayingIcon(true, false, false))
        assertTrue(PlaybackStartPolicy.showPlayingIcon(true, true, false))
        assertTrue(PlaybackStartPolicy.showPlayingIcon(false, true, false))
        assertFalse(PlaybackStartPolicy.showPlayingIcon(false, false, false))
    }

    @Test
    fun userPause_alwaysShowsPausedIcon() {
        assertFalse(PlaybackStartPolicy.showPlayingIcon(true, true, true))
        assertFalse(PlaybackStartPolicy.showPlayingIcon(true, false, true))
        assertTrue(PlaybackStartPolicy.shouldClearStickyOnPause(true))
        assertFalse(PlaybackStartPolicy.shouldClearStickyOnPause(false))
    }

    @Test
    fun spinner_hiddenWhileStickyPlay() {
        assertFalse(
            PlaybackStartPolicy.showBufferingSpinner(
                true,
                false,
                true,
                true,
                false,
            ),
        )
        assertTrue(
            PlaybackStartPolicy.showBufferingSpinner(
                false,
                false,
                true,
                true,
                false,
            ),
        )
        assertFalse(
            PlaybackStartPolicy.showBufferingSpinner(
                false,
                false,
                true,
                true,
                true,
            ),
        )
        assertFalse(
            PlaybackStartPolicy.showBufferingSpinner(
                false,
                true,
                true,
                true,
                false,
            ),
        )
    }

    @Test
    fun expand_onlyAfterFirstAudioAndMatchingItem() {
        assertTrue(PlaybackStartPolicy.shouldExpandAfterFirstAudio(true, true, true, true))
        assertFalse(PlaybackStartPolicy.shouldExpandAfterFirstAudio(true, false, true, true))
        assertFalse(PlaybackStartPolicy.shouldExpandAfterFirstAudio(true, true, false, true))
        assertFalse(PlaybackStartPolicy.shouldExpandAfterFirstAudio(true, true, true, false))
        assertFalse(PlaybackStartPolicy.shouldExpandAfterFirstAudio(false, true, true, true))
    }

    @Test
    fun expand_addOnlyOnSingleItem_neverResetsPlaylist() {
        assertTrue(PlaybackStartPolicy.canExpandWithAddOnly(1))
        assertFalse(PlaybackStartPolicy.canExpandWithAddOnly(0))
        assertFalse(PlaybackStartPolicy.canExpandWithAddOnly(65))
        assertFalse(PlaybackStartPolicy.shouldResetPlaylistToExpand(1))
        assertFalse(PlaybackStartPolicy.shouldResetPlaylistToExpand(40))
    }

    @Test
    fun coldStart_isSingleTrackUnlessAlreadyInQueue() {
        assertEquals(1, PlaybackStartPolicy.coldStartQueueSize(false, 4000))
        assertEquals(12, PlaybackStartPolicy.coldStartQueueSize(true, 12))
        assertEquals(0, PlaybackStartPolicy.coldStartQueueSize(true, 0))
    }

    @Test
    fun spinnerTimeoutToken_isShort() {
        assertTrue(PlaybackStartPolicy.BUFFERING_SPINNER_MAX_MS <= 1_000L)
        assertTrue(PlaybackStartPolicy.BUFFERING_SPINNER_MAX_MS >= 400L)
    }
}
