package com.credo.soundgroove.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.ui.components.SgLoadingState
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.data.repository.ListeningStats
import com.credo.soundgroove.ui.components.BottomNavBar
import com.credo.soundgroove.ui.components.InfoRow
import com.credo.soundgroove.ui.components.MiniPlayer
import com.credo.soundgroove.ui.components.formatDuration
import com.credo.soundgroove.ui.theme.LocalSgAnimatedVisibilityScope
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.util.MediaPermissions
import com.credo.soundgroove.util.PlayerGuards
import com.credo.soundgroove.util.blendWithAlbumArt
import com.credo.soundgroove.util.displayAlbum
import com.credo.soundgroove.util.displayArtist
import com.credo.soundgroove.util.displayTitle
import com.credo.soundgroove.util.rememberAlbumArtAccentColor
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    player: MediaController,
    accentColor: Color,
    currentTheme: AppTheme = AppTheme.NOIR_ABSOLU,
    currentAccent: AppAccent = AppAccent.VIOLET,
    onThemeSelected: (AppTheme) -> Unit = {},
    onAccentSelected: (AppAccent) -> Unit = {},
    albumCoverAccentEnabled: Boolean = false,
    onAlbumCoverAccentChange: (Boolean) -> Unit = {},
    sleepTimerRemainingSeconds: Int? = null,
    onSetSleepTimer: (Int) -> Unit = {},
    onSetSleepTimerEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
    playbackSpeed: Float = 1f,
    playbackPitch: Float = 1f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    onPlaybackPitchChange: (Float) -> Unit = {},
    gaplessEnabled: Boolean = true,
    onGaplessChange: (Boolean) -> Unit = {},
    crossfadeDurationMs: Int = 0,
    onCrossfadeDurationChange: (Int) -> Unit = {},
    equalizerEnabled: Boolean = true,
    equalizerPresetLabel: String = "Normal",
    equalizerPreset: com.credo.soundgroove.util.EqualizerPreset = com.credo.soundgroove.util.EqualizerPreset.NORMAL,
    equalizerBands: List<com.credo.soundgroove.util.EqualizerBandInfo> = emptyList(),
    onEqualizerEnabledChange: (Boolean) -> Unit = {},
    onEqualizerPresetChange: (com.credo.soundgroove.util.EqualizerPreset) -> Unit = {},
    onEqualizerBandLevelChange: (Int, Short) -> Unit = { _, _ -> },
    onRefreshEqualizerBands: () -> Unit = {},
    onSaveSongMetadata: (Song, String, String, String) -> Unit = { _, _, _, _ -> },
    onSetSongCoverArt: (Song, android.net.Uri) -> Unit = { _, _ -> },
    metadataEditMessage: String? = null,
    onClearMetadataEditMessage: () -> Unit = {},
    songs: List<Song> = emptyList(),
    sortedSongs: List<Song> = emptyList(),
    sortMode: Int = 0,
    onSortModeChange: (Int) -> Unit = {},
    favoriteSongs: List<Song> = emptyList(),
    recentlyPlayed: List<Song> = emptyList(),
    playlists: List<Playlist> = emptyList(),
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    playbackPosition: Long = 0L,
    playbackQueue: List<Song> = emptyList(),
    onPlaySongs: (List<Song>, Song) -> Unit = { _, _ -> },
    onPlayNextSong: (Song) -> Unit = {},
    onAddSongToQueue: (Song) -> Unit = {},
    selectedTab: Int = 0,
    onSelectedTabChange: (Int) -> Unit = {},
    librarySelectedTab: Int = 0,
    onLibrarySelectedTabChange: (Int) -> Unit = {},
    onReloadMusic: () -> Unit = {},
    listeningTimeLabel: String = "0 min",
    listeningStats: ListeningStats = ListeningStats(0, 0, 0, 0),
    formatListeningTime: (Long) -> String = { listeningTimeLabel },
    smartNotificationsEnabled: Boolean = true,
    onSmartNotificationsChange: (Boolean) -> Unit = {},
    persistentMiniPlayerEnabled: Boolean = true,
    onPersistentMiniPlayerChange: (Boolean) -> Unit = {},
    performanceModeEnabled: Boolean = false,
    onPerformanceModeChange: (Boolean) -> Unit = {},
    onClearRecentlyPlayed: () -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    hiddenFolders: Set<String> = emptySet(),
    onHideFolder: (String) -> Unit = {},
    onUnhideFolder: (String) -> Unit = {},
    onToggleFavorite: (Song) -> Unit = {},
    onCreatePlaylist: (name: String, onCreated: (Long) -> Unit) -> Unit = { _, _ -> },
    onPlaylistAddSong: (Playlist, Song) -> Unit = { _, _ -> },
    onAddSongsToPlaylist: (playlistId: Long, songs: List<Song>) -> Unit = { _, _ -> },
    onPlaylistDelete: (Playlist) -> Unit = {},
    onPlaylistRename: (Playlist, String) -> Unit = { _, _ -> },
    onRemoveSongFromPlaylist: (Playlist, Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberSgReducedMotion()
    var localCurrentSong by remember { mutableStateOf<Song?>(null) }
    var localIsPlaying by remember { mutableStateOf(false) }
    var showRecentlyPlayed by remember { mutableStateOf(false) }

    // État des overlays globaux
    var overlayedSong by remember { mutableStateOf<Song?>(null) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val launchCoverPicker = com.credo.soundgroove.ui.components.rememberSongCoverArtPicker(
        onCoverSelected = onSetSongCoverArt
    )

    var hasPermission by remember {
        mutableStateOf(MediaPermissions.hasAudioReadPermission(context))
    }

    var libraryScanPending by remember { mutableStateOf(true) }

    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) libraryScanPending = false
    }

    LaunchedEffect(hasPermission, songs) {
        if (hasPermission && songs.isEmpty()) {
            kotlinx.coroutines.delay(4_000)
            libraryScanPending = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val audioPermission = remember { MediaPermissions.audioReadPermission() }
    val notificationPermission = remember { MediaPermissions.postNotificationsPermission() }
    var notificationPermissionRequested by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> notificationPermissionRequested = true }

    LaunchedEffect(hasPermission, songs.isNotEmpty()) {
        if (hasPermission) {
            if (songs.isEmpty()) {
                onReloadMusic()
            }
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    LaunchedEffect(hasPermission, smartNotificationsEnabled, notificationPermission) {
        if (!hasPermission || !smartNotificationsEnabled || notificationPermission == null) return@LaunchedEffect
        if (notificationPermissionRequested) return@LaunchedEffect
        if (!MediaPermissions.hasPostNotificationsPermission(context)) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(notificationPermission)
        }
    }

    LaunchedEffect(currentSong) {
        if (currentSong != null) localCurrentSong = currentSong
    }

    LaunchedEffect(isPlaying) {
        localIsPlaying = isPlaying
    }

    LaunchedEffect(player, songs, playbackQueue) {
        while (true) {
            try {
                val index = player.currentMediaItemIndex
                val resolvedSong = playbackQueue.getOrNull(index)
                    ?: player.currentMediaItem?.let { item ->
                        PlayerGuards.resolveSongFromMediaItem(item, songs)
                    }
                if (resolvedSong != null) {
                    localCurrentSong = resolvedSong
                }
                localIsPlaying = player.isPlaying
            } catch (_: Exception) {
                break
            }
            kotlinx.coroutines.delay(500)
        }
    }

    fun playSong(song: Song, playlist: List<Song>) {
        onPlaySongs(playlist, song)
    }

    fun insertPlayNext(song: Song) {
        onPlayNextSong(song)
    }

    fun addToQueueEnd(song: Song) {
        onAddSongToQueue(song)
    }

    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }
    LaunchedEffect(playbackPitch) {
        player.playbackParameters = PlaybackParameters(player.playbackParameters.speed, playbackPitch)
    }
    LaunchedEffect(metadataEditMessage) {
        metadataEditMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            onClearMetadataEditMessage()
        }
    }
    BackHandler(enabled = selectedTab != 0) { onSelectedTabChange(0) }
    BackHandler(enabled = showSongInfo) { showSongInfo = false }
    BackHandler(enabled = showPlaylistPicker) { showPlaylistPicker = false }
    BackHandler(enabled = showRecentlyPlayed) { showRecentlyPlayed = false }
    var showSettings by remember { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showPlaybackSpeedSheet by remember { mutableStateOf(false) }
    var showCrossfadeSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showEditMetadata by remember { mutableStateOf(false) }
    val activeSong = localCurrentSong ?: currentSong
    val activeIsPlaying = localIsPlaying
    val secondaryAccent = themeSecondaryAccent(accentColor)
    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBackgroundBrush(currentTheme))
    ) {
        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_songs),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "SoundGroove a besoin d'accéder à votre musique pour la lire.",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .background(accentColor, RoundedCornerShape(12.dp))
                            .clickable {
                                permissionLauncher.launch(audioPermission)
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Accorder la permission", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (libraryScanPending && songs.isEmpty()) {
            SgLoadingState(
                message = "Analyse de votre bibliothèque musicale…",
                accentColor = accentColor,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (reducedMotion) {
                            SgMotion.tabNavTransitionReduced()
                        } else {
                            SgMotion.tabNavTransition(initialState, targetState)
                        }
                    },
                    label = "mainTab"
                ) { tab ->
                when (tab) {
                    0 -> HomeTab(
                        songs = songs,
                        currentSong = activeSong,
                        isPlaying = activeIsPlaying,
                        recentlyPlayed = recentlyPlayed,
                        favoriteSongs = favoriteSongs,
                        playlists = playlists,
                        playbackPosition = playbackPosition,
                        playbackQueue = playbackQueue,
                        onSeeAllRecent = { showRecentlyPlayed = true },
                        onPlaySong = { song, queue ->
                            localCurrentSong = song
                            playSong(song, queue)
                            localIsPlaying = true
                            onNavigateToPlayer()
                        },
                        onToggleFavorite = onToggleFavorite,
                        onShowSongInfo = { song ->
                            overlayedSong = song
                            showSongInfo = true
                        },
                        onShowPlaylistPicker = { song ->
                            overlayedSong = song
                            showPlaylistPicker = true
                        },
                        onPlayNext = { song -> insertPlayNext(song) },
                        onAddToQueue = { song -> addToQueueEnd(song) },
                        onOpenPlayer = onNavigateToPlayer,
                        onResumeListening = {
                            when {
                                activeSong != null && !activeIsPlaying -> {
                                    player.play()
                                    onNavigateToPlayer()
                                }
                                activeSong != null -> onNavigateToPlayer()
                                recentlyPlayed.isNotEmpty() -> {
                                    val song = recentlyPlayed.first()
                                    localCurrentSong = song
                                    playSong(song, recentlyPlayed)
                                    localIsPlaying = true
                                    onNavigateToPlayer()
                                }
                            }
                        },
                        onNavigateToSearch = onNavigateToSearch,
                        onOpenSettings = { showSettings = true },
                        onNavigateToLibrarySection = { section ->
                            onLibrarySelectedTabChange(section)
                            onSelectedTabChange(1)
                        },
                        accentColor = accentColor,
                        secondaryAccent = secondaryAccent
                    )

                    1 -> LibraryTab(
                        songs = songs,
                        sortedSongs = sortedSongs,
                        sortMode = sortMode,
                        onSortModeChange = onSortModeChange,
                        currentSong = activeSong,
                        isPlaying = activeIsPlaying,
                        favoriteSongs = favoriteSongs,
                        playlists = playlists,
                        onPlaylistCreate = onCreatePlaylist,
                        onPlaylistAddSong = onPlaylistAddSong,
                        onAddSongsToPlaylist = onAddSongsToPlaylist,
                        onSongClick = { song ->
                            localCurrentSong = song
                            playSong(song, songs)
                            localIsPlaying = true
                            onNavigateToPlayer()
                        },
                        onPlayPlaylist = { song, playlist ->
                            localCurrentSong = song
                            playSong(song, playlist)
                            localIsPlaying = true
                            onNavigateToPlayer()
                        },
                        onToggleFavorite = onToggleFavorite,
                        onPlaylistDelete = onPlaylistDelete,
                        onPlaylistRename = onPlaylistRename,
                        onRemoveSongFromPlaylist = onRemoveSongFromPlaylist,
                        onNavigateToPlaylist = onNavigateToPlaylist,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToArtist = onNavigateToArtist,
                        onPlayNext = { song -> insertPlayNext(song) },
                        onAddToQueue = { song -> addToQueueEnd(song) },
                        onShowSongInfo = { song ->
                            overlayedSong = song
                            showSongInfo = true
                        },
                        onShareCard = { song ->
                            com.credo.soundgroove.util.PlayerActions.shareSongCard(
                                context,
                                song,
                                accentColor.hashCode()
                            )
                        },
                        onEditMetadata = { song ->
                            overlayedSong = song
                            showEditMetadata = true
                        },
                        onSetCoverArt = onSetSongCoverArt,
                        onShowPlaylistPicker = { song ->
                            overlayedSong = song
                            showPlaylistPicker = true
                        },
                        selectedTab = librarySelectedTab,
                        onSelectedTabChange = onLibrarySelectedTabChange,
                        hiddenFolders = hiddenFolders,
                        onHideFolder = onHideFolder,
                        onUnhideFolder = onUnhideFolder,
                        accentColor = accentColor
                    )

                    3 -> ProfileTab(
                        songs = songs,
                        recentlyPlayed = recentlyPlayed,
                        favoriteSongs = favoriteSongs,
                        playlists = playlists,
                        listeningStats = listeningStats,
                        formatListeningTime = formatListeningTime,
                        currentTheme = currentTheme,
                        accentColor = accentColor,
                        smartNotificationsEnabled = smartNotificationsEnabled,
                        onSmartNotificationsChange = onSmartNotificationsChange,
                        performanceModeEnabled = performanceModeEnabled,
                        onPerformanceModeChange = onPerformanceModeChange,
                        onThemeSelected = onThemeSelected,
                        onOpenSettings = { showSettings = true },
                        onOpenFavorites = {
                            onSelectedTabChange(1)
                            onLibrarySelectedTabChange(5)
                        },
                        onOpenPlaylists = {
                            onSelectedTabChange(1)
                            onLibrarySelectedTabChange(3)
                        },
                        onExportBackup = onExportBackup,
                        onImportBackup = onImportBackup,
                        appVersion = appVersion,
                        onSongClick = { song ->
                            localCurrentSong = song
                            playSong(song, recentlyPlayed)
                            localIsPlaying = true
                            onNavigateToPlayer()
                        }
                    )
                }
                }
            }
            AnimatedVisibility(
                visible = persistentMiniPlayerEnabled && !showRecentlyPlayed && activeSong != null,
                enter = SgMotion.slideUpEnter(reducedMotion),
                exit = SgMotion.slideUpExit(reducedMotion)
            ) {
                // Fournit l'AnimatedVisibilityScope de ce mini-player "intégré" (onglet
                // Accueil) au shared element pochette, au même titre que l'overlay de
                // AppNavigation — cf. ui/theme/Motion.kt et docs/FEATURES_C_SHARED_ELEMENT.md.
                CompositionLocalProvider(LocalSgAnimatedVisibilityScope provides this@AnimatedVisibility) {
                    activeSong?.let { song ->
                        val duration = song.duration.takeIf { it > 0L } ?: 1L
                        MiniPlayer(
                            song = song,
                            isPlaying = activeIsPlaying,
                            progress = (playbackPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                            accentColor = accentColor,
                            onPlayPause = {
                                if (activeIsPlaying) player.pause() else player.play()
                                localIsPlaying = !activeIsPlaying
                            },
                            onSkipPrevious = { PlayerGuards.safeSeekToPrevious(player) },
                            onSkipNext = { PlayerGuards.safeSeekToNext(player) },
                            onOpen = onNavigateToPlayer,
                            gaplessEnabled = gaplessEnabled,
                            crossfadeDurationMs = crossfadeDurationMs,
                            albumCoverAccentEnabled = albumCoverAccentEnabled,
                        )
                    }
                }
            }


                BottomNavBar(
                    selectedTab = selectedTab,
                    accentColor = accentColor,
                    onTabSelected = { tab ->
                        if (tab == 2) {
                            onNavigateToSearch()
                        } else {
                            onSelectedTabChange(tab)
                        }
                    }
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showRecentlyPlayed,
        enter = SgMotion.slideUpEnter(reducedMotion),
        exit = SgMotion.slideUpExit(reducedMotion)
    ) {
        RecentlyPlayedScreen(
            songs = recentlyPlayed,
            onClose = { showRecentlyPlayed = false },
            onSongClick = { song ->
                localCurrentSong = song
                playSong(song, recentlyPlayed)
                localIsPlaying = true
                onNavigateToPlayer()
            }
        )
    }

    AnimatedVisibility(
        visible = showSettings,
        enter = SgMotion.slideUpEnter(reducedMotion),
        exit = SgMotion.slideUpExit(reducedMotion)
    ) {
        com.credo.soundgroove.ui.screens.SettingsScreen(
            currentTheme = currentTheme,
            currentAccent = currentAccent,
            accentColor = accentColor,
            appVersion = appVersion,
            songCount = songs.size,
            favoriteCount = favoriteSongs.size,
            playlistCount = playlists.count { !it.isSmart },
            listeningTimeLabel = formatListeningTime(listeningStats.totalSeconds),
            listeningWeekLabel = formatListeningTime(listeningStats.weekSeconds),
            listeningMonthLabel = formatListeningTime(listeningStats.monthSeconds),
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            onBack = { showSettings = false },
            onThemeSelected = onThemeSelected,
            onAccentSelected = onAccentSelected,
            albumCoverAccentEnabled = albumCoverAccentEnabled,
            onAlbumCoverAccentChange = onAlbumCoverAccentChange,
            onOpenSleepTimer = { showSleepTimerSheet = true },
            onCancelSleepTimer = onCancelSleepTimer,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            gaplessEnabled = gaplessEnabled,
            onGaplessChange = onGaplessChange,
            crossfadeDurationMs = crossfadeDurationMs,
            onOpenCrossfade = { showCrossfadeSheet = true },
            equalizerEnabled = equalizerEnabled,
            equalizerPresetLabel = equalizerPresetLabel,
            onOpenEqualizer = {
                onRefreshEqualizerBands()
                showEqualizerSheet = true
            },
            onOpenPlaybackSpeed = { showPlaybackSpeedSheet = true },
            smartNotificationsEnabled = smartNotificationsEnabled,
            onSmartNotificationsChange = onSmartNotificationsChange,
            persistentMiniPlayerEnabled = persistentMiniPlayerEnabled,
            onPersistentMiniPlayerChange = onPersistentMiniPlayerChange,
            performanceModeEnabled = performanceModeEnabled,
            onPerformanceModeChange = onPerformanceModeChange,
            onReloadMusic = onReloadMusic,
            onClearRecentlyPlayed = onClearRecentlyPlayed,
            onClearSearchHistory = onClearSearchHistory,
            onExportBackup = onExportBackup,
            onImportBackup = onImportBackup
        )
    }

    if (showSleepTimerSheet) {
        com.credo.soundgroove.ui.components.SleepTimerBottomSheet(
            accentColor = accentColor,
            onDismiss = { showSleepTimerSheet = false },
            onSelectMinutes = onSetSleepTimer,
            onSelectEndOfTrack = onSetSleepTimerEndOfTrack,
            onCancel = onCancelSleepTimer
        )
    }

    if (showPlaybackSpeedSheet) {
        com.credo.soundgroove.ui.components.PlaybackSpeedBottomSheet(
            currentSpeed = playbackSpeed,
            currentPitch = playbackPitch,
            accentColor = accentColor,
            onSpeedSelected = onPlaybackSpeedChange,
            onPitchSelected = onPlaybackPitchChange,
            onDismiss = { showPlaybackSpeedSheet = false }
        )
    }

    if (showCrossfadeSheet) {
        com.credo.soundgroove.ui.components.CrossfadeBottomSheet(
            currentMs = crossfadeDurationMs,
            gaplessEnabled = gaplessEnabled,
            accentColor = accentColor,
            onDurationSelected = onCrossfadeDurationChange,
            onDismiss = { showCrossfadeSheet = false }
        )
    }

    if (showEqualizerSheet) {
        com.credo.soundgroove.ui.components.EqualizerBottomSheet(
            enabled = equalizerEnabled,
            preset = equalizerPreset,
            bands = equalizerBands,
            accentColor = accentColor,
            onEnabledChange = onEqualizerEnabledChange,
            onPresetSelected = onEqualizerPresetChange,
            onBandLevelChange = onEqualizerBandLevelChange,
            onDismiss = { showEqualizerSheet = false }
        )
    }

    overlayedSong?.let { song ->
        if (showEditMetadata) {
            com.credo.soundgroove.ui.components.EditMetadataBottomSheet(
                song = song,
                accentColor = accentColor,
                onSave = { title, artist, album ->
                    onSaveSongMetadata(song, title, artist, album)
                },
                onSetCoverArt = { launchCoverPicker(song) },
                onDismiss = { showEditMetadata = false }
            )
        }
    }

    // Overlay infos chanson
    AnimatedVisibility(
        visible = showSongInfo && overlayedSong != null,
        enter = SgMotion.queueEnter(reducedMotion),
        exit = SgMotion.queueExit(reducedMotion)
    ) {
        val song = overlayedSong!!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimOverlay)
                .pointerInput(Unit) { detectTapGestures { showSongInfo = false } },
            contentAlignment = Alignment.BottomCenter
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } },
                cornerRadius = 28.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(sgModalContentBrush())
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(GlassBorder, RoundedCornerShape(2.dp))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(GraphiteCard),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.albumArtUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(song.albumArtUri).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = accentColor, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.displayTitle(), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(song.displayArtist(), color = accentColor, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoRow(R.drawable.ic_profile, "Artiste", song.displayArtist(), accentColor)
                    InfoRow(R.drawable.ic_songs, "Titre", song.displayTitle(), accentColor)
                    InfoRow(R.drawable.ic_playlists, "Album", song.displayAlbum(), accentColor)
                    InfoRow(R.drawable.ic_play, "Durée", formatDuration(song.duration), accentColor)
                    if (song.folderPath.isNotBlank()) {
                        InfoRow(R.drawable.ic_queue, "Dossier", song.folderPath, accentColor)
                    } else {
                        InfoRow(R.drawable.ic_songs, "Fichier", song.uri.lastPathSegment ?: "—", accentColor)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (favoriteSongs.any { it.id == song.id })
                                        FavoritePink.copy(0.2f)
                                    else GlassSurface,
                                    RoundedCornerShape(14.dp)
                                )
                                .border(1.dp,
                                    if (favoriteSongs.any { it.id == song.id })
                                        FavoritePink.copy(0.4f)
                                    else GlassBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    onToggleFavorite(song)
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(
                                        if (favoriteSongs.any { it.id == song.id }) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
                                    ),
                                    contentDescription = null,
                                    tint = FavoritePink,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (favoriteSongs.any { it.id == song.id }) "Favori" else "Ajouter",
                                    color = FavoritePink, fontSize = 13.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(accentColor, RoundedCornerShape(14.dp))
                                .clickable { showSongInfo = false }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Fermer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showPlaylistPicker && overlayedSong != null,
        enter = SgMotion.queueEnter(reducedMotion),
        exit = SgMotion.queueExit(reducedMotion)
    ) {
        val song = overlayedSong!!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimOverlay)
                .pointerInput(Unit) { detectTapGestures { showPlaylistPicker = false } },
            contentAlignment = Alignment.BottomCenter
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } },
                cornerRadius = 28.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(sgModalContentBrush())
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(GlassBorder, RoundedCornerShape(2.dp))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Ajouter à une playlist", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (playlists.isEmpty()) {
                        Text("Aucune playlist — crée-en une d'abord dans Bibliothèque", color = TextSecondary, fontSize = 14.sp)
                    } else {
                        playlists.forEach { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlaylistAddSong(playlist, song)
                                        showPlaylistPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            Brush.radialGradient(listOf(SilverAccent, GraphiteMid)),
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(com.credo.soundgroove.ui.util.songsCountLabel(playlist.songs.size), color = TextSecondary, fontSize = 12.sp)
                                }
                                Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, tint = SilverAccent, modifier = Modifier.size(20.dp))
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassSurface, RoundedCornerShape(14.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .clickable { showPlaylistPicker = false }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Annuler", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

}

