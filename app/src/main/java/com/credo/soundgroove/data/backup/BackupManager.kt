package com.credo.soundgroove.data.backup

import android.content.Context
import android.net.Uri
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.theme.AppAccent
import com.credo.soundgroove.ui.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class BackupSnapshot(
    val theme: AppTheme?,
    val accent: AppAccent? = null,
    val favorites: List<Song>,
    val playlists: List<Playlist>
)

class BackupManager(private val context: Context) {

    fun serialize(snapshot: BackupSnapshot): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "SoundGroove")

        snapshot.theme?.let { root.put("theme", it.name) }
        snapshot.accent?.let { root.put("accent", it.id) }

        val favoritesArray = JSONArray()
        snapshot.favorites.forEach { song ->
            favoritesArray.put(songToJson(song))
        }
        root.put("favorites", favoritesArray)

        val playlistsArray = JSONArray()
        snapshot.playlists.forEach { playlist ->
            val playlistObj = JSONObject()
            playlistObj.put("id", playlist.id)
            playlistObj.put("name", playlist.name)
            val songsArray = JSONArray()
            playlist.songs.forEach { song ->
                songsArray.put(songToJson(song))
            }
            playlistObj.put("songs", songsArray)
            playlistsArray.put(playlistObj)
        }
        root.put("playlists", playlistsArray)

        return root.toString(2)
    }

    fun parse(json: String): BackupSnapshot {
        if (json.length > MAX_BACKUP_CHARS) {
            throw IllegalArgumentException("Fichier de sauvegarde trop volumineux.")
        }

        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        if (version > BACKUP_VERSION) {
            throw IllegalArgumentException("Version de sauvegarde non prise en charge.")
        }

        val theme = root.optString("theme", "")
            .takeIf { it.isNotBlank() }
            ?.let { name ->
                runCatching { AppTheme.valueOf(name) }.getOrNull()
            }

        val accent = root.optString("accent", "")
            .takeIf { it.isNotBlank() }
            ?.let { id -> AppAccent.fromId(id) }

        val favoritesArray = root.optJSONArray("favorites")
        if (favoritesArray != null && favoritesArray.length() > MAX_FAVORITES) {
            throw IllegalArgumentException("Trop de favoris dans la sauvegarde.")
        }
        val favorites = favoritesArray?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                parseSong(array.optJSONObject(index))
            }
        } ?: emptyList()

        val playlistsArray = root.optJSONArray("playlists")
        if (playlistsArray != null && playlistsArray.length() > MAX_PLAYLISTS) {
            throw IllegalArgumentException("Trop de playlists dans la sauvegarde.")
        }
        val playlists = playlistsArray?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optLong("id", System.currentTimeMillis() + index)
                val name = obj.optString("name", "Playlist").take(MAX_PLAYLIST_NAME_LENGTH)
                val songsArray = obj.optJSONArray("songs")
                if (songsArray != null && songsArray.length() > MAX_SONGS_PER_PLAYLIST) {
                    throw IllegalArgumentException("Playlist trop longue dans la sauvegarde.")
                }
                val songs = songsArray?.let { songsJson ->
                    (0 until songsJson.length()).mapNotNull { songIndex ->
                        parseSong(songsJson.optJSONObject(songIndex))
                    }
                } ?: emptyList()
                Playlist(id = id, name = name, songs = songs)
            }
        } ?: emptyList()

        return BackupSnapshot(theme = theme, accent = accent, favorites = favorites, playlists = playlists)
    }

    fun writeToUri(uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Impossible d'écrire le fichier de sauvegarde.")
    }

    fun readFromUri(uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val buffer = CharArray(READ_BUFFER_SIZE)
            val builder = StringBuilder()
            var totalChars = 0
            while (true) {
                val read = reader.read(buffer)
                if (read == -1) break
                totalChars += read
                if (totalChars > MAX_BACKUP_CHARS) {
                    throw IllegalArgumentException("Fichier de sauvegarde trop volumineux.")
                }
                builder.append(buffer, 0, read)
            }
            return builder.toString()
        } ?: throw IllegalStateException("Impossible de lire le fichier de sauvegarde.")
    }

    private fun songToJson(song: Song): JSONObject {
        val obj = JSONObject()
        obj.put("id", song.id)
        obj.put("title", song.title)
        obj.put("artist", song.artist)
        obj.put("uri", song.uri.toString())
        song.albumArtUri?.let { obj.put("albumArtUri", it.toString()) }
        if (song.albumName.isNotBlank()) obj.put("albumName", song.albumName)
        if (song.folderPath.isNotBlank()) obj.put("folderPath", song.folderPath)
        if (song.duration > 0L) obj.put("duration", song.duration)
        if (song.dateAdded > 0L) obj.put("dateAdded", song.dateAdded)
        return obj
    }

    private fun parseSong(obj: JSONObject?): Song? {
        if (obj == null) return null
        val uriStr = obj.optString("uri", "")
        val uri = parseAllowedUri(uriStr) ?: return null
        val albumArtUri = obj.optString("albumArtUri", "")
            .takeIf { it.isNotBlank() }
            ?.let { parseAllowedUri(it) }
        return Song(
            id = obj.optLong("id", 0L),
            title = obj.optString("title", "Inconnu").take(MAX_SONG_FIELD_LENGTH),
            artist = obj.optString("artist", "Inconnu").take(MAX_SONG_FIELD_LENGTH),
            uri = uri,
            albumArtUri = albumArtUri,
            albumName = obj.optString("albumName", "Inconnu").take(MAX_SONG_FIELD_LENGTH),
            folderPath = obj.optString("folderPath", "").take(MAX_SONG_FIELD_LENGTH),
            duration = obj.optLong("duration", 0L).coerceAtLeast(0L),
            dateAdded = obj.optLong("dateAdded", 0L).coerceAtLeast(0L)
        )
    }

    /** N'accepte que les URI content:// (MediaStore) — rejette file:// et autres schémas. */
    private fun parseAllowedUri(raw: String): Uri? {
        if (raw.isBlank() || raw.length > MAX_URI_LENGTH) return null
        val uri = Uri.parse(raw)
        if (uri.scheme != "content") return null
        if (uri.isOpaque) return null
        return uri
    }

    companion object {
        const val BACKUP_VERSION = 1
        const val BACKUP_MIME = "application/json"
        const val BACKUP_FILENAME = "soundgroove_backup.json"

        private const val MAX_BACKUP_CHARS = 5 * 1024 * 1024
        private const val MAX_FAVORITES = 10_000
        private const val MAX_PLAYLISTS = 500
        private const val MAX_SONGS_PER_PLAYLIST = 5_000
        private const val MAX_SONG_FIELD_LENGTH = 512
        private const val MAX_PLAYLIST_NAME_LENGTH = 256
        private const val MAX_URI_LENGTH = 2048
        private const val READ_BUFFER_SIZE = 8192
    }
}
