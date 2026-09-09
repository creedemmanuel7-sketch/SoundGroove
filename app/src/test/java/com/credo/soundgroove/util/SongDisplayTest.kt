package com.credo.soundgroove.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongDisplayTest {

    @Test
    fun formatDurationOrNull_rejectsSubSecond() {
        assertNull(SongDisplay.formatDurationOrNull(0L))
        assertNull(SongDisplay.formatDurationOrNull(999L))
    }

    @Test
    fun formatDurationOrNull_formatsMinutesSeconds() {
        assertEquals("3:05", SongDisplay.formatDurationOrNull(185_000L))
        assertEquals("0:45", SongDisplay.formatDurationOrNull(45_000L))
        assertEquals("61:00", SongDisplay.formatDurationOrNull(3_660_000L))
    }

    @Test
    fun formatDurationOrEmpty_blankWhenInvalid() {
        assertEquals("", SongDisplay.formatDurationOrEmpty(500L))
        assertEquals("1:01", SongDisplay.formatDurationOrEmpty(61_000L))
    }

    @Test
    fun title_unknownFallsBackToCleanFilename() {
        val title = SongDisplay.title("<unknown>", filePathHint = "/Music/phonk_drift_aac.mp3")
        assertTrue(title.contains("Phonk", ignoreCase = true) || title.contains("drift", ignoreCase = true))
    }

    @Test
    fun artist_unknownUsesCompoundTitle() {
        assertEquals("Night Rider", SongDisplay.artist("<unknown>", titleHint = "Night Rider - Drift"))
    }

    @Test
    fun album_unknownMarkerLocalized() {
        assertEquals("Album inconnu", SongDisplay.album(""))
        assertEquals("Neon Nights", SongDisplay.album("Neon Nights"))
    }
}
