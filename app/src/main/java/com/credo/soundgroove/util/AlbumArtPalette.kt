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
import kotlin.math.pow

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

// ─────────────────────────────────────────────────────────────────────────────
// Règles de contraste "AGENT CONTRASTE PALETTE"
// ─────────────────────────────────────────────────────────────────────────────
// 1. Toute couleur de FOND dérivée d'une pochette est désaturée avant usage
//    (clampSaturation, plafond ~0.35) : une pochette très saturée ne doit
//    jamais produire un fond criard qui nuit à la lisibilité du texte.
// 2. La luminosité des fonds est bornée (clampLuminance) pour garantir un
//    fond "sombre premium" (Paroles) ou un fond clair contrôlé (Player en
//    thème clair), jamais une valeur extrême imprévisible selon la pochette.
// 3. Tout texte posé sur un fond dérivé passe par ensureContrast, qui vise un
//    ratio WCAG ~4.5:1 (AA texte normal) en poussant la couleur vers le blanc
//    ou le noir selon la luminance du fond — jamais l'inverse (on ne fonce/
//    éclaircit jamais le FOND pour "rattraper" le texte, seulement le texte).
// 4. Les écrans immersifs (Paroles) gardent un jeu de textes/verres FIXE
//    (blanc à opacité variable) indépendant du thème actif : leur fond est
//    toujours sombre par construction, donc leurs textes ne doivent jamais
//    suivre TextPrimary/TextSecondary (qui s'inversent en thème clair).
// ─────────────────────────────────────────────────────────────────────────────

/** Luminance relative WCAG (sRGB → linéaire), utilisée pour le calcul de contraste. */
private fun relativeLuminance(color: Color): Float {
    fun toLinear(c: Float): Float = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * toLinear(color.red) + 0.7152f * toLinear(color.green) + 0.0722f * toLinear(color.blue)
}

/** Ratio de contraste WCAG entre deux couleurs opaques (1:1 à 21:1). */
fun contrastRatio(foreground: Color, background: Color): Float {
    val l1 = relativeLuminance(foreground) + 0.05f
    val l2 = relativeLuminance(background) + 0.05f
    return if (l1 > l2) l1 / l2 else l2 / l1
}

/**
 * Pousse [foreground] vers le blanc ou le noir (selon la luminance de [background])
 * jusqu'à atteindre [minRatio] (défaut 4.5, seuil AA texte normal). Ne modifie jamais
 * le fond : seul le texte cède, par petits pas, pour rester le plus proche possible
 * de la teinte d'origine tout en devenant lisible.
 */
fun ensureContrast(foreground: Color, background: Color, minRatio: Float = 4.5f): Color {
    if (contrastRatio(foreground, background) >= minRatio) return foreground
    val target = if (relativeLuminance(background) < 0.5f) Color.White else Color.Black
    var result = foreground
    var step = 0f
    while (step < 1f && contrastRatio(result, background) < minRatio) {
        step += 0.05f
        result = lerp(foreground, target, step.coerceAtMost(1f))
    }
    return result
}

/** Conversion RGB → HSL (h, s, l dans [0f, 1f]), pour clamp saturation/luminance. */
private fun rgbToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f))
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } / 6f
    return floatArrayOf(h, s, l)
}

private fun hueToRgb(p: Float, q: Float, t0: Float): Float {
    var t = t0
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 1f / 2f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }
}

private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float): Color {
    if (s <= 0f) return Color(l, l, l, alpha)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val r = hueToRgb(p, q, h + 1f / 3f)
    val g = hueToRgb(p, q, h)
    val b = hueToRgb(p, q, h - 1f / 3f)
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), alpha)
}

/** Plafonne la saturation d'une couleur — plafond conseillé ~0.35 pour un FOND. */
fun clampSaturation(color: Color, maxSaturation: Float = 0.35f): Color {
    val (h, s, l) = rgbToHsl(color)
    if (s <= maxSaturation) return color
    return hslToColor(h, maxSaturation, l, color.alpha)
}

/** Borne la luminosité (L de HSL) d'une couleur entre [min] et [max]. */
fun clampLuminance(color: Color, min: Float, max: Float): Color {
    val (h, s, l) = rgbToHsl(color)
    val clamped = l.coerceIn(min, max)
    return hslToColor(h, s, clamped, color.alpha)
}

private fun darken(color: Color, weight: Float): Color = lerp(color, Color.Black, weight.coerceIn(0f, 1f))

/**
 * Palette dédiée à un écran immersif plein écran (Paroles) : un fond sombre
 * "premium" dérivé de la pochette + des accents garantis lisibles pour le
 * texte actif/inactif, quelle que soit la pochette d'origine.
 *
 * Le fond est TOUJOURS sombre par construction (immersion type "Now Playing"),
 * donc les couleurs de chrome ([primaryText], [secondaryText], [tertiaryText],
 * [glassSurface], [glassBorder]) sont FIXES (blanc à opacité variable) et ne
 * doivent jamais être remplacées par les tokens de thème (TextPrimary, etc.)
 * qui s'inversent en thème clair — cf. règle 4 en tête de fichier.
 */
data class LyricsPalette(
    val backgroundTop: Color,
    val backgroundCenter: Color,
    val backgroundBottom: Color,
    val activeText: Color,
    val inactiveText: Color,
    val primaryText: Color = LyricsChromePrimaryText,
    val secondaryText: Color = LyricsChromeSecondaryText,
    val tertiaryText: Color = LyricsChromeTertiaryText,
    val glassSurface: Color = LyricsChromeGlassSurface,
    val glassBorder: Color = LyricsChromeGlassBorder,
    val surfaceElevated: Color = LyricsChromeSurfaceElevated,
    val borderSubtle: Color = LyricsChromeBorderSubtle,
)

/** Tokens de chrome fixes de l'écran Paroles (cf. KDoc [LyricsPalette]). */
val LyricsChromePrimaryText = Color(0xFFF5F5F7)
val LyricsChromeSecondaryText = Color(0xFFC7C9CE)
val LyricsChromeTertiaryText = Color(0xFF9A9CA3)
val LyricsChromeGlassSurface = Color(0x14FFFFFF)
val LyricsChromeGlassBorder = Color(0x28FFFFFF)
val LyricsChromeSurfaceElevated = Color(0x1FFFFFFF)
val LyricsChromeBorderSubtle = Color(0x28FFFFFF)

private const val LyricsBackgroundMaxSaturation = 0.35f
private const val LyricsMinTextContrast = 4.5f

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
    val bgBase = clampSaturation(accent, LyricsBackgroundMaxSaturation)
    val backgroundCenter = darken(bgBase, 0.7f)
    val activeText = ensureContrast(accent, backgroundCenter, LyricsMinTextContrast)
    return LyricsPalette(
        backgroundTop = darken(bgBase, 0.5f),
        backgroundCenter = backgroundCenter,
        backgroundBottom = darken(bgBase, 0.86f),
        activeText = activeText,
        inactiveText = Color.White.copy(alpha = 0.55f)
    )
}

private fun buildLyricsPalette(palette: Palette, fallbackAccent: Color): LyricsPalette {
    val bgSwatch = palette.darkMutedSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
    val accentSwatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch

    val bgRaw = bgSwatch?.rgb?.let { Color(it) } ?: fallbackAccent
    val accentBase = accentSwatch?.rgb?.let { Color(it) } ?: fallbackAccent

    // Fond désaturé (plafond ~0.35) AVANT assombrissement : une pochette très
    // vive ne doit jamais produire un fond "premium" criard (règle 1).
    val bgBase = clampSaturation(bgRaw, LyricsBackgroundMaxSaturation)
    val backgroundCenter = darken(bgBase, 0.62f)

    // Le texte actif cède du terrain (ensureContrast) tant qu'il n'atteint pas
    // ~4.5:1 face au centre du fond — jamais l'inverse (règle 3).
    val activeText = ensureContrast(accentBase, backgroundCenter, LyricsMinTextContrast)

    return LyricsPalette(
        backgroundTop = darken(bgBase, 0.35f),
        backgroundCenter = backgroundCenter,
        backgroundBottom = darken(bgBase, 0.85f),
        activeText = activeText,
        inactiveText = Color.White.copy(alpha = 0.5f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Ambiance Player — même logique de dérivation que Paroles, mais qui doit
// rester lisible aussi bien en thème sombre qu'en thème clair (contrairement
// à Paroles, le Player garde le fond du THÈME actif ; seule une teinte
// "scrim" dérivée de la pochette est superposée, jamais un fond opaque sombre
// forcé — cf. problème utilisateur n°2).
// ─────────────────────────────────────────────────────────────────────────────

/** Dégradé de superposition (scrim) dérivé de la pochette pour PlayerScreen. */
data class PlayerAmbiencePalette(
    val scrimTop: Color,
    val scrimMid: Color,
    val scrimBottom: Color
)

private const val PlayerAmbienceMaxSaturation = 0.35f

@Composable
fun rememberPlayerAmbiencePalette(
    albumArtUri: Uri?,
    fallbackAccent: Color,
    isLightTheme: Boolean,
    themeBackground: Color
): PlayerAmbiencePalette {
    val context = LocalContext.current
    val fallback = remember(fallbackAccent, isLightTheme, themeBackground) {
        buildPlayerAmbience(fallbackAccent, isLightTheme, themeBackground)
    }
    var palette by remember(albumArtUri, fallbackAccent, isLightTheme, themeBackground) {
        mutableStateOf(fallback)
    }

    LaunchedEffect(albumArtUri, fallbackAccent, isLightTheme, themeBackground) {
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
                val swatch = generated?.dominantSwatch ?: generated?.mutedSwatch ?: generated?.vibrantSwatch
                val base = swatch?.rgb?.let { Color(it) } ?: fallbackAccent
                palette = buildPlayerAmbience(base, isLightTheme, themeBackground)
            }
        } catch (_: Exception) {
            palette = fallback
        }
    }

    return palette
}

private fun buildPlayerAmbience(rawTint: Color, isLightTheme: Boolean, themeBackground: Color): PlayerAmbiencePalette {
    val tint = clampSaturation(rawTint, PlayerAmbienceMaxSaturation)
    return if (isLightTheme) {
        // Thème clair : on ne fonce JAMAIS le fond (le texte reste sombre,
        // TextPrimary) — on éclaircit la teinte extraite et on la mélange
        // légèrement au fond du thème, avec un scrim qui redevient le fond pur
        // en bas d'écran pour ne pas washer les contrôles/texte du bas.
        val lightTint = clampLuminance(lerp(tint, Color.White, 0.55f), 0.8f, 0.95f)
        PlayerAmbiencePalette(
            scrimTop = lightTint.copy(alpha = 0.9f),
            scrimMid = lerp(themeBackground, tint, 0.16f).copy(alpha = 0.92f),
            scrimBottom = themeBackground.copy(alpha = 0.97f)
        )
    } else {
        val darkTint = darken(tint, 0.15f)
        val midTint = lerp(darken(tint, 0.55f), themeBackground, 0.35f)
        PlayerAmbiencePalette(
            scrimTop = darkTint.copy(alpha = 0.4f),
            scrimMid = midTint.copy(alpha = 0.88f),
            scrimBottom = themeBackground.copy(alpha = 0.95f)
        )
    }
}
