package com.credo.soundgroove.auto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSearchMatcherTest {

    @Test
    fun matchesTitleIgnoreCase() {
        assertTrue(AutoSearchMatcher.matches("Bohemian Rhapsody", "Queen", "Opera", "bohem"))
    }

    @Test
    fun matchesArtist() {
        assertTrue(AutoSearchMatcher.matches("Song", "Queen", "Album", "quee"))
    }

    @Test
    fun matchesAlbum() {
        assertTrue(AutoSearchMatcher.matches("Song", "Artist", "A Night at the Opera", "opera"))
    }

    @Test
    fun rejectsEmptyQuery() {
        assertFalse(AutoSearchMatcher.matches("Title", "Artist", "Album", "   "))
    }

    @Test
    fun rejectsNonMatch() {
        assertFalse(AutoSearchMatcher.matches("Title", "Artist", "Album", "xyzzy"))
    }
}
