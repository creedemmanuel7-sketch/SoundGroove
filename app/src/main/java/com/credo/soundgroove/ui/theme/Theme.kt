package com.credo.soundgroove.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SoundGrooveColors = darkColorScheme(
    primary = LightPurple,
    secondary = CyanAccent,
    background = DeepPurple,
    surface = CardSurface,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun SoundGrooveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoundGrooveColors,
        content = content
    )
}