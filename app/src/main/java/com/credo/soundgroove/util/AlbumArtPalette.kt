package com.credo.soundgroove.util

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult

@Composable
fun rememberAlbumArtAccentColor(albumArtUri: Uri?, defaultColor: Color): Color {
    val context = LocalContext.current
    var extractedColor by remember(albumArtUri) { mutableStateOf(defaultColor) }

    LaunchedEffect(albumArtUri, defaultColor) {
        if (albumArtUri == null) {
            extractedColor = defaultColor
            return@LaunchedEffect
        }
        try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(albumArtUri)
                .allowHardware(false)
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    Palette.from(drawable.bitmap).generate { palette ->
                        val swatch = palette?.vibrantSwatch
                            ?: palette?.dominantSwatch
                            ?: palette?.lightVibrantSwatch
                        swatch?.rgb?.let { colorInt ->
                            extractedColor = Color(colorInt)
                        } ?: run {
                            extractedColor = defaultColor
                        }
                    }
                } else {
                    extractedColor = defaultColor
                }
            } else {
                extractedColor = defaultColor
            }
        } catch (_: Exception) {
            extractedColor = defaultColor
        }
    }

    return extractedColor
}

/** Mélange la couleur de thème utilisateur avec l'accent extrait de la pochette. */
fun blendWithAlbumArt(themeAccent: Color, albumAccent: Color, weight: Float = 0.42f): Color {
    return lerp(themeAccent, albumAccent, weight.coerceIn(0f, 1f))
}

/**
 * Palette dédiée à un écran immersif plein écran (Paroles) : un fond sombre
 * "premium" dérivé de la pochette + des accents garantis lisibles pour le
 * texte actif/inactif, quelle que soit la pochette d'origine.
 */
data class LyricsPalette(
    val backgroundTop: Color,
    val backgroundCenter: Color,
    val backgroundBottom: Color,
    val activeText: Color,
    val inactiveText: Color
)

@Composable
fun rememberLyricsPalette(albumArtUri: Uri?, fallbackAccent: Color): LyricsPalette {
    val context = LocalContext.current
    val fallback = remember(fallbackAccent) { fallbackLyricsPalette(fallbackAccent) }
    var palette by remember(albumArtUri, fallbackAccent) { mutableStateOf(fallback) }

    LaunchedEffect(albumArtUri, fallbackAccent) {
        if (albumArtUri == null) {
            palette = fallback
            return@LaunchedEffect
        }
        try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(albumArtUri)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable as? BitmapDrawable
            if (bitmap == null) {
                palette = fallback
                return@LaunchedEffect
            }
            Palette.from(bitmap.bitmap).generate { generated ->
                palette = generated?.let { buildLyricsPalette(it, fallbackAccent) } ?: fallback
            }
        } catch (_: Exception) {
            palette = fallback
        }
    }

    return palette
}

private fun fallbackLyricsPalette(accent: Color): LyricsPalette {
    val base = darken(accent, 0.7f)
    return LyricsPalette(
        backgroundTop = darken(accent, 0.5f),
        backgroundCenter = base,
        backgroundBottom = darken(accent, 0.86f),
        activeText = ensureReadableOnDark(accent),
        inactiveText = Color.White.copy(alpha = 0.55f)
    )
}

private fun buildLyricsPalette(palette: Palette, fallbackAccent: Color): LyricsPalette {
    val bgSwatch = palette.darkMutedSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
    val accentSwatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch

    val bgBase = bgSwatch?.rgb?.let { Color(it) } ?: fallbackAccent
    val accentBase = accentSwatch?.rgb?.let { Color(it) } ?: fallbackAccent

    return LyricsPalette(
        backgroundTop = darken(bgBase, 0.35f),
        backgroundCenter = darken(bgBase, 0.62f),
        backgroundBottom = darken(bgBase, 0.85f),
        activeText = ensureReadableOnDark(accentBase),
        inactiveText = Color.White.copy(alpha = 0.5f)
    )
}

private fun darken(color: Color, weight: Float): Color = lerp(color, Color.Black, weight.coerceIn(0f, 1f))

/** Éclaircit une couleur d'accent trop sombre pour rester lisible sur un fond noir. */
private fun ensureReadableOnDark(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance < 0.4f) lerp(color, Color.White, 0.45f) else color
}
