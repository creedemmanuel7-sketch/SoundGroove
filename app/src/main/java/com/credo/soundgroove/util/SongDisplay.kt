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

    /** "Artiste - Titre", "Artiste – Titre", "Artiste — Titre". */
    private val ArtistFromTitleRegex =
        Regex("""^\s*(.+?)\s*[-–—]\s+(.+?)\s*$""")

    fun artist(raw: String?, titleHint: String? = null, pathHint: String? = null): String {
        val value = raw?.trim().orEmpty()
        if (!isUnknownMarker(value)) return value
        parseArtistFromCompoundTitle(titleHint)?.let { return it }
        val fromPath = cleanFilenameTitle(fileNameFromPath(pathHint))
        parseArtistFromCompoundTitle(fromPath)?.let { return it }
        return "Artiste inconnu"
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

    /** Initiale pour fallback pochette (titre / artiste assaini). */
    fun coverInitial(titleRaw: String?, artistRaw: String?, pathHint: String? = null): String {
        val displayTitle = title(titleRaw, pathHint)
        val displayArtist = artist(artistRaw, titleRaw, pathHint)
        val source = when {
            !isUnknownMarker(displayArtist) && displayArtist != "Artiste inconnu" -> displayArtist
            displayTitle.isNotBlank() && displayTitle != "Titre inconnu" -> displayTitle
            else -> "?"
        }
        val ch = source.firstOrNull { it.isLetterOrDigit() } ?: return "?"
        return ch.uppercaseChar().toString()
    }

    private fun isUnknownMarker(value: String): Boolean =
        value.lowercase() in UnknownMarkers

    private fun parseArtistFromCompoundTitle(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val match = ArtistFromTitleRegex.matchEntire(raw.trim()) ?: return null
        val candidate = match.groupValues[1].trim()
        if (candidate.length < 2 || isUnknownMarker(candidate)) return null
        // Évite de prendre un titre trop long comme "artiste" (ex. phrases entières).
        if (candidate.length > 48) return null
        if (candidate.count { it == ' ' } > 4) return null
        return candidate
    }

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

fun Song.displayArtist(): String = SongDisplay.artist(artist, title, folderPath.ifBlank { null })

fun Song.displayAlbum(): String = SongDisplay.album(albumName)

fun Song.coverInitial(): String = SongDisplay.coverInitial(title, artist, folderPath.ifBlank { null })
