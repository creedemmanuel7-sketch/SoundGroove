package com.credo.soundgroove

import android.content.Context
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.net.Uri
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.credo.soundgroove.BuildConfig
import com.credo.soundgroove.data.db.SoundGrooveMigrations
import com.credo.soundgroove.data.model.Song
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicReference

// ─── Entités Room ───
// Les morceaux MediaStore ne sont PAS stockés ici : favoris / playlists / historique
// dénormalisent titre/artiste/uri. Intégrité « song → refs » = purge applicative
// (voir DatabaseRepository.purgeOrphanSongReferences), pas de FK vers une table songs.

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["artist"]),
        Index(value = ["title"]),
        Index(value = ["uri"]),
    ]
)
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val albumArtUri: String?
)

@Entity(
    tableName = "recently_played",
    indices = [
        Index(value = ["playedAt"]),
        Index(value = ["playCount"]),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["uri"]),
    ]
)
data class RecentlyPlayedEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val albumArtUri: String?,
    val playedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: Long,
    val name: String
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["songId"]),
        Index(value = ["playlistId", "position"]),
        Index(value = ["artist"]),
        Index(value = ["title"]),
        Index(value = ["uri"]),
    ]
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

@Entity(
    tableName = "metadata_overrides",
    indices = [Index(value = ["updatedAt"])]
)
data class MetadataOverrideEntity(
    @PrimaryKey val songId: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val coverArtUri: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── DAOs ───

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("DELETE FROM favorites WHERE songId IN (:songIds)")
    suspend fun deleteByIds(songIds: List<Long>)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE uri = :uri)")
    suspend fun isFavoriteByUri(uri: String): Boolean

    @Query("SELECT * FROM favorites WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): FavoriteEntity?

    @Query("SELECT * FROM favorites")
    suspend fun getAllOnce(): List<FavoriteEntity>

    @Query("SELECT songId FROM favorites")
    suspend fun getAllSongIds(): List<Long>

    @Query("DELETE FROM favorites")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Query(
        """
        SELECT * FROM favorites
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR uri LIKE '%' || :query || '%'
        """
    )
    suspend fun search(query: String): List<FavoriteEntity>
}

@Dao
interface RecentlyPlayedDao {
    @Query("SELECT * FROM recently_played ORDER BY playedAt DESC LIMIT 70")
    fun getAll(): Flow<List<RecentlyPlayedEntity>>

    @Query("SELECT * FROM recently_played ORDER BY playCount DESC, playedAt DESC LIMIT 50")
    fun getOftenPlayed(): Flow<List<RecentlyPlayedEntity>>

    @Query("SELECT * FROM recently_played WHERE songId = :songId LIMIT 1")
    suspend fun getBySongId(songId: Long): RecentlyPlayedEntity?

    @Query("SELECT * FROM recently_played WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): RecentlyPlayedEntity?

    @Query("SELECT * FROM recently_played ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getRecentOnce(limit: Int): List<RecentlyPlayedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: RecentlyPlayedEntity)

    @Query("DELETE FROM recently_played WHERE songId NOT IN (SELECT songId FROM recently_played ORDER BY playedAt DESC LIMIT 70)")
    suspend fun trimToLimit()

    @Query("SELECT songId FROM recently_played")
    suspend fun getAllSongIds(): List<Long>

    @Query("DELETE FROM recently_played WHERE songId IN (:songIds)")
    suspend fun deleteByIds(songIds: List<Long>)

    @Query("DELETE FROM recently_played")
    suspend fun clearAll()

    /** Lecture + upsert + trim atomiques. */
    @Transaction
    suspend fun upsertAndTrim(song: RecentlyPlayedEntity) {
        val existing = getBySongId(song.songId)
        val entity = if (existing != null) {
            song.copy(
                playedAt = System.currentTimeMillis(),
                playCount = existing.playCount + 1
            )
        } else {
            song
        }
        insert(entity)
        trimToLimit()
    }
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    /** Une seule requête pour toutes les entrées — évite le N+1 Flow par playlist. */
    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    fun getAllPlaylistSongs(): Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongIdsForPlaylist(playlistId: Long): List<Long>

    @Query("SELECT songId FROM playlist_songs")
    suspend fun getAllSongIds(): List<Long>

    @Query("DELETE FROM playlist_songs WHERE songId IN (:songIds)")
    suspend fun removeSongsByIds(songIds: List<Long>)

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    suspend fun getAllPlaylistSongsOnce(): List<PlaylistSongEntity>

    @Query("SELECT COUNT(*) FROM playlist_songs")
    suspend fun countSongs(): Int

    @Query(
        """
        SELECT * FROM playlist_songs
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR uri LIKE '%' || :query || '%'
        ORDER BY playlistId, position
        """
    )
    suspend fun searchSongs(query: String): List<PlaylistSongEntity>

    @Query("DELETE FROM playlist_songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()

    /** CASCADE FK playlist_songs → playlists : une seule DELETE suffit. */
    @Transaction
    suspend fun deletePlaylistWithSongs(playlistId: Long) {
        deletePlaylist(playlistId)
    }

    /** Insert playlist + morceaux dans une seule transaction. */
    @Transaction
    suspend fun insertPlaylistWithSongs(
        playlist: PlaylistEntity,
        songs: List<PlaylistSongEntity>
    ) {
        insertPlaylist(playlist)
        if (songs.isNotEmpty()) {
            insertSongs(songs)
        }
    }
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

    @Query("SELECT songId FROM metadata_overrides")
    suspend fun getAllSongIds(): List<Long>

    @Query("DELETE FROM metadata_overrides WHERE songId IN (:songIds)")
    suspend fun deleteByIds(songIds: List<Long>)
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
    version = 6,
    exportSchema = false
)
abstract class SoundGrooveDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun metadataOverrideDao(): MetadataOverrideDao

    companion object {
        private const val TAG = "SoundGrooveDb"
        const val DB_NAME = "soundgroove.db"

        @Volatile
        private var INSTANCE: SoundGrooveDatabase? = null

        private val openWarning = AtomicReference<String?>(null)

        /** Message one-shot après récupération (corruption / disque plein). */
        fun consumeOpenWarning(): String? = openWarning.getAndSet(null)

        fun getInstance(context: Context): SoundGrooveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: openWithRecovery(context.applicationContext).also { INSTANCE = it }
            }
        }

        /** Remet le singleton (tests). */
        fun clearInstanceForTests() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun openWithRecovery(context: Context): SoundGrooveDatabase {
            return try {
                buildDatabase(context).also { probeOpen(it) }
            } catch (t: Throwable) {
                if (!isRecoverableDbFailure(t)) throw t
                Log.e(TAG, "Ouverture Room échouée — recréation de la base", t)
                runCatching { context.deleteDatabase(DB_NAME) }
                openWarning.set(
                    when (t) {
                        is SQLiteFullException ->
                            "Stockage insuffisant : bibliothèque locale réinitialisée. Libérez de l’espace puis réimportez une sauvegarde."
                        is SQLiteDatabaseCorruptException ->
                            "Base locale corrompue : données Room réinitialisées. Restaurez une sauvegarde si besoin."
                        else ->
                            "Impossible d’ouvrir la base locale : données réinitialisées. Restaurez une sauvegarde si besoin."
                    }
                )
                buildDatabase(context).also { probeOpen(it) }
            }
        }

        private fun buildDatabase(context: Context): SoundGrooveDatabase {
            val builder = Room.databaseBuilder(
                context,
                SoundGrooveDatabase::class.java,
                DB_NAME
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(*SoundGrooveMigrations.ALL)
            // Destructive uniquement en debug si un chemin de migration manque (dev).
            if (BuildConfig.DEBUG) {
                @Suppress("DEPRECATION")
                builder.fallbackToDestructiveMigration()
            }
            return builder.build()
        }

        private fun probeOpen(db: SoundGrooveDatabase) {
            // Force l’ouverture pour faire remonter corruption / disque plein au démarrage.
            db.openHelper.readableDatabase
        }

        fun isRecoverableDbFailure(t: Throwable): Boolean {
            var current: Throwable? = t
            while (current != null) {
                when (current) {
                    is SQLiteDatabaseCorruptException,
                    is SQLiteCantOpenDatabaseException,
                    is SQLiteDiskIOException,
                    is SQLiteFullException -> return true
                    else -> {
                        // Message heuristics (SQLiteException stubs JVM + wrappers).
                        if (current is SQLiteException || looksLikeSqliteFailureMessage(current.message)) {
                            if (looksLikeSqliteFailureMessage(current.message)) return true
                        }
                    }
                }
                current = current.cause
            }
            return false
        }

        fun looksLikeSqliteFailureMessage(message: String?): Boolean {
            val msg = message.orEmpty().lowercase()
            return msg.contains("corrupt") ||
                msg.contains("malformed") ||
                msg.contains("disk is full") ||
                msg.contains("database or disk is full") ||
                msg.contains("unable to open") ||
                msg.contains("disk i/o") ||
                msg.contains("sqlite_full") ||
                msg.contains("sqlite_corrupt")
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