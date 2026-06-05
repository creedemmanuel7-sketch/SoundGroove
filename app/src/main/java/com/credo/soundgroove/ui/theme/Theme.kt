package com.credo.soundgroove.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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
        content = content
    )
}