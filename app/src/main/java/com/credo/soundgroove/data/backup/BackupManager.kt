package com.credo.soundgroove.data.backup

import android.content.Context
import android.net.Uri
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class BackupSnapshot(
    val theme: AppTheme?,
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

        val favorites = root.optJSONArray("favorites")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                parseSong(array.optJSONObject(index))
            }
        } ?: emptyList()

        val playlists = root.optJSONArray("playlists")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optLong("id", System.currentTimeMillis() + index)
                val name = obj.optString("name", "Playlist")
                val songs = obj.optJSONArray("songs")?.let { songsArray ->
                    (0 until songsArray.length()).mapNotNull { songIndex ->
                        parseSong(songsArray.optJSONObject(songIndex))
                    }
                } ?: emptyList()
                Playlist(id = id, name = name, songs = songs)
            }
        } ?: emptyList()

        return BackupSnapshot(theme = theme, favorites = favorites, playlists = playlists)
    }

    fun writeToUri(uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Impossible d'écrire le fichier de sauvegarde.")
    }

    fun readFromUri(uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
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
        if (uriStr.isBlank()) return null
        return Song(
            id = obj.optLong("id", 0L),
            title = obj.optString("title", "Inconnu"),
            artist = obj.optString("artist", "Inconnu"),
            uri = Uri.parse(uriStr),
            albumArtUri = obj.optString("albumArtUri", "").takeIf { it.isNotBlank() }?.let(Uri::parse),
            albumName = obj.optString("albumName", "Inconnu"),
            folderPath = obj.optString("folderPath", ""),
            duration = obj.optLong("duration", 0L),
            dateAdded = obj.optLong("dateAdded", 0L)
        )
    }

    companion object {
        const val BACKUP_VERSION = 1
        const val BACKUP_MIME = "application/json"
        const val BACKUP_FILENAME = "soundgroove_backup.json"
    }
}
