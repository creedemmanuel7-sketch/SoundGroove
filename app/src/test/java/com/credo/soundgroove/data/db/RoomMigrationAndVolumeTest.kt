package com.credo.soundgroove.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Validation SQL des migrations + bench ~5000 lignes (JVM, sqlite-jdbc).
 * Évite Robolectric (conflits natives Rive / hang CI).
 * Les scripts exécutés correspondent à [SoundGrooveMigrations].
 */
class RoomMigrationAndVolumeTest {

    @Test
    fun migration_5_to_6_creates_title_uri_indexes() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            createV5Schema(conn)
            applyMigration56(conn)
            val indexes = indexNames(conn)
            assertTrue(indexes.contains("index_favorites_title"))
            assertTrue(indexes.contains("index_favorites_uri"))
            assertTrue(indexes.contains("index_playlist_songs_title"))
            assertTrue(indexes.contains("index_playlist_songs_uri"))
            assertTrue(indexes.contains("index_recently_played_title"))
            assertTrue(indexes.contains("index_recently_played_artist"))
            assertTrue(indexes.contains("index_recently_played_uri"))
            assertTrue(indexes.contains("index_metadata_overrides_updatedAt"))
        }
    }

    @Test
    fun migration_4_to_5_creates_cascade_indexes_and_drops_orphans() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            createV4Schema(conn)
            conn.createStatement().use { st ->
                st.executeUpdate("INSERT INTO playlists (id, name) VALUES (1, 'A')")
                st.executeUpdate(
                    "INSERT INTO playlist_songs (playlistId, songId, title, artist, uri, albumArtUri, position) " +
                        "VALUES (1, 10, 'T', 'Art', 'content://x/10', NULL, 0)"
                )
                st.executeUpdate(
                    "INSERT INTO playlist_songs (playlistId, songId, title, artist, uri, albumArtUri, position) " +
                        "VALUES (999, 11, 'Orphan', 'Art', 'content://x/11', NULL, 0)"
                )
            }
            applyMigration45(conn)
            val count = conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM playlist_songs").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
            assertEquals(1, count)
            val indexes = indexNames(conn)
            assertTrue(indexes.contains("index_playlist_songs_playlistId"))
            assertTrue(indexes.contains("index_favorites_artist"))
        }
    }

    @Test
    fun volume_5000_favorites_load_search_count_timings() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            createV6Favorites(conn)
            conn.autoCommit = false
            val insertStart = System.nanoTime()
            conn.prepareStatement(
                "INSERT INTO favorites (songId, title, artist, uri, albumArtUri) VALUES (?,?,?,?,NULL)"
            ).use { ps ->
                for (i in 1..5000) {
                    ps.setLong(1, i.toLong())
                    ps.setString(2, "Track $i")
                    ps.setString(3, "Artist ${i % 200}")
                    ps.setString(4, "content://media/external/audio/media/$i")
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
            val insertMs = (System.nanoTime() - insertStart) / 1_000_000.0

            val loadStart = System.nanoTime()
            val loaded = conn.createStatement().use { st ->
                st.executeQuery("SELECT * FROM favorites").use { rs ->
                    var n = 0
                    while (rs.next()) n++
                    n
                }
            }
            val loadMs = (System.nanoTime() - loadStart) / 1_000_000.0

            val searchStart = System.nanoTime()
            val hits = conn.prepareStatement(
                "SELECT * FROM favorites WHERE title LIKE ? OR artist LIKE ? OR uri LIKE ?"
            ).use { ps ->
                val q = "%Track 42%"
                ps.setString(1, q)
                ps.setString(2, q)
                ps.setString(3, q)
                ps.executeQuery().use { rs ->
                    var n = 0
                    while (rs.next()) n++
                    n
                }
            }
            val searchMs = (System.nanoTime() - searchStart) / 1_000_000.0

            val countStart = System.nanoTime()
            val count = conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM favorites").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
            val countMs = (System.nanoTime() - countStart) / 1_000_000.0

            println(
                "ROOM_BENCH_5000 insertMs=$insertMs loadMs=$loadMs searchMs=$searchMs " +
                    "countMs=$countMs hits=$hits count=$count loaded=$loaded"
            )

            assertEquals(5000, count)
            assertEquals(5000, loaded)
            assertTrue(hits > 0)
            assertTrue("load trop lent: ${loadMs}ms", loadMs < 5_000)
            assertTrue("search trop lent: ${searchMs}ms", searchMs < 2_000)
            assertTrue("count trop lent: ${countMs}ms", countMs < 1_000)
        }
    }

    @Test
    fun volume_5000_playlist_songs_search() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            createV6PlaylistSongs(conn)
            conn.createStatement().use { it.executeUpdate("INSERT INTO playlists (id, name) VALUES (1, 'Bench')") }
            conn.autoCommit = false
            val insertStart = System.nanoTime()
            conn.prepareStatement(
                "INSERT INTO playlist_songs (playlistId, songId, title, artist, uri, albumArtUri, position) " +
                    "VALUES (1,?,?,?,?,NULL,?)"
            ).use { ps ->
                for (i in 1..5000) {
                    ps.setLong(1, i.toLong())
                    ps.setString(2, "Song $i")
                    ps.setString(3, "Band ${i % 100}")
                    ps.setString(4, "content://media/external/audio/media/$i")
                    ps.setInt(5, i)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
            val insertMs = (System.nanoTime() - insertStart) / 1_000_000.0

            val loadStart = System.nanoTime()
            val loaded = conn.createStatement().use { st ->
                st.executeQuery("SELECT * FROM playlist_songs ORDER BY playlistId, position").use { rs ->
                    var n = 0
                    while (rs.next()) n++
                    n
                }
            }
            val loadMs = (System.nanoTime() - loadStart) / 1_000_000.0

            val searchStart = System.nanoTime()
            val hits = conn.prepareStatement(
                "SELECT * FROM playlist_songs WHERE title LIKE ? OR artist LIKE ? OR uri LIKE ?"
            ).use { ps ->
                val q = "%Song 99%"
                ps.setString(1, q)
                ps.setString(2, q)
                ps.setString(3, q)
                ps.executeQuery().use { rs ->
                    var n = 0
                    while (rs.next()) n++
                    n
                }
            }
            val searchMs = (System.nanoTime() - searchStart) / 1_000_000.0

            val countStart = System.nanoTime()
            val count = conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM playlist_songs").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
            val countMs = (System.nanoTime() - countStart) / 1_000_000.0

            println(
                "ROOM_BENCH_PLAYLIST_5000 insertMs=$insertMs loadMs=$loadMs " +
                    "searchMs=$searchMs countMs=$countMs hits=$hits count=$count"
            )

            assertEquals(5000, count)
            assertEquals(5000, loaded)
            assertTrue(hits > 0)
            assertTrue(loadMs < 5_000)
            assertTrue(searchMs < 2_000)
        }
    }

    private fun indexNames(conn: Connection): Set<String> {
        val names = mutableSetOf<String>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='index'").use { rs ->
                while (rs.next()) names += rs.getString(1)
            }
        }
        return names
    }

    private fun applyMigration56(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute("CREATE INDEX IF NOT EXISTS index_favorites_title ON favorites (title)")
            st.execute("CREATE INDEX IF NOT EXISTS index_favorites_uri ON favorites (uri)")
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_title ON playlist_songs (title)")
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_uri ON playlist_songs (uri)")
            st.execute("CREATE INDEX IF NOT EXISTS index_recently_played_title ON recently_played (title)")
            st.execute("CREATE INDEX IF NOT EXISTS index_recently_played_artist ON recently_played (artist)")
            st.execute("CREATE INDEX IF NOT EXISTS index_recently_played_uri ON recently_played (uri)")
            st.execute("CREATE INDEX IF NOT EXISTS index_metadata_overrides_updatedAt ON metadata_overrides (updatedAt)")
        }
    }

    private fun applyMigration45(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys=OFF")
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS playlist_songs_new (
                    playlistId INTEGER NOT NULL,
                    songId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    uri TEXT NOT NULL,
                    albumArtUri TEXT,
                    position INTEGER NOT NULL,
                    PRIMARY KEY(playlistId, songId),
                    FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            st.execute(
                """
                INSERT INTO playlist_songs_new
                    (playlistId, songId, title, artist, uri, albumArtUri, position)
                SELECT playlistId, songId, title, artist, uri, albumArtUri, position
                FROM playlist_songs
                WHERE playlistId IN (SELECT id FROM playlists)
                """.trimIndent()
            )
            st.execute("DROP TABLE playlist_songs")
            st.execute("ALTER TABLE playlist_songs_new RENAME TO playlist_songs")
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId ON playlist_songs (playlistId)")
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs (songId)")
            st.execute(
                "CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId_position ON playlist_songs (playlistId, position)"
            )
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_artist ON playlist_songs (artist)")
            st.execute("CREATE INDEX IF NOT EXISTS index_recently_played_playedAt ON recently_played (playedAt)")
            st.execute("CREATE INDEX IF NOT EXISTS index_recently_played_playCount ON recently_played (playCount)")
            st.execute("CREATE INDEX IF NOT EXISTS index_favorites_artist ON favorites (artist)")
            st.execute("PRAGMA foreign_keys=ON")
        }
    }

    private fun createV5Schema(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                "CREATE TABLE favorites (songId INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                    "artist TEXT NOT NULL, uri TEXT NOT NULL, albumArtUri TEXT)"
            )
            st.execute(
                "CREATE TABLE recently_played (songId INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                    "artist TEXT NOT NULL, uri TEXT NOT NULL, albumArtUri TEXT, playedAt INTEGER NOT NULL, " +
                    "playCount INTEGER NOT NULL)"
            )
            st.execute("CREATE TABLE playlists (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
            st.execute(
                "CREATE TABLE playlist_songs (playlistId INTEGER NOT NULL, songId INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, artist TEXT NOT NULL, uri TEXT NOT NULL, albumArtUri TEXT, " +
                    "position INTEGER NOT NULL, PRIMARY KEY(playlistId, songId), " +
                    "FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE)"
            )
            st.execute(
                "CREATE TABLE metadata_overrides (songId INTEGER NOT NULL PRIMARY KEY, title TEXT, " +
                    "artist TEXT, album TEXT, coverArtUri TEXT, updatedAt INTEGER NOT NULL)"
            )
            st.execute("CREATE INDEX IF NOT EXISTS index_favorites_artist ON favorites (artist)")
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId ON playlist_songs (playlistId)")
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs (songId)")
            st.execute(
                "CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId_position ON playlist_songs (playlistId, position)"
            )
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_artist ON playlist_songs (artist)")
            st.execute("CREATE INDEX IF NOT EXISTS index_recently_played_playedAt ON recently_played (playedAt)")
            st.execute("CREATE INDEX IF NOT EXISTS index_recently_played_playCount ON recently_played (playCount)")
        }
    }

    private fun createV4Schema(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                "CREATE TABLE favorites (songId INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                    "artist TEXT NOT NULL, uri TEXT NOT NULL, albumArtUri TEXT)"
            )
            st.execute(
                "CREATE TABLE recently_played (songId INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                    "artist TEXT NOT NULL, uri TEXT NOT NULL, albumArtUri TEXT, playedAt INTEGER NOT NULL, " +
                    "playCount INTEGER NOT NULL)"
            )
            st.execute("CREATE TABLE playlists (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
            st.execute(
                "CREATE TABLE playlist_songs (playlistId INTEGER NOT NULL, songId INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, artist TEXT NOT NULL, uri TEXT NOT NULL, albumArtUri TEXT, " +
                    "position INTEGER NOT NULL, PRIMARY KEY(playlistId, songId))"
            )
            st.execute(
                "CREATE TABLE metadata_overrides (songId INTEGER NOT NULL PRIMARY KEY, title TEXT, " +
                    "artist TEXT, album TEXT, coverArtUri TEXT, updatedAt INTEGER NOT NULL)"
            )
        }
    }

    private fun createV6Favorites(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                "CREATE TABLE favorites (songId INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                    "artist TEXT NOT NULL, uri TEXT NOT NULL, albumArtUri TEXT)"
            )
            st.execute("CREATE INDEX IF NOT EXISTS index_favorites_artist ON favorites (artist)")
            st.execute("CREATE INDEX IF NOT EXISTS index_favorites_title ON favorites (title)")
            st.execute("CREATE INDEX IF NOT EXISTS index_favorites_uri ON favorites (uri)")
        }
    }

    private fun createV6PlaylistSongs(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute("CREATE TABLE playlists (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
            st.execute(
                "CREATE TABLE playlist_songs (playlistId INTEGER NOT NULL, songId INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, artist TEXT NOT NULL, uri TEXT NOT NULL, albumArtUri TEXT, " +
                    "position INTEGER NOT NULL, PRIMARY KEY(playlistId, songId), " +
                    "FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE)"
            )
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId ON playlist_songs (playlistId)")
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs (songId)")
            st.execute(
                "CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId_position ON playlist_songs (playlistId, position)"
            )
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_artist ON playlist_songs (artist)")
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_title ON playlist_songs (title)")
            st.execute("CREATE INDEX IF NOT EXISTS index_playlist_songs_uri ON playlist_songs (uri)")
        }
    }
}