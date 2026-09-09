package com.credo.soundgroove.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/**
 * DAO-like SQL (favoris + playlists) en mémoire — étend la couverture Room
 * sans Robolectric (même approche que [RoomMigrationAndVolumeTest]).
 */
class FavoritePlaylistDaoSqlTest {

    @Test
    fun favorites_toggle_insert_delete_and_isFavorite() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE favorites (
                        songId INTEGER PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        albumArtUri TEXT
                    )
                    """.trimIndent()
                )
            }
            conn.prepareStatement(
                "INSERT INTO favorites (songId, title, artist, uri, albumArtUri) VALUES (?,?,?,?,NULL)"
            ).use { ps ->
                ps.setLong(1, 42)
                ps.setString(2, "Track")
                ps.setString(3, "Artist")
                ps.setString(4, "content://x/42")
                ps.executeUpdate()
            }
            val exists = conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = ?)"
            ).use { ps ->
                ps.setLong(1, 42)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1) == 1
                }
            }
            assertTrue(exists)
            conn.prepareStatement("DELETE FROM favorites WHERE songId = ?").use { ps ->
                ps.setLong(1, 42)
                assertEquals(1, ps.executeUpdate())
            }
            val gone = conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = ?)"
            ).use { ps ->
                ps.setLong(1, 42)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1) == 1
                }
            }
            assertFalse(gone)
        }
    }

    @Test
    fun playlist_insert_songs_ordered_by_position() {
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
            }
            conn.createStatement().use { st ->
                st.executeUpdate("INSERT INTO playlists (id, name) VALUES (7, 'Road')")
            }
            conn.prepareStatement(
                "INSERT INTO playlist_songs (playlistId, songId, title, artist, uri, albumArtUri, position) " +
                    "VALUES (?,?,?,?,?,NULL,?)"
            ).use { ps ->
                listOf(
                    Triple(2L, "B", 1),
                    Triple(1L, "A", 0),
                    Triple(3L, "C", 2),
                ).forEach { (songId, title, pos) ->
                    ps.setLong(1, 7)
                    ps.setLong(2, songId)
                    ps.setString(3, title)
                    ps.setString(4, "Art")
                    ps.setString(5, "content://x/$songId")
                    ps.setInt(6, pos)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            val titles = conn.prepareStatement(
                "SELECT title FROM playlist_songs WHERE playlistId = 7 ORDER BY position"
            ).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(rs.getString(1))
                    }
                }
            }
            assertEquals(listOf("A", "B", "C"), titles)
        }
    }

    @Test
    fun playlist_add_song_then_count_and_reject_duplicate_primary_key() {
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
                st.executeUpdate("INSERT INTO playlists (id, name) VALUES (3, 'Favoris road')")
            }
            conn.prepareStatement(
                "INSERT INTO playlist_songs (playlistId, songId, title, artist, uri, albumArtUri, position) " +
                    "VALUES (3, 10, 'Alpha', 'Art', 'content://x/10', NULL, 0)"
            ).use { it.executeUpdate() }

            val count = conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = 3").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
            assertEquals(1, count)

            var duplicateRejected = false
            try {
                conn.prepareStatement(
                    "INSERT INTO playlist_songs (playlistId, songId, title, artist, uri, albumArtUri, position) " +
                        "VALUES (3, 10, 'Alpha', 'Art', 'content://x/10', NULL, 1)"
                ).use { it.executeUpdate() }
            } catch (_: Exception) {
                duplicateRejected = true
            }
            assertTrue(duplicateRejected)
        }
    }
}
