package com.credo.soundgroove.util

import com.credo.soundgroove.data.model.Song

/**
 * Métadonnées d'affichage assainies — évite `<unknown>`, filenames bruts et durées 0:00.
 * Centralisé pour Profil, Bibliothèque, listes et lecteur.
 */
object SongDisplay {

    private val UnknownMarkers = setOf(
        "",
        "unknown",
        "<unknown>",
        "inconnu",
        "unknown artist",
        "artiste inconnu",
        "titre inconnu",
        "album inconnu",
    )

    private val CodecSuffixRegex =
        Regex("""(?i)[_\-.]?(aac|mp3|flac|ogg|opus|wav|m4a|alac|aiff?)([_\-]?\d+)?$""")
    private val TrailingIdRegex = Regex("""[_\-]\d{3,}$""")
    private val ExtensionRegex =
        Regex("""\.(mp3|aac|flac|ogg|opus|wav|m4a|alac|aiff?|wma)$""", RegexOption.IGNORE_CASE)

    fun artist(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (isUnknownMarker(value)) return "Artiste inconnu"
        return value
    }

    fun album(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (isUnknownMarker(value)) return "Album inconnu"
        return value
    }

    fun title(raw: String?, filePathHint: String? = null): String {
        val value = raw?.trim().orEmpty()
        if (isUnknownMarker(value)) {
            return cleanFilenameTitle(fileNameFromPath(filePathHint))
                ?.takeIf { it.isNotBlank() && !isUnknownMarker(it) }
                ?: "Titre inconnu"
        }
        if (looksLikeFilename(value)) {
            return cleanFilenameTitle(value) ?: "Titre inconnu"
        }
        return value
    }

    /** Durée lisible, ou `null` si invalide (ne pas afficher `0:00` comme durée réelle). */
    fun formatDurationOrNull(durationMs: Long): String? {
        if (durationMs < 1000L) return null
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun formatDurationOrEmpty(durationMs: Long): String = formatDurationOrNull(durationMs).orEmpty()

    private fun isUnknownMarker(value: String): Boolean =
        value.lowercase() in UnknownMarkers

    private fun looksLikeFilename(value: String): Boolean {
        if (value.contains('/') || value.contains('\\')) return true
        if (ExtensionRegex.containsMatchIn(value)) return true
        val underscores = value.count { it == '_' }
        if (underscores >= 2 && !value.contains(' ')) return true
        if (CodecSuffixRegex.containsMatchIn(value) && underscores >= 1) return true
        if (TrailingIdRegex.containsMatchIn(value) && underscores >= 1 && !value.contains(' ')) return true
        return false
    }

    private fun cleanFilenameTitle(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var cleaned = raw.trim()
        cleaned = cleaned.substringAfterLast('/').substringAfterLast('\\')
        cleaned = ExtensionRegex.replace(cleaned, "")
        cleaned = CodecSuffixRegex.replace(cleaned, "")
        cleaned = TrailingIdRegex.replace(cleaned, "")
        cleaned = cleaned.replace('_', ' ').replace('-', ' ')
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        if (cleaned.isBlank() || isUnknownMarker(cleaned)) return null
        return cleaned.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
    }

    private fun fileNameFromPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
            .takeIf { it.isNotBlank() }
    }
}

fun Song.displayTitle(): String = SongDisplay.title(title, folderPath.ifBlank { null })

fun Song.displayArtist(): String = SongDisplay.artist(artist)

fun Song.displayAlbum(): String = SongDisplay.album(albumName)
