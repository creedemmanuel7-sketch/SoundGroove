package com.credo.soundgroove.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class AppTheme {
    CLASSIC_DARK,
    ORIGINAL_PURPLE,
    CORAL_VIBRANT
}

private val OriginalPurpleColors = darkColorScheme(
    primary = LightPurple,
    secondary = CyanAccent,
    background = DeepPurple,
    surface = CardSurface,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

private val ClassicDarkColors = darkColorScheme(
    primary = ClassicAccent,
    secondary = ClassicAccent,
    background = ClassicBackground,
    surface = ClassicSurface,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

private val CoralVibrantColors = darkColorScheme(
    primary = CoralAccent,
    secondary = CoralAccent,
    background = CoralBackground,
    surface = CoralSurface,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

fun accentColorForTheme(appTheme: AppTheme): Color = when (appTheme) {
    AppTheme.CLASSIC_DARK -> ClassicAccent
    AppTheme.ORIGINAL_PURPLE -> LightPurple
    AppTheme.CORAL_VIBRANT -> CoralAccent
}

fun themeBackgroundBrush(appTheme: AppTheme): Brush = when (appTheme) {
    AppTheme.CLASSIC_DARK -> Brush.verticalGradient(
        listOf(Color(0xFF0A1810), Color(0xFF030303), Color(0xFF000000))
    )
    AppTheme.ORIGINAL_PURPLE -> Brush.verticalGradient(
        listOf(Color(0xFF1A0E30), Color(0xFF0C0616), Color(0xFF06030C))
    )
    AppTheme.CORAL_VIBRANT -> Brush.verticalGradient(
        listOf(Color(0xFF2A1014), Color(0xFF14080A), Color(0xFF0A0406))
    )
}

fun themeSecondaryAccent(accentColor: Color): Color =
    accentColor.copy(alpha = 0.65f)

@Composable
fun SoundGrooveTheme(
    appTheme: AppTheme = AppTheme.CLASSIC_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.CLASSIC_DARK -> ClassicDarkColors
        AppTheme.ORIGINAL_PURPLE -> OriginalPurpleColors
        AppTheme.CORAL_VIBRANT -> CoralVibrantColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}