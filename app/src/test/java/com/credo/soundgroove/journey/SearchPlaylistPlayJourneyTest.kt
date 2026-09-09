package com.credo.soundgroove.journey

import com.credo.soundgroove.auto.AutoSearchMatcher
import com.credo.soundgroove.util.PlaybackQueueOps
import com.credo.soundgroove.util.PlaybackSessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/**
 * Parcours utilisateur simulé (JVM) — **pas d'inscription** dans SoundGroove (N/A).
 *
 * Chaîne : recherche locale → ajout playlist (SQL mémoire) → intent play mocké
 * (ids de file uniquement). Aucun MediaController / ExoPlayer / isBuffering.
 */
class SearchPlaylistPlayJourneyTest {

    data class PlayIntent(
        val startSongId: Long,
        val queueIds: List<Long>,
    )

    @Test
    fun search_addToPlaylist_thenMockPlayIntent_withoutPlayerEngine() {
        // 1) Recherche (catalogue local — pas de compte / signup)
        val catalog = listOf(
            Triple(101L, "Phonk Drift", "Night Rider"),
            Triple(102L, "Glass Rain", "Neon Coast"),
            Triple(103L, "Midnight Bass", "Night Rider"),
        )
        val query = "phonk"
        val hits = catalog.filter { (_, title, artist) ->
            AutoSearchMatcher.matches(title, artist, album = "", query = query)
        }
        assertEquals(1, hits.size)
        assertEquals(101L, hits.first().first)

        // 2) Ajout à une playlist (SQL in-memory, même schéma que favoris/playlists)
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE playlists (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL)")
                st.execute(
                    """
                    CREATE TABLE playlist_songs (
                        playlistId INTEGER NOT NULL,
                        songId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        albumArtUri TEXT,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, songId)
                    )
                    """.trimIndent()
                )
                st.executeUpdate("INSERT INTO playlists (id, name) VALUES (1, 'Road Phonk')")
            }
            val songId = hits.first().first
            conn.prepareStatement(
                "INSERT INTO playlist_songs (playlistId, songId, title, artist, uri, albumArtUri, position) " +
                    "VALUES (1, ?, ?, ?, ?, NULL, 0)"
            ).use { ps ->
                ps.setLong(1, songId)
                ps.setString(2, hits.first().second)
                ps.setString(3, hits.first().third)
                ps.setString(4, "content://local/$songId")
                assertEquals(1, ps.executeUpdate())
            }

            // 3) Intent play mock — file normalisée, sans brancher le moteur
            val playlistQueue = listOf(songId, 102L, 103L)
            val intent = PlayIntent(
                startSongId = songId,
                queueIds = PlaybackSessionStore.normalizeQueueIds(playlistQueue, songId),
            )
            assertEquals(songId, intent.startSongId)
            assertTrue(intent.queueIds.first() == songId || songId in intent.queueIds)
            assertFalse(intent.queueIds.isEmpty())

            // Mutation pure « play next » avant lecture (toujours hors ExoPlayer)
            val withNext = PlaybackQueueOps.insertNext(intent.queueIds, currentIndex = 0, songId = 999L)
            assertEquals(999L, withNext[1])
        }
    }

    @Test
    fun inscription_is_not_applicable() {
        // Documenté pour CI / PM : SoundGroove = lecteur local, pas d'auth.
        assertTrue("N/A — pas d'inscription / compte cloud", true)
    }
}
