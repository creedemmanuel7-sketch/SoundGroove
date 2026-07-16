package com.credo.soundgroove.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Trois identités visuelles premium et universelles, inspirées des leaders du secteur
 * (noir profond, gris argenté, graphite) — volontairement sobres, sans violet/corail
 * générique. Chaque thème reste sombre pour garantir un contraste de texte fiable partout
 * dans l'app (voir Design.kt / TextPrimary) ; un vrai thème clair (fond blanc) nécessiterait
 * de rendre les tokens de texte dépendants du thème — un chantier distinct et volontairement
 * hors scope ici pour ne pas fragiliser le reste de l'UI.
 */
enum class AppTheme {
    NOIR_ABSOLU,
    ARGENT_CLAIR,
    GRAPHITE
}

private val NoirAbsoluColors = darkColorScheme(
    primary = ChampagneGold,
    secondary = ChampagneGold,
    background = AbsoluteBlackBg,
    surface = CardSurface,
    onPrimary = Color(0xFF1A1408),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

private val ArgentClairColors = darkColorScheme(
    primary = SteelBlue,
    secondary = SteelBlue,
    background = ArgentClairBg,
    surface = CardSurface,
    onPrimary = Color(0xFF08111C),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

private val GraphiteColors = darkColorScheme(
    primary = SilverAccent,
    secondary = IceAccent,
    background = GraphiteAbyss,
    surface = CardSurface,
    onPrimary = Color(0xFF15161A),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

fun accentColorForTheme(appTheme: AppTheme): Color = when (appTheme) {
    AppTheme.NOIR_ABSOLU -> ChampagneGold
    AppTheme.ARGENT_CLAIR -> SteelBlue
    AppTheme.GRAPHITE -> SilverAccent
}

fun themeBackgroundBrush(appTheme: AppTheme): Brush = when (appTheme) {
    AppTheme.NOIR_ABSOLU -> Brush.verticalGradient(
        listOf(Color(0xFF161208), Color(0xFF040404), Color(0xFF000000))
    )
    AppTheme.ARGENT_CLAIR -> Brush.verticalGradient(
        listOf(Color(0xFF454A53), Color(0xFF2E323A), Color(0xFF23262C))
    )
    AppTheme.GRAPHITE -> Brush.verticalGradient(
        listOf(Color(0xFF20222A), Color(0xFF0D0E10), Color(0xFF0A0A0C))
    )
}

fun themeSecondaryAccent(accentColor: Color): Color =
    accentColor.copy(alpha = 0.65f)

@Composable
fun SoundGrooveTheme(
    appTheme: AppTheme = AppTheme.NOIR_ABSOLU,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.NOIR_ABSOLU -> NoirAbsoluColors
        AppTheme.ARGENT_CLAIR -> ArgentClairColors
        AppTheme.GRAPHITE -> GraphiteColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
