package com.credo.soundgroove.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Accents utilisateur — orthogonaux aux thèmes clair/sombre (Noir / Graphite / Argent).
 * Violet reste la valeur par défaut (#A855F7).
 */
enum class AppAccent(
    val id: String,
    val label: String,
    val primary: Color,
    val deep: Color,
    val muted: Color,
    val container: Color,
    val soft: Color,
) {
    VIOLET(
        id = "violet",
        label = "Violet",
        primary = Color(0xFFA855F7),
        deep = Color(0xFF7C3AED),
        muted = Color(0xFF9333EA),
        container = Color(0xFF2E1065),
        soft = Color(0xFFC084FC),
    ),
    TEAL(
        id = "teal",
        label = "Teal",
        primary = Color(0xFF2DD4BF),
        deep = Color(0xFF0D9488),
        muted = Color(0xFF14B8A6),
        container = Color(0xFF042F2E),
        soft = Color(0xFF5EEAD4),
    ),
    CORAL(
        id = "coral",
        label = "Corail",
        primary = Color(0xFFF97066),
        deep = Color(0xFFDC2626),
        muted = Color(0xFFE85D52),
        container = Color(0xFF450A0A),
        soft = Color(0xFFFDA4AF),
    ),
    AMBER(
        id = "amber",
        label = "Ambre",
        primary = Color(0xFFFBBF24),
        deep = Color(0xFFD97706),
        muted = Color(0xFFF59E0B),
        container = Color(0xFF451A03),
        soft = Color(0xFFFDE68A),
    ),
    EMERALD(
        id = "emerald",
        label = "Émeraude",
        primary = Color(0xFF34D399),
        deep = Color(0xFF059669),
        muted = Color(0xFF10B981),
        container = Color(0xFF022C22),
        soft = Color(0xFF6EE7B7),
    ),
    FROST(
        id = "frost",
        label = "Givre",
        primary = Color(0xFFA8B4C4),
        deep = Color(0xFF64748B),
        muted = Color(0xFF94A3B8),
        container = Color(0xFF1E293B),
        soft = Color(0xFFCBD5E1),
    );

    companion object {
        fun fromId(id: String?): AppAccent =
            entries.firstOrNull { it.id == id } ?: VIOLET
    }
}

/** Couleur d'accent effective selon le thème actif (tonalité WCAG sur clair, atténuée sur Graphite). */
fun resolveAccentColor(appTheme: AppTheme, accent: AppAccent): Color = when (appTheme) {
    AppTheme.NOIR_ABSOLU -> accent.primary
    AppTheme.ARGENT_CLAIR -> accent.deep
    AppTheme.GRAPHITE -> accent.muted
}

/** Pastille de prévisualisation du thème (surface, pas accent). */
fun themePreviewColor(appTheme: AppTheme): Color = when (appTheme) {
    AppTheme.NOIR_ABSOLU -> Color(0xFF1A1A1E)
    AppTheme.ARGENT_CLAIR -> Color(0xFFF0F2F5)
    AppTheme.GRAPHITE -> Color(0xFF25262C)
}

/** Variantes dérivées d'une couleur extraite (pochette). */
data class DynamicAccentVariants(
    val primary: Color,
    val deep: Color,
    val muted: Color,
    val container: Color,
    val soft: Color,
)

private fun darkenColor(color: Color, weight: Float): Color =
    lerp(color, Color.Black, weight.coerceIn(0f, 1f))

private fun clampLuminance(color: Color, min: Float, max: Float): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxC = maxOf(r, g, b)
    val minC = minOf(r, g, b)
    val l = (maxC + minC) / 2f
    if (l <= min || l >= max) {
        val target = l.coerceIn(min, max)
        if (maxC == minC) return Color(target, target, target, color.alpha)
        val scale = target / l.coerceAtLeast(0.001f)
        return Color(
            (r * scale).coerceIn(0f, 1f),
            (g * scale).coerceIn(0f, 1f),
            (b * scale).coerceIn(0f, 1f),
            color.alpha
        )
    }
    return color
}

fun deriveAccentVariants(base: Color): DynamicAccentVariants = DynamicAccentVariants(
    primary = base,
    deep = clampLuminance(base, 0f, 0.42f),
    muted = lerp(base, Color(0xFF9498A0), 0.18f),
    container = darkenColor(base, 0.72f),
    soft = lerp(base, Color.White, 0.38f),
)

/** Accent effectif issu de la palette pochette, adapté au thème actif. */
fun resolveDynamicAccent(appTheme: AppTheme, albumColor: Color): Color {
    val variants = deriveAccentVariants(albumColor)
    return when (appTheme) {
        AppTheme.NOIR_ABSOLU -> variants.primary
        AppTheme.ARGENT_CLAIR -> variants.deep
        AppTheme.GRAPHITE -> variants.muted
    }
}
