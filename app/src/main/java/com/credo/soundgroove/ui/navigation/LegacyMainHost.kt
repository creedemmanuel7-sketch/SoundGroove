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
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val sortedSongs by viewModel.sortedSongs.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val totalListeningSeconds by viewModel.totalListeningSeconds.collectAsState()
    val mainSelectedTab by viewModel.mainSelectedTab.collectAsState()
    val librarySelectedTab by viewModel.librarySelectedTab.collectAsState()
    val smartNotificationsEnabled by viewModel.smartNotificationsEnabled.collectAsState()
    val persistentMiniPlayerEnabled by viewModel.persistentMiniPlayerEnabled.collectAsState()
    val performanceModeEnabled by viewModel.performanceModeEnabled.collectAsState()

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
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onNavigateToPlaylist = onNavigateToPlaylist,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist,
            songs = songs,
            sortedSongs = sortedSongs,
            sortMode = sortMode,
            onSortModeChange = { viewModel.updateSortMode(it) },
            favoriteSongs = favoriteSongs,
            recentlyPlayed = recentlyPlayed,
            playlists = playlists,
            currentSong = currentSong,
            isPlaying = isPlaying,
            playbackPosition = playbackPosition,
            selectedTab = mainSelectedTab,
            onSelectedTabChange = { viewModel.updateMainSelectedTab(it) },
            librarySelectedTab = librarySelectedTab,
            onLibrarySelectedTabChange = { viewModel.updateLibrarySelectedTab(it) },
            onReloadMusic = { viewModel.reloadMusic() },
            listeningTimeLabel = viewModel.formatListeningTime(totalListeningSeconds),
            smartNotificationsEnabled = smartNotificationsEnabled,
            onSmartNotificationsChange = { viewModel.setSmartNotificationsEnabled(it) },
            persistentMiniPlayerEnabled = persistentMiniPlayerEnabled,
            onPersistentMiniPlayerChange = { viewModel.setPersistentMiniPlayerEnabled(it) },
            performanceModeEnabled = performanceModeEnabled,
            onPerformanceModeChange = { viewModel.setPerformanceModeEnabled(it) },
            onClearRecentlyPlayed = { viewModel.clearRecentlyPlayed() },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onCreatePlaylist = { viewModel.createPlaylist(it) },
            onPlaylistAddSong = { playlist, song ->
                viewModel.addSongToPlaylist(playlist.id, song, playlist.songs.size)
            },
            onPlaylistDelete = { viewModel.deletePlaylist(it.id) },
            onPlaylistRename = { playlist, newName -> viewModel.renamePlaylist(playlist.id, newName) },
            onRemoveSongFromPlaylist = { playlist, songId ->
                viewModel.removeSongFromPlaylist(playlist.id, songId)
            }
        )
    }
}
