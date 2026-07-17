package com.credo.soundgroove.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Trois identités visuelles distinctes :
 * - [NOIR_ABSOLU] : noir profond, accent cyan (identité icône)
 * - [GRAPHITE] : graphite mat, accent argent/platine
 * - [ARGENT_CLAIR] : thème clair (fond blanc, texte sombre)
 */
enum class AppTheme {
    NOIR_ABSOLU,
    ARGENT_CLAIR,
    GRAPHITE
}

private val NoirAbsoluSemantic = SgSemanticColors(
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF9A9CA3),
    textTertiary = Color(0xFF68696F),
    cardSurface = Color(0xFF121316),
    surfaceElevated = Color(0xFF191A1E),
    surfaceOverlay = Color(0xFF202227),
    borderSubtle = Color(0xFF272830),
    glassSurface = Color(0x14FFFFFF),
    glassBorder = Color(0x28FFFFFF),
    glassSurfaceDark = Color(0x0AFFFFFF),
    glassHighlight = Color(0x1FFFFFFF),
    scrimOverlay = Color(0x99000000),
    sheetDeep = Color(0xFF000000),
    heroGradientTop = Color(0xFF0A1520),
    heroGradientBottom = Color(0xFF000000),
    isLight = false,
)

private val GraphiteSemantic = SgSemanticColors(
    textPrimary = Color(0xFFF0F1F4),
    textSecondary = Color(0xFF9498A0),
    textTertiary = Color(0xFF63666E),
    cardSurface = Color(0xFF17181C),
    surfaceElevated = Color(0xFF1E1F24),
    surfaceOverlay = Color(0xFF25262C),
    borderSubtle = Color(0xFF2E3038),
    glassSurface = Color(0x12FFFFFF),
    glassBorder = Color(0x24FFFFFF),
    glassSurfaceDark = Color(0x08FFFFFF),
    glassHighlight = Color(0x18FFFFFF),
    scrimOverlay = Color(0x990A0A0C),
    sheetDeep = Color(0xFF0A0A0C),
    heroGradientTop = Color(0xFF25262C),
    heroGradientBottom = Color(0xFF0A0A0C),
    isLight = false,
)

private val ArgentClairSemantic = SgSemanticColors(
    textPrimary = Color(0xFF1A1D23),
    textSecondary = Color(0xFF5C6370),
    textTertiary = Color(0xFF8A919C),
    cardSurface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF3F5F8),
    surfaceOverlay = Color(0xFFE8ECF1),
    borderSubtle = Color(0xFFD8DEE6),
    glassSurface = Color(0x1A1A1D23),
    glassBorder = Color(0x331A1D23),
    glassSurfaceDark = Color(0x0D1A1D23),
    glassHighlight = Color(0x24FFFFFF),
    scrimOverlay = Color(0x661A1D23),
    sheetDeep = Color(0xFFEEF1F5),
    heroGradientTop = Color(0xFFE8ECF1),
    heroGradientBottom = Color(0xFFF7F8FA),
    isLight = true,
)

private val NoirAbsoluColors = darkColorScheme(
    primary = BrandCyan,
    onPrimary = Color(0xFF001820),
    primaryContainer = Color(0xFF003544),
    onPrimaryContainer = BrandCyan,
    secondary = BrandCyan,
    onSecondary = Color(0xFF001820),
    background = AbsoluteBlackBg,
    onBackground = NoirAbsoluSemantic.textPrimary,
    surface = NoirAbsoluSemantic.cardSurface,
    onSurface = NoirAbsoluSemantic.textPrimary,
    surfaceVariant = NoirAbsoluSemantic.surfaceElevated,
    onSurfaceVariant = NoirAbsoluSemantic.textSecondary,
    outline = NoirAbsoluSemantic.borderSubtle,
    error = ErrorRed,
    onError = Color.White,
)

private val GraphiteColors = darkColorScheme(
    primary = SilverAccent,
    onPrimary = Color(0xFF15161A),
    primaryContainer = Color(0xFF3C4048),
    onPrimaryContainer = IceAccent,
    secondary = IceAccent,
    onSecondary = Color(0xFF15161A),
    background = GraphiteAbyss,
    onBackground = GraphiteSemantic.textPrimary,
    surface = GraphiteSemantic.cardSurface,
    onSurface = GraphiteSemantic.textPrimary,
    surfaceVariant = GraphiteSemantic.surfaceElevated,
    onSurfaceVariant = GraphiteSemantic.textSecondary,
    outline = GraphiteSemantic.borderSubtle,
    error = ErrorRed,
    onError = Color.White,
)

private val ArgentClairColors = lightColorScheme(
    primary = ArgentClairAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF004D5A),
    secondary = SteelBlue,
    onSecondary = Color.White,
    tertiary = BrandCyan,
    onTertiary = Color(0xFF001820),
    background = ArgentClairBg,
    onBackground = ArgentClairSemantic.textPrimary,
    surface = ArgentClairSemantic.cardSurface,
    onSurface = ArgentClairSemantic.textPrimary,
    surfaceVariant = ArgentClairSemantic.surfaceElevated,
    onSurfaceVariant = ArgentClairSemantic.textSecondary,
    outline = ArgentClairSemantic.borderSubtle,
    error = ErrorRed,
    onError = Color.White,
)

fun semanticColorsForTheme(appTheme: AppTheme): SgSemanticColors = when (appTheme) {
    AppTheme.NOIR_ABSOLU -> NoirAbsoluSemantic
    AppTheme.ARGENT_CLAIR -> ArgentClairSemantic
    AppTheme.GRAPHITE -> GraphiteSemantic
}

fun accentColorForTheme(appTheme: AppTheme): Color = when (appTheme) {
    AppTheme.NOIR_ABSOLU -> BrandCyan
    AppTheme.ARGENT_CLAIR -> ArgentClairAccent
    AppTheme.GRAPHITE -> SilverAccent
}

fun themeBackgroundBrush(appTheme: AppTheme): Brush = when (appTheme) {
    AppTheme.NOIR_ABSOLU -> Brush.verticalGradient(
        listOf(Color(0xFF0A1014), Color(0xFF040404), Color(0xFF000000))
    )
    AppTheme.ARGENT_CLAIR -> Brush.verticalGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFF7F8FA), Color(0xFFEEF1F5))
    )
    AppTheme.GRAPHITE -> Brush.verticalGradient(
        listOf(Color(0xFF20222A), Color(0xFF0D0E10), Color(0xFF0A0A0C))
    )
}

@Composable
fun sgThemedBackgroundBrush(): Brush = Brush.verticalGradient(
    listOf(
        SurfaceOverlay,
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.background
    )
)

@Composable
fun sgSheetGradientBrush(): Brush = Brush.verticalGradient(
    listOf(SurfaceOverlay, MaterialTheme.colorScheme.background, SheetDeep)
)

@Composable
fun sgFullScreenGradientBrush(): Brush = Brush.verticalGradient(
    listOf(SurfaceOverlay, MaterialTheme.colorScheme.surface, SheetDeep)
)

@Composable
fun sgModalContentBrush(): Brush = Brush.verticalGradient(
    listOf(
        SurfaceOverlay.copy(alpha = 0.97f),
        MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        SheetDeep.copy(alpha = 0.97f),
    )
)

@Composable
fun sgHeroPlaceholderBrush(): Brush = Brush.verticalGradient(
    listOf(HeroGradientTop, HeroGradientBottom)
)

@Composable
fun sgHeroScrimBrush(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color.Transparent,
        0.5f to Color.Black.copy(alpha = if (IsLightTheme) 0.12f else 0.3f),
        1f to HeroGradientBottom,
    )
)

@Composable
fun sgHeroScrimBrushWithCover(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color.Black.copy(alpha = if (IsLightTheme) 0.25f else 0.4f),
        0.5f to Color.Black.copy(alpha = if (IsLightTheme) 0.12f else 0.2f),
        1f to HeroGradientBottom,
    )
)

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
    val semanticColors = semanticColorsForTheme(appTheme)

    CompositionLocalProvider(LocalSgSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
