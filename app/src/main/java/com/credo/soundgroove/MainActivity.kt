package com.credo.soundgroove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.credo.soundgroove.ui.navigation.AppNavigation
import com.credo.soundgroove.ui.screens.ThemeSelectionScreen
import com.credo.soundgroove.ui.theme.SoundGrooveTheme
import com.credo.soundgroove.ui.theme.rememberDynamicAccentBase
import com.credo.soundgroove.ui.theme.rememberEffectiveAccentColor
import com.credo.soundgroove.ui.theme.resolveAccentColor
import com.credo.soundgroove.util.CoilImageConfig
import com.credo.soundgroove.viewmodel.SoundGrooveViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoilImageConfig.install(this)
        enableEdgeToEdge()
        setContent {
            val viewModel: SoundGrooveViewModel = viewModel()
            val currentTheme by viewModel.currentTheme.collectAsState()
            val currentAccent by viewModel.currentAccent.collectAsState()
            val albumCoverAccentEnabled by viewModel.albumCoverAccentEnabled.collectAsState()
            val currentSong by viewModel.currentSong.collectAsState()
            val showThemeSelection by viewModel.showThemeSelection.collectAsState()

            val manualAccentColor = resolveAccentColor(currentTheme, currentAccent)
            val dynamicBase = rememberDynamicAccentBase(
                albumCoverAccentEnabled = albumCoverAccentEnabled,
                albumArtUri = currentSong?.albumArtUri,
                fallback = manualAccentColor,
            )
            val accentColor = rememberEffectiveAccentColor(
                appTheme = currentTheme,
                manualAccent = currentAccent,
                albumCoverAccentEnabled = albumCoverAccentEnabled,
                albumArtUri = currentSong?.albumArtUri,
            )

            SoundGrooveTheme(
                appTheme = currentTheme,
                accent = currentAccent,
                dynamicAccentBase = dynamicBase,
            ) {
                if (showThemeSelection) {
                    ThemeSelectionScreen(
                        onThemeSelected = { theme ->
                            viewModel.completeThemeSelection(theme)
                        }
                    )
                } else {
                    AppNavigation(
                        viewModel = viewModel,
                        accentColor = accentColor,
                    )
                }
            }
        }
    }
}
