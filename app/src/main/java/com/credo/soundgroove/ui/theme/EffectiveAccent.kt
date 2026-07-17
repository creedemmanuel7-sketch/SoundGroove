package com.credo.soundgroove.ui.theme

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.credo.soundgroove.util.AlbumArtRolePalette
import com.credo.soundgroove.util.buildRolePaletteFromCandidates
import com.credo.soundgroove.util.rememberAlbumArtAccentColor
import com.credo.soundgroove.util.rememberAlbumArtRolePalette

/**
 * Résout l'accent UI effectif : manuel (swatches) ou extrait de la pochette en cours.
 * Avec accent pochette, la couleur primaire est choisie parmi dominant/secondaire/tertiaire
 * selon le contraste sur le fond du thème.
 */
@Composable
fun rememberEffectiveAccentColor(
    appTheme: AppTheme,
    manualAccent: AppAccent,
    albumCoverAccentEnabled: Boolean,
    albumArtUri: Uri?,
): Color {
    val rolePalette = rememberEffectiveAccentPalette(
        appTheme = appTheme,
        manualAccent = manualAccent,
        albumCoverAccentEnabled = albumCoverAccentEnabled,
        albumArtUri = albumArtUri,
    )
    val reducedMotion = rememberSgReducedMotion()
    return animateColorAsState(
        targetValue = rolePalette.primary,
        animationSpec = if (reducedMotion) snap() else SgMotion.tweenMediumOf(),
        label = "effectiveAccent",
    ).value
}

/**
 * Palette structurée (primary / secondary / muted…) adaptée au fond réel du thème.
 */
@Composable
fun rememberEffectiveAccentPalette(
    appTheme: AppTheme,
    manualAccent: AppAccent,
    albumCoverAccentEnabled: Boolean,
    albumArtUri: Uri?,
): AlbumArtRolePalette {
    val manualColor = resolveAccentColor(appTheme, manualAccent)
    val surfaceBackground = themeBackgroundColor(appTheme)
    val isLight = appTheme == AppTheme.ARGENT_CLAIR
    val extracted = rememberAlbumArtRolePalette(
        albumArtUri = if (albumCoverAccentEnabled) albumArtUri else null,
        defaultColor = manualColor,
        surfaceBackground = surfaceBackground,
        isLightTheme = isLight,
    )
    return remember(albumCoverAccentEnabled, albumArtUri, manualColor, extracted, appTheme, surfaceBackground) {
        if (albumCoverAccentEnabled && albumArtUri != null) {
            val themeAwarePrimary = resolveDynamicAccent(appTheme, extracted.primary)
            extracted.copy(
                primary = themeAwarePrimary,
                secondary = resolveDynamicAccent(appTheme, extracted.secondary),
                tertiary = resolveDynamicAccent(appTheme, extracted.tertiary),
                muted = resolveDynamicAccent(appTheme, extracted.muted),
                // onSurface déjà calculé contre le fond thème dans rememberAlbumArtRolePalette
                onSurface = extracted.onSurface,
            )
        } else {
            buildRolePaletteFromCandidates(
                candidates = listOf(
                    manualAccent.primary,
                    manualAccent.soft,
                    manualAccent.deep,
                    manualAccent.muted,
                ),
                surfaceBackground = surfaceBackground,
                fallback = manualColor,
                isLightTheme = isLight,
            )
        }
    }
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

fun themeBackgroundColor(appTheme: AppTheme): Color = when (appTheme) {
    AppTheme.NOIR_ABSOLU -> AbsoluteBlackBg
    AppTheme.GRAPHITE -> GraphiteAbyss
    AppTheme.ARGENT_CLAIR -> ArgentClairBg
}
