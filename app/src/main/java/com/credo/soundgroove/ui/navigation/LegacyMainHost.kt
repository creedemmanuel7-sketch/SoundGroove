package com.credo.soundgroove.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.credo.soundgroove.viewmodel.SoundGrooveViewModel

/**
 * LegacyMainHost bridges the new SoundGrooveViewModel to the
 * existing MainScreen composable. As screens are progressively
 * migrated to use ViewModel directly, this file shrinks.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LegacyMainHost(
    viewModel: SoundGrooveViewModel,
    accentColor: Color,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val controller by viewModel.mediaController.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()

    if (controller != null) {
        com.credo.soundgroove.MainScreen(
            player = controller!!,
            accentColor = accentColor,
            currentTheme = currentTheme,
            onThemeSelected = { viewModel.setTheme(it) },
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            onSetSleepTimer = { viewModel.setSleepTimer(it) },
            onSetSleepTimerEndOfTrack = { viewModel.setSleepTimerEndOfTrack() },
            onCancelSleepTimer = { viewModel.cancelSleepTimer() },
            onNavigateToPlaylist = onNavigateToPlaylist,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist
        )
    }
}
