package com.credo.soundgroove.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMediaIdsTest {

    @Test
    fun playlist_roundTrip() {
        val id = AutoMediaIds.playlist(42L)
        assertEquals(42L, AutoMediaIds.parsePlaylistId(id))
    }

    @Test
    fun folder_roundTripEncodedPath() {
        val path = "/storage/Music/My Band/Live"
        val id = AutoMediaIds.folder(path)
        assertEquals(path, AutoMediaIds.parseFolderPath(id))
    }

    @Test
    fun song_roundTripUri() {
        val uri = "content://media/external/audio/media/9"
        val id = AutoMediaIds.song(uri)
        assertEquals(uri, AutoMediaIds.parseSongUri(id))
    }

    @Test
    fun roots_areBrowsable() {
        assertTrue(AutoMediaIds.isBrowsableRoot(AutoMediaIds.ROOT))
        assertTrue(AutoMediaIds.isBrowsableRoot(AutoMediaIds.PLAYLISTS))
        assertTrue(AutoMediaIds.isBrowsableRoot(AutoMediaIds.FOLDERS))
        assertTrue(AutoMediaIds.isBrowsableRoot(AutoMediaIds.FAVORITES))
        assertNull(AutoMediaIds.parsePlaylistId(AutoMediaIds.FAVORITES))
    }
}
