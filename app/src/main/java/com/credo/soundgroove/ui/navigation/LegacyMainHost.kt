package com.credo.soundgroove.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.credo.soundgroove.data.backup.BackupManager
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
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val controller by viewModel.mediaController.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val playbackPitch by viewModel.playbackPitch.collectAsState()
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsState()
    val crossfadeDurationMs by viewModel.crossfadeDurationMs.collectAsState()
    val metadataEditMessage by viewModel.metadataEditMessage.collectAsState()
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
    val listeningStats by viewModel.listeningStats.collectAsState()
    val mainSelectedTab by viewModel.mainSelectedTab.collectAsState()
    val librarySelectedTab by viewModel.librarySelectedTab.collectAsState()
    val smartNotificationsEnabled by viewModel.smartNotificationsEnabled.collectAsState()
    val persistentMiniPlayerEnabled by viewModel.persistentMiniPlayerEnabled.collectAsState()
    val performanceModeEnabled by viewModel.performanceModeEnabled.collectAsState()
    val hiddenFolders by viewModel.hiddenFolders.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(backupMessage) {
        backupMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearBackupMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupManager.BACKUP_MIME)
    ) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBackup(it) }
    }

    val activeController = controller
    if (activeController != null) {
        Box {
            com.credo.soundgroove.ui.screens.MainScreen(
            player = activeController,
            accentColor = accentColor,
            currentTheme = currentTheme,
            onThemeSelected = { viewModel.setTheme(it) },
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            onSetSleepTimer = { viewModel.setSleepTimer(it) },
            onSetSleepTimerEndOfTrack = { viewModel.setSleepTimerEndOfTrack() },
            onCancelSleepTimer = { viewModel.cancelSleepTimer() },
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            onPlaybackSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onPlaybackPitchChange = { viewModel.setPlaybackPitch(it) },
            gaplessEnabled = gaplessEnabled,
            onGaplessChange = { viewModel.setGaplessEnabled(it) },
            crossfadeDurationMs = crossfadeDurationMs,
            onCrossfadeDurationChange = { viewModel.setCrossfadeDurationMs(it) },
            onSaveSongMetadata = { song, title, artist, album ->
                viewModel.saveSongMetadata(song, title, artist, album)
            },
            onSetSongCoverArt = { song, uri -> viewModel.saveSongCoverArt(song, uri) },
            metadataEditMessage = metadataEditMessage,
            onClearMetadataEditMessage = { viewModel.clearMetadataEditMessage() },
            onNavigateToPlaylist = onNavigateToPlaylist,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist,
            onNavigateToPlayer = onNavigateToPlayer,
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
            playbackQueue = playbackQueue,
            onPlaySongs = { queue, song -> viewModel.playSongs(queue, song) },
            onPlayNextSong = { viewModel.playNext(it) },
            onAddSongToQueue = { viewModel.addToQueue(it) },
            selectedTab = mainSelectedTab,
            onSelectedTabChange = { viewModel.updateMainSelectedTab(it) },
            librarySelectedTab = librarySelectedTab,
            onLibrarySelectedTabChange = { viewModel.updateLibrarySelectedTab(it) },
            onReloadMusic = { viewModel.reloadMusic() },
            listeningTimeLabel = viewModel.formatListeningTime(totalListeningSeconds),
            listeningStats = listeningStats,
            formatListeningTime = { viewModel.formatListeningTime(it) },
            smartNotificationsEnabled = smartNotificationsEnabled,
            onSmartNotificationsChange = { viewModel.setSmartNotificationsEnabled(it) },
            persistentMiniPlayerEnabled = persistentMiniPlayerEnabled,
            onPersistentMiniPlayerChange = { viewModel.setPersistentMiniPlayerEnabled(it) },
            performanceModeEnabled = performanceModeEnabled,
            onPerformanceModeChange = { viewModel.setPerformanceModeEnabled(it) },
            onClearRecentlyPlayed = { viewModel.clearRecentlyPlayed() },
            onClearSearchHistory = { viewModel.clearSearchHistory() },
            onExportBackup = {
                exportLauncher.launch(BackupManager.BACKUP_FILENAME)
            },
            onImportBackup = {
                importLauncher.launch(arrayOf(BackupManager.BACKUP_MIME, "application/json", "text/json"))
            },
            hiddenFolders = hiddenFolders,
            onHideFolder = { viewModel.hideFolder(it) },
            onUnhideFolder = { viewModel.unhideFolder(it) },
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            )
        }
    } else {
        Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(color = accentColor)
        }
    }
}
