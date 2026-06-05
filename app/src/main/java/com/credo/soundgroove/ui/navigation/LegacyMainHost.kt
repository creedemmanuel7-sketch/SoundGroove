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
    // Collect all ViewModel state here and pass down to MainScreen
    val controller by viewModel.mediaController.collectAsState()

    // If the MediaController isn't ready yet, show nothing (or a splash)
    if (controller != null) {
        // Legacy MainScreen still manages its own internal state for now.
        // It receives the controller and handles tabs / overlays internally.
        // This is the transitional bridge – as each screen is migrated,
        // remove it from MainScreen and drive it from the ViewModel here.
        com.credo.soundgroove.MainScreen(
            player = controller!!,
            onNavigateToPlaylist = onNavigateToPlaylist,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist
        )
    }
}
