package com.credo.soundgroove.ui.theme

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.credo.soundgroove.util.rememberAlbumArtAccentColor

/**
 * Résout l'accent UI effectif : manuel (swatches) ou extrait de la pochette en cours.
 */
@Composable
fun rememberEffectiveAccentColor(
    appTheme: AppTheme,
    manualAccent: AppAccent,
    albumCoverAccentEnabled: Boolean,
    albumArtUri: Uri?,
): Color {
    val manualColor = resolveAccentColor(appTheme, manualAccent)
    val albumRaw = rememberAlbumArtAccentColor(
        albumArtUri = if (albumCoverAccentEnabled) albumArtUri else null,
        defaultColor = manualColor,
    )
    val targetColor = if (albumCoverAccentEnabled && albumArtUri != null) {
        resolveDynamicAccent(appTheme, albumRaw)
    } else {
        manualColor
    }
    return animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(SgMotion.MediumMs),
        label = "effectiveAccent",
    ).value
}

/** Couleur brute extraite de la pochette (avant adaptation au thème), pour MaterialTheme dynamique. */
@Composable
fun rememberDynamicAccentBase(
    albumCoverAccentEnabled: Boolean,
    albumArtUri: Uri?,
    fallback: Color,
): Color? {
    if (!albumCoverAccentEnabled || albumArtUri == null) return null
    return rememberAlbumArtAccentColor(albumArtUri, fallback)
}
