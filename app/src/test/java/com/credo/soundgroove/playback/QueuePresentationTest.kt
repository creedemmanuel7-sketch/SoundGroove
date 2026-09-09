package com.credo.soundgroove.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePresentationTest {

    @Test
    fun stableKeys_useIdAndOccurrence() {
        val keys = QueuePresentation.stableKeys(longArrayOf(10L, 20L, 10L, 30L))
        assertEquals(listOf("10#0", "20#0", "10#1", "30#0"), keys)
        assertEquals("7#2", QueuePresentation.stableKey(7L, 2))
    }

    @Test
    fun remainingDuration_includesCurrentRemainderAndUpcoming() {
        val durations = longArrayOf(60_000L, 120_000L, 30_000L)
        assertEquals(130_000L, QueuePresentation.remainingDurationMs(durations, 0, 20_000L))
        assertEquals(30_000L, QueuePresentation.remainingDurationMs(durations, 1, 120_000L))
        assertEquals(10_000L, QueuePresentation.remainingDurationMs(durations, 2, 20_000L))
        assertEquals(0L, QueuePresentation.remainingDurationMs(longArrayOf(), 0, 0L))
    }

    @Test
    fun formatRemaining_andLabel() {
        assertEquals("0:00", QueuePresentation.formatRemaining(500L))
        assertEquals("2:05", QueuePresentation.formatRemaining(125_000L))
        assertEquals("1:01:01", QueuePresentation.formatRemaining(3_661_000L))
        val label = QueuePresentation.remainingLabel(125_000L, 3, 4)
        assertTrue(label.contains("2:05"))
        assertTrue(label.contains("4 titres"))
        assertEquals("0:00 · 1 titre", QueuePresentation.remainingLabel(0L, 0, 1))
    }

    @Test
    fun split_sectionsHistoryNowUpcoming() {
        val durations = longArrayOf(10_000L, 20_000L, 30_000L, 40_000L)
        val sections = QueuePresentation.split(4, 1, durations, 5_000L)
        assertEquals(0, sections.historyStart)
        assertEquals(1, sections.historyEndExclusive)
        assertEquals(1, sections.nowPlaying)
        assertEquals(2, sections.upcomingStart)
        assertEquals(4, sections.upcomingEndExclusive)
        assertEquals(1, sections.historyCount)
        assertEquals(2, sections.upcomingCount)
        assertTrue(sections.hasHistory())
        assertTrue(sections.hasUpcoming())
        assertEquals(15_000L + 30_000L + 40_000L, sections.remainingMs)
    }

    @Test
    fun clearUpcoming_keepsHistoryAndCurrent() {
        assertEquals(3, QueuePresentation.sizeAfterClearUpcoming(10, 2))
        assertEquals(1, QueuePresentation.sizeAfterClearUpcoming(1, 0))
        assertEquals(0, QueuePresentation.sizeAfterClearUpcoming(0, 0))
    }

    @Test
    fun adjustCurrentAfterMove_tracksNowPlaying() {
        assertEquals(2, QueuePresentation.adjustCurrentAfterMove(0, 2, 0))
        assertEquals(0, QueuePresentation.adjustCurrentAfterMove(0, 2, 1))
        assertEquals(2, QueuePresentation.adjustCurrentAfterMove(3, 1, 1))
        assertEquals(5, QueuePresentation.adjustCurrentAfterMove(1, 2, 5))
    }

    @Test
    fun emptyQueue_hasNoNowPlaying() {
        val empty = QueuePresentation.split(0, 0, longArrayOf(), 0L)
        assertFalse(empty.hasNowPlaying())
        assertFalse(empty.hasHistory())
        assertFalse(empty.hasUpcoming())
    }
}
