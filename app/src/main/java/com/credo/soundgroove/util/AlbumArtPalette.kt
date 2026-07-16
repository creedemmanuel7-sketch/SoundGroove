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
