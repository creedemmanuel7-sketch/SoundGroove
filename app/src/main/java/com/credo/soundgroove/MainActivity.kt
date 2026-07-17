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
import com.credo.soundgroove.ui.theme.accentColorForTheme
import com.credo.soundgroove.viewmodel.SoundGrooveViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SoundGrooveViewModel = viewModel()
            val currentTheme by viewModel.currentTheme.collectAsState()
            val showThemeSelection by viewModel.showThemeSelection.collectAsState()

            SoundGrooveTheme(appTheme = currentTheme) {
                if (showThemeSelection) {
                    ThemeSelectionScreen(
                        onThemeSelected = { theme ->
                            viewModel.completeThemeSelection(theme)
                        }
                    )
                } else {
                    val accentColor = accentColorForTheme(currentTheme)
                    AppNavigation(
                        viewModel = viewModel,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}
