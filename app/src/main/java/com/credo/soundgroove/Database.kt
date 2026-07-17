package com.credo.soundgroove

import android.content.Context
import android.net.Uri
import androidx.room.*
import com.credo.soundgroove.data.model.Song
import kotlinx.coroutines.flow.Flow

// ─── Entités Room ───

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val albumArtUri: String?
)

@Entity(tableName = "recently_played")
data class RecentlyPlayedEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val albumArtUri: String?,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: Long,
    val name: String
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val albumArtUri: String?,
    val position: Int
)

@Entity(tableName = "metadata_overrides")
data class MetadataOverrideEntity(
    @PrimaryKey val songId: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── DAOs ───

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    @Query("SELECT * FROM favorites")
    suspend fun getAllOnce(): List<FavoriteEntity>

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}

@Dao
interface RecentlyPlayedDao {
    @Query("SELECT * FROM recently_played ORDER BY playedAt DESC LIMIT 70")
    fun getAll(): Flow<List<RecentlyPlayedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: RecentlyPlayedEntity)

    @Query("DELETE FROM recently_played WHERE songId NOT IN (SELECT songId FROM recently_played ORDER BY playedAt DESC LIMIT 70)")
    suspend fun trimToLimit()

    @Query("DELETE FROM recently_played")
    suspend fun clearAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    suspend fun getAllPlaylistSongsOnce(): List<PlaylistSongEntity>

    @Query("DELETE FROM playlist_songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()
}

@Dao
interface MetadataOverrideDao {
    @Query("SELECT * FROM metadata_overrides")
    fun getAll(): Flow<List<MetadataOverrideEntity>>

    @Query("SELECT * FROM metadata_overrides WHERE songId = :songId LIMIT 1")
    suspend fun getBySongId(songId: Long): MetadataOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MetadataOverrideEntity)

    @Query("DELETE FROM metadata_overrides WHERE songId = :songId")
    suspend fun delete(songId: Long)
}

// ─── Database ───

@Database(
    entities = [
        FavoriteEntity::class,
        RecentlyPlayedEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        MetadataOverrideEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SoundGrooveDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun metadataOverrideDao(): MetadataOverrideDao

    companion object {
        @Volatile private var INSTANCE: SoundGrooveDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
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

        fun getInstance(context: Context): SoundGrooveDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SoundGrooveDatabase::class.java,
                    "soundgroove.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}

// ─── Extensions de conversion ───

fun FavoriteEntity.toSong() = Song(
    id = songId,
    title = title,
    artist = artist,
    uri = Uri.parse(uri),
    albumArtUri = albumArtUri?.let { Uri.parse(it) }
)

fun Song.toFavoriteEntity() = FavoriteEntity(
    songId = id,
    title = title,
    artist = artist,
    uri = uri.toString(),
    albumArtUri = albumArtUri?.toString()
)

fun RecentlyPlayedEntity.toSong() = Song(
    id = songId,
    title = title,
    artist = artist,
    uri = Uri.parse(uri),
    albumArtUri = albumArtUri?.let { Uri.parse(it) }
)

fun Song.toRecentlyPlayedEntity() = RecentlyPlayedEntity(
    songId = id,
    title = title,
    artist = artist,
    uri = uri.toString(),
    albumArtUri = albumArtUri?.toString()
)