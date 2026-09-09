package com.credo.soundgroove.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionStoreTest {

    @Test
    fun parseQueueIds_emptyFallsBackToSong() {
        assertEquals(listOf(42L), PlaybackSessionStore.parseQueueIds(null, 42L))
        assertEquals(listOf(7L), PlaybackSessionStore.parseQueueIds("  ", 7L))
    }

    @Test
    fun parseQueueIds_filtersNegativesAndDuplicates() {
        // songId déjà dans la liste → pas de préfixe
        val ids = PlaybackSessionStore.parseQueueIds("1, -2, 3, 1, abc, 3", 1L)
        assertEquals(listOf(1L, 3L), ids)
    }

    @Test
    fun parseQueueIds_prependsMissingSongId() {
        val ids = PlaybackSessionStore.parseQueueIds("2, 3", 99L)
        assertEquals(listOf(99L, 2L, 3L), ids)
    }

    @Test
    fun normalizeQueueIds_ensuresCurrentSongPresent() {
        val ids = PlaybackSessionStore.normalizeQueueIds(listOf(2L, 3L), songId = 1L)
        assertEquals(listOf(1L, 2L, 3L), ids)
    }

    @Test
    fun normalizeQueueIds_capsLength() {
        val huge = (1L..500L).toList()
        val capped = PlaybackSessionStore.normalizeQueueIds(huge, songId = 1L)
        assertEquals(PlaybackSessionStore.MAX_QUEUE_IDS, capped.size)
        assertTrue(1L in capped)
    }
}
