package com.credo.soundgroove.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations Room explicites (pas de destructive en production).
 * Exposees pour tests JVM / instrumented.
 */
object SoundGrooveMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS metadata_overrides (
                    songId INTEGER NOT NULL PRIMARY KEY,
                    title TEXT,
                    artist TEXT,
                    album TEXT,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE metadata_overrides ADD COLUMN coverArtUri TEXT")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE recently_played ADD COLUMN playCount INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    /**
     * v5 : FK CASCADE playlist -> playlist_songs, indexes perf, purge orphelines.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL(
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
            db.execSQL(
                """
                INSERT INTO playlist_songs_new
                    (playlistId, songId, title, artist, uri, albumArtUri, position)
                SELECT playlistId, songId, title, artist, uri, albumArtUri, position
                FROM playlist_songs
                WHERE playlistId IN (SELECT id FROM playlists)
                """.trimIndent()
            )
            db.execSQL("DROP TABLE playlist_songs")
            db.execSQL("ALTER TABLE playlist_songs_new RENAME TO playlist_songs")

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId ON playlist_songs (playlistId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs (songId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId_position ON playlist_songs (playlistId, position)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_songs_artist ON playlist_songs (artist)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_recently_played_playedAt ON recently_played (playedAt)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_recently_played_playCount ON recently_played (playCount)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_favorites_artist ON favorites (artist)"
            )
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    /**
     * v6 : indexes titre / uri (path) / artiste manquants pour recherche & volume 5k+.
     * Le catalogue MediaStore reste hors Room (dateAdded cote ContentResolver).
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_favorites_title ON favorites (title)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_favorites_uri ON favorites (uri)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_songs_title ON playlist_songs (title)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_songs_uri ON playlist_songs (uri)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_recently_played_title ON recently_played (title)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_recently_played_artist ON recently_played (artist)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_recently_played_uri ON recently_played (uri)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_metadata_overrides_updatedAt ON metadata_overrides (updatedAt)"
            )
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )
}
