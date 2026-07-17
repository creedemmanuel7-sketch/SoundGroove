package com.credo.soundgroove.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Palette de marque (icône : note cyan #00D6F9 sur noir) ─────────────────

val BrandCyan = Color(0xFF00D6F9)
val BrandBlack = Color(0xFF000000)

// ── Thème Noir Absolu ───────────────────────────────────────────────────────

val AbsoluteBlackBg = Color(0xFF000000)
val AbsoluteBlackSurface = Color(0xFF0D0D0D)

// ── Thème Graphite ──────────────────────────────────────────────────────────

val GraphiteAbyss = Color(0xFF0A0A0C)
val GraphiteCard = Color(0xFF17181C)
val GraphiteMid = Color(0xFF3C4048)
val SilverAccent = Color(0xFFC7CDD6)
val IceAccent = Color(0xFFD7E3EA)
val SilverAccentSoft = Color(0xFFB9C0CA)

// ── Thème Clair Argent (fond blanc / argent) ────────────────────────────────

val ArgentClairBg = Color(0xFFF7F8FA)
val ArgentClairSurface = Color(0xFFFFFFFF)
val ArgentClairCard = Color(0xFFF0F2F5)
val SteelBlue = Color(0xFF2B6CB0)
val ArgentClairAccent = Color(0xFF00B8D9)

// ── Sémantique transversale (constantes fixes) ──────────────────────────────

val FavoritePink = Color(0xFFE0759B)
val ErrorRed = Color(0xFFE0554F)

/**
 * Tokens sémantiques dépendants du thème actif — fournis par [SoundGrooveTheme]
 * via [LocalSgSemanticColors]. Les accesseurs [TextPrimary], [CardSurface], etc.
 * lisent ces valeurs dans les @Composable.
 */
data class SgSemanticColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val cardSurface: Color,
    val surfaceElevated: Color,
    val surfaceOverlay: Color,
    val borderSubtle: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val glassSurfaceDark: Color,
    val glassHighlight: Color,
    val scrimOverlay: Color,
    val sheetDeep: Color,
    val heroGradientTop: Color,
    val heroGradientBottom: Color,
    val isLight: Boolean,
)

val LocalSgSemanticColors = staticCompositionLocalOf {
    SgSemanticColors(
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
}

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.textSecondary

val TextTertiary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.textTertiary

val CardSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.cardSurface

val SurfaceElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.surfaceElevated

val SurfaceOverlay: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.surfaceOverlay

val BorderSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.borderSubtle

val GlassSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.glassSurface

val GlassBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.glassBorder

val GlassSurfaceDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.glassSurfaceDark

val GlassHighlight: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.glassHighlight

val IsLightTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.isLight

val ScrimOverlay: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.scrimOverlay

val SheetDeep: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.sheetDeep

val HeroGradientTop: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.heroGradientTop

val HeroGradientBottom: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSgSemanticColors.current.heroGradientBottom

// Rétrocompatibilité — alias palette historique
val ChampagneGold = BrandCyan
val DarkSurface = GraphiteAbyss
