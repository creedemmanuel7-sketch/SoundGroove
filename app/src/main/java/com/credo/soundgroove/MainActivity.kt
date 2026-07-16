package com.credo.soundgroove

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.ui.components.formatDuration
import com.credo.soundgroove.util.blendWithAlbumArt
import com.credo.soundgroove.util.rememberAlbumArtAccentColor
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val albumArtUri: Uri?,
    val albumName: String = "Inconnu",
    val folderPath: String = "",
    val duration: Long = 0L,
    val dateAdded: Long = 0L
)

data class Playlist(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val songs: List<Song> = emptyList()
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: com.credo.soundgroove.viewmodel.SoundGrooveViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val currentTheme by viewModel.currentTheme.collectAsState()
            val showThemeSelection by viewModel.showThemeSelection.collectAsState()

            SoundGrooveTheme(appTheme = currentTheme) {
                if (showThemeSelection) {
                    com.credo.soundgroove.ui.screens.ThemeSelectionScreen(
                        onThemeSelected = { theme ->
                            viewModel.completeThemeSelection(theme)
                        }
                    )
                } else {
                    val accentColor = accentColorForTheme(currentTheme)
                    com.credo.soundgroove.ui.navigation.AppNavigation(
                        viewModel = viewModel,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    player: MediaController,
    accentColor: Color,
    currentTheme: AppTheme = AppTheme.ORIGINAL_PURPLE,
    onThemeSelected: (AppTheme) -> Unit = {},
    sleepTimerRemainingSeconds: Int? = null,
    onSetSleepTimer: (Int) -> Unit = {},
    onSetSleepTimerEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    playbackSpeed: Float = 1f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
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
    selectedTab: Int = 0,
    onSelectedTabChange: (Int) -> Unit = {},
    librarySelectedTab: Int = 0,
    onLibrarySelectedTabChange: (Int) -> Unit = {},
    onReloadMusic: () -> Unit = {},
    listeningTimeLabel: String = "0 min",
    smartNotificationsEnabled: Boolean = true,
    onSmartNotificationsChange: (Boolean) -> Unit = {},
    persistentMiniPlayerEnabled: Boolean = true,
    onPersistentMiniPlayerChange: (Boolean) -> Unit = {},
    performanceModeEnabled: Boolean = false,
    onPerformanceModeChange: (Boolean) -> Unit = {},
    onClearRecentlyPlayed: () -> Unit = {},
    onToggleFavorite: (Song) -> Unit = {},
    onCreatePlaylist: (String) -> Unit = {},
    onPlaylistAddSong: (Playlist, Song) -> Unit = { _, _ -> },
    onPlaylistDelete: (Playlist) -> Unit = {},
    onPlaylistRename: (Playlist, String) -> Unit = { _, _ -> },
    onRemoveSongFromPlaylist: (Playlist, Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localCurrentSong by remember { mutableStateOf<Song?>(null) }
    var localIsPlaying by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showRecentlyPlayed by remember { mutableStateOf(false) }
    var currentPlaylist by remember { mutableStateOf<List<Song>>(emptyList()) }

    // État des overlays globaux
    var overlayedSong by remember { mutableStateOf<Song?>(null) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission, songs.isNotEmpty()) {
        if (hasPermission) {
            if (songs.isEmpty()) {
                onReloadMusic()
            }
        } else {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }

    LaunchedEffect(currentSong) {
        if (currentSong != null) localCurrentSong = currentSong
    }

    LaunchedEffect(isPlaying) {
        localIsPlaying = isPlaying
    }

    LaunchedEffect(player, songs) {
        while (true) {
            val index = player.currentMediaItemIndex
            val resolvedSong = currentPlaylist.getOrNull(index)
                ?: player.currentMediaItem?.let { item ->
                    val mediaId = item.mediaId
                    songs.find { it.uri.toString() == mediaId }
                        ?: item.localConfiguration?.uri?.let { uri -> songs.find { it.uri == uri } }
                }
            if (resolvedSong != null) {
                localCurrentSong = resolvedSong
            }
            localIsPlaying = player.isPlaying
            kotlinx.coroutines.delay(500)
        }
    }

    fun songToMediaItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title.ifBlank { song.uri.lastPathSegment ?: "Titre inconnu" })
                    .setArtist(song.artist.ifBlank { "Artiste inconnu" })
                    .setAlbumTitle(song.albumName.ifBlank { "Album inconnu" })
                    .setArtworkUri(song.albumArtUri)
                    .build()
            )
            .build()

    fun resolveSongFromMediaItem(item: MediaItem): Song? {
        val mediaId = item.mediaId
        return songs.find { it.uri.toString() == mediaId }
            ?: item.localConfiguration?.uri?.let { uri -> songs.find { it.uri == uri } }
    }

    fun rebuildPlaylistFromPlayer(): List<Song> =
        (0 until player.mediaItemCount).mapNotNull { i ->
            resolveSongFromMediaItem(player.getMediaItemAt(i))
        }

    fun playSong(song: Song, playlist: List<Song>) {
        currentPlaylist = playlist
        val mediaItems = playlist.map { s -> songToMediaItem(s) }
        val index = playlist.indexOf(song)
        player.setMediaItems(mediaItems, index, 0L)
        player.prepare()
        player.play()
    }

    fun insertPlayNext(song: Song) {
        val mediaItem = songToMediaItem(song)
        if (player.mediaItemCount == 0) {
            currentPlaylist = listOf(song)
            player.setMediaItems(listOf(mediaItem))
            player.prepare()
        } else {
            val insertAt = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
            player.addMediaItem(insertAt, mediaItem)
            val newList = currentPlaylist.toMutableList()
            newList.add(insertAt.coerceIn(0, newList.size), song)
            currentPlaylist = newList
        }
    }

    fun addToQueueEnd(song: Song) {
        val mediaItem = songToMediaItem(song)
        if (player.mediaItemCount == 0) {
            currentPlaylist = listOf(song)
            player.setMediaItems(listOf(mediaItem))
            player.prepare()
        } else {
            player.addMediaItem(mediaItem)
            currentPlaylist = currentPlaylist + song
        }
    }

    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }
    BackHandler(enabled = selectedTab != 0) { onSelectedTabChange(0) }
    BackHandler(enabled = showSongInfo) { showSongInfo = false }
    BackHandler(enabled = showPlaylistPicker) { showPlaylistPicker = false }
    BackHandler(enabled = showRecentlyPlayed) { showRecentlyPlayed = false }
    BackHandler(enabled = showPlayer) { showPlayer = false }
    var showQueue by remember { mutableStateOf(false) }
    BackHandler(enabled = showQueue) { showQueue = false }
    var showSettings by remember { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showPlaybackSpeedSheet by remember { mutableStateOf(false) }
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
                                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Accorder la permission", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        SgMotion.tabContentEnter() togetherWith SgMotion.tabContentExit()
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
                        onSeeAllRecent = { showRecentlyPlayed = true },
                        onSongClick = { song ->
                            localCurrentSong = song
                            playSong(song, songs)
                            localIsPlaying = true
                            showPlayer = true
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
                        onOpenPlayer = { showPlayer = true },
                        onNavigateToSearch = onNavigateToSearch,
                        onOpenSettings = { showSettings = true },
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
                        onSongClick = { song ->
                            localCurrentSong = song
                            playSong(song, songs)
                            localIsPlaying = true
                            showPlayer = true
                        },
                        onPlayPlaylist = { song, playlist ->
                            localCurrentSong = song
                            playSong(song, playlist)
                            localIsPlaying = true
                            showPlayer = true
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
                        onShowPlaylistPicker = { song ->
                            overlayedSong = song
                            showPlaylistPicker = true
                        },
                        selectedTab = librarySelectedTab,
                        onSelectedTabChange = onLibrarySelectedTabChange,
                        accentColor = accentColor
                    )

                    3 -> ProfileTab(
                        songs = songs,
                        recentlyPlayed = recentlyPlayed,
                        favoriteSongs = favoriteSongs,
                        playlists = playlists,
                        listeningTimeLabel = listeningTimeLabel,
                        accentColor = accentColor,
                        onOpenSettings = { showSettings = true },
                        onOpenFavorites = { onSelectedTabChange(1) },
                        onSongClick = { song ->
                            localCurrentSong = song
                            playSong(song, recentlyPlayed)
                            localIsPlaying = true
                            showPlayer = true
                        }
                    )
                }
                }
            }
            AnimatedVisibility(
                visible = persistentMiniPlayerEnabled && !showPlayer && !showRecentlyPlayed && activeSong != null,
                enter = SgMotion.slideUpEnter(),
                exit = SgMotion.slideUpExit()
            ) {
                activeSong?.let { song ->
                    val duration = song.duration.takeIf { it > 0L } ?: 1L
                    com.credo.soundgroove.ui.components.MiniPlayer(
                        song = song,
                        isPlaying = activeIsPlaying,
                        progress = (playbackPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                        accentColor = accentColor,
                        onPlayPause = {
                            if (activeIsPlaying) player.pause() else player.play()
                            localIsPlaying = !activeIsPlaying
                        },
                        onSkipNext = { player.seekToNextMediaItem() },
                        onOpen = { showPlayer = true }
                    )
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
        enter = SgMotion.slideUpEnter(),
        exit = SgMotion.slideUpExit()
    ) {
        RecentlyPlayedScreen(
            songs = recentlyPlayed,
            onClose = { showRecentlyPlayed = false },
            onSongClick = { song ->
                localCurrentSong = song
                playSong(song, recentlyPlayed)
                localIsPlaying = true
                showPlayer = true
            }
        )
    }

    AnimatedVisibility(
        visible = showPlayer && activeSong != null,
        enter = SgMotion.playerEnter(),
        exit = SgMotion.playerExit()
    ) {
        val song = activeSong!!
        PlayerScreen(
            song = song,
            isPlaying = activeIsPlaying,
            accentColor = accentColor,
            onPlayPause = {
                if (activeIsPlaying) player.pause() else player.play()
                localIsPlaying = !activeIsPlaying
            },
            onClose = { showPlayer = false },
            onSwipeDown = { showPlayer = false },
            onSwipeUp = { showQueue = true },
            player = player,
            isFavorite = favoriteSongs.any { it.id == song.id },
            onToggleFavorite = { onToggleFavorite(song) },
            onOpenQueue = { showQueue = true },
            onShowInfo = {
                overlayedSong = song
                showSongInfo = true
            },
            onShare = { com.credo.soundgroove.util.PlayerActions.shareSong(context, song) },
            onSetRingtone = { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, song) },
            playbackSpeed = playbackSpeed,
            onOpenPlaybackSpeed = { showPlaybackSpeedSheet = true }
        )
    }

    AnimatedVisibility(
        visible = showSettings,
        enter = SgMotion.slideUpEnter(),
        exit = SgMotion.slideUpExit()
    ) {
        com.credo.soundgroove.ui.screens.SettingsScreen(
            currentTheme = currentTheme,
            accentColor = accentColor,
            appVersion = appVersion,
            songCount = songs.size,
            favoriteCount = favoriteSongs.size,
            playlistCount = playlists.size,
            listeningTimeLabel = listeningTimeLabel,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            onBack = { showSettings = false },
            onThemeSelected = onThemeSelected,
            onOpenSleepTimer = { showSleepTimerSheet = true },
            onCancelSleepTimer = onCancelSleepTimer,
            playbackSpeed = playbackSpeed,
            onOpenPlaybackSpeed = { showPlaybackSpeedSheet = true },
            smartNotificationsEnabled = smartNotificationsEnabled,
            onSmartNotificationsChange = onSmartNotificationsChange,
            persistentMiniPlayerEnabled = persistentMiniPlayerEnabled,
            onPersistentMiniPlayerChange = onPersistentMiniPlayerChange,
            performanceModeEnabled = performanceModeEnabled,
            onPerformanceModeChange = onPerformanceModeChange,
            onReloadMusic = onReloadMusic,
            onClearRecentlyPlayed = onClearRecentlyPlayed
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
            accentColor = accentColor,
            onSpeedSelected = onPlaybackSpeedChange,
            onDismiss = { showPlaybackSpeedSheet = false }
        )
    }

    // Overlay infos chanson
    AnimatedVisibility(
        visible = showSongInfo && overlayedSong != null,
        enter = SgMotion.queueEnter(),
        exit = SgMotion.queueExit()
    ) {
        val song = overlayedSong!!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
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
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2D1B4E).copy(0.97f), Color(0xFF1A0A2E).copy(0.97f))
                            )
                        )
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
                                .background(DarkPurple),
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
                            Text(song.title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(song.artist, color = accentColor, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoRow(R.drawable.ic_profile, "Artiste", song.artist, accentColor)
                    InfoRow(R.drawable.ic_songs, "Titre", song.title, accentColor)
                    InfoRow(R.drawable.ic_playlists, "Album", song.albumName, accentColor)
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
                                        Color(0xFFFF6B9D).copy(0.2f)
                                    else GlassSurface,
                                    RoundedCornerShape(14.dp)
                                )
                                .border(1.dp,
                                    if (favoriteSongs.any { it.id == song.id })
                                        Color(0xFFFF6B9D).copy(0.4f)
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
                                    tint = Color(0xFFFF6B9D),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (favoriteSongs.any { it.id == song.id }) "Favori" else "Ajouter",
                                    color = Color(0xFFFF6B9D), fontSize = 13.sp, fontWeight = FontWeight.Bold
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
        enter = SgMotion.queueEnter(),
        exit = SgMotion.queueExit()
    ) {
        val song = overlayedSong!!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
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
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2D1B4E).copy(0.97f), Color(0xFF1A0A2E).copy(0.97f))
                            )
                        )
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
                                            Brush.radialGradient(listOf(LightPurple, MediumPurple)),
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("${playlist.songs.size} chanson(s)", color = TextSecondary, fontSize = 12.sp)
                                }
                                Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, tint = LightPurple, modifier = Modifier.size(20.dp))
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

    LaunchedEffect(showQueue, player.mediaItemCount, songs) {
        if (showQueue) {
            val rebuilt = rebuildPlaylistFromPlayer()
            if (rebuilt.isNotEmpty()) currentPlaylist = rebuilt
        }
    }

    val queueCurrentIndex = player.currentMediaItemIndex.coerceIn(0, (currentPlaylist.size - 1).coerceAtLeast(0))

    AnimatedVisibility(
        visible = showQueue,
        enter = SgMotion.queueEnter(),
        exit = SgMotion.queueExit()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .pointerInput(Unit) { detectTapGestures { showQueue = false } },
            contentAlignment = Alignment.BottomCenter
        ) {
            QueueScreen(
                playlist = currentPlaylist,
                currentIndex = queueCurrentIndex,
                accentColor = accentColor,
                onClose = { showQueue = false },
                onPlaySong = { index ->
                    if (currentPlaylist.isEmpty()) return@QueueScreen
                    player.seekToDefaultPosition(index)
                    player.play()
                    localIsPlaying = true
                },
                onRemoveSong = { index ->
                    if (currentPlaylist.isEmpty() || index !in currentPlaylist.indices) return@QueueScreen
                    player.removeMediaItem(index)
                    val newList = currentPlaylist.toMutableList()
                    newList.removeAt(index)
                    currentPlaylist = newList
                },
                onMoveSong = { from, to ->
                    if (currentPlaylist.isEmpty() || from !in currentPlaylist.indices || to !in currentPlaylist.indices || from == to) return@QueueScreen
                    player.moveMediaItem(from, to)
                    val newList = currentPlaylist.toMutableList()
                    val item = newList.removeAt(from)
                    newList.add(to, item)
                    currentPlaylist = newList
                }
            )
        }
    }

}


@Composable
fun BottomNavBar(selectedTab: Int, accentColor: Color, onTabSelected: (Int) -> Unit) {
    data class NavItem(val label: String, val iconRes: Int)

    val tabs = listOf(
        NavItem("Accueil", R.drawable.ic_home),
        NavItem("Bibliothèque", R.drawable.ic_playlists),
        NavItem("Recherche", R.drawable.ic_search),
        NavItem("Profil", R.drawable.ic_profile)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SgSpacing.sm, vertical = SgSpacing.xs)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = SgRadius.pill,
            accentColor = accentColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SgSpacing.xs, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, item ->
                    val selected = selectedTab == index
                    val tabBg by animateColorAsState(
                        targetValue = if (selected) accentColor.copy(alpha = 0.18f) else Color.Transparent,
                        animationSpec = SgMotion.tweenFastOf(),
                        label = "navTabBg"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) accentColor else TextTertiary,
                        animationSpec = SgMotion.tweenFastOf(),
                        label = "navIconTint"
                    )
                    val tabScale by animateFloatAsState(
                        targetValue = if (selected) 1.04f else 1f,
                        animationSpec = SgMotion.SpringSoft,
                        label = "navTabScale"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = tabScale
                                scaleY = tabScale
                            }
                            .clip(RoundedCornerShape(SgRadius.pill))
                            .background(tabBg)
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 7.dp, horizontal = SgSpacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(item.iconRes),
                                contentDescription = item.label,
                                tint = iconTint,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = iconTint,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTab(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    recentlyPlayed: List<Song>,
    favoriteSongs: List<Song>,        // ← nouveau
    playlists: List<Playlist>,         // ← nouveau
    onSeeAllRecent: () -> Unit,
    onSongClick: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,  // ← nouveau
    onShowSongInfo: (Song) -> Unit,
    onShowPlaylistPicker: (Song) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onOpenPlayer: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    accentColor: Color,
    secondaryAccent: Color = themeSecondaryAccent(accentColor)
){
    var searchQuery by remember { mutableStateOf("") }
    val filteredSongs = remember(searchQuery, songs) {
        if (searchQuery.isEmpty()) songs
        else songs.filter { song ->
            song.title.contains(searchQuery, ignoreCase = true) ||
                    song.artist.contains(searchQuery, ignoreCase = true)
        }
    }
    val dailySuggestions = remember(songs, recentlyPlayed) {
        val calendar = java.util.Calendar.getInstance()
        val daySeed = calendar.get(java.util.Calendar.YEAR) * 400L +
                calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val listenCounts = recentlyPlayed.groupingBy { it.id }.eachCount()

        fun hasUsefulMetadata(song: Song): Boolean {
            val unknownValues = setOf("", "inconnu", "unknown", "<unknown>")
            return song.title.trim().lowercase() !in unknownValues &&
                    song.artist.trim().lowercase() !in unknownValues
        }

        fun stableDailyRank(song: Song): Long {
            val mixed = song.id * 1103515245L + daySeed * 12345L
            return if (mixed == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(mixed)
        }

        songs.sortedWith(
            compareByDescending<Song> { listenCounts[it.id] ?: 0 }
                .thenByDescending { if (it.albumArtUri != null) 1 else 0 }
                .thenByDescending { if (hasUsefulMetadata(it)) 1 else 0 }
                .thenBy { stableDailyRank(it) }
        ).take(8)
    }

    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Bonjour"
        hour < 18 -> "Bon après-midi"
        else -> "Bonsoir"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(40.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Prêt à écouter ?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Paramètres",
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onOpenSettings() }
                )
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    androidx.compose.material3.TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Rechercher une chanson...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = accentColor
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        if (currentSong != null && searchQuery.isEmpty()) {
            item {
                Text(
                    text = "EN COURS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { onOpenPlayer() },
                            indication = null,
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            }
                        ),
                    cornerRadius = 20.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(accentColor.copy(0.28f), secondaryAccent.copy(0.12f))
                                )
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentSong.artist,
                                color = accentColor,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentSong.title,
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                    .border(1.dp, accentColor.copy(0.4f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (isPlaying) R.drawable.ic_play else R.drawable.ic_pause),
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isPlaying) "En lecture" else "En pause",
                                    color = accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, GlassBorder.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .background(CardSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentSong.albumArtUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(currentSong.albumArtUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_songs),
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                }
            }
        }


    if (recentlyPlayed.isNotEmpty() && searchQuery.isEmpty()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RÉCEMMENT ÉCOUTÉS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Voir tout",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                    modifier = Modifier.clickable { onSeeAllRecent() }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Grille 2x2 — seulement 4 chansons
            val rows = recentlyPlayed.take(4).chunked(2)
            rows.forEach { rowSongs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowSongs.forEach { song ->
                        GlassCard(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable { onSongClick(song) },
                            cornerRadius = 16.dp
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                if (song.albumArtUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(song.albumArtUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(0.7f))
                                            )
                                        )
                                )
                                Text(
                                    text = song.title,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                    if (rowSongs.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    if (searchQuery.isEmpty()) {
        item {
            Text(
                text = "SUGGESTION DU JOUR",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        if (dailySuggestions.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_songs),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Tes suggestions apparaîtront dès que ta bibliothèque sera chargée.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            items(dailySuggestions, key = { it.id }) { song ->
                SongItem(
                    song = song,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    onClick = { onSongClick(song) },
                    showMenu = true,
                    isFavorite = favoriteSongs.any { it.id == song.id },
                    accentColor = accentColor,
                    onToggleFavorite = { onToggleFavorite(song) },
                    onShowInfo = { onShowSongInfo(song) },
                    onShowPlaylistPicker = { onShowPlaylistPicker(song) },
                    onPlayNow = { onSongClick(song) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) }
                )
            }
            item {
                Text(
                    text = "Retrouve toutes les chansons dans Bibliothèque > Chansons.",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    } else {
        item {
            Text(
                text = "${filteredSongs.size} RÉSULTAT(S) POUR \"${searchQuery.uppercase()}\"",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        items(filteredSongs, key = { it.id }) { song ->
                SongItem(
                    song = song,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    onClick = { onSongClick(song) },
                    showMenu = true,
                    isFavorite = favoriteSongs.any { it.id == song.id },
                    accentColor = accentColor,
                    onToggleFavorite = { onToggleFavorite(song) },
                    onShowInfo = { onShowSongInfo(song) },
                    onShowPlaylistPicker = { onShowPlaylistPicker(song) },
                    onPlayNow = { onSongClick(song) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) }
                )
        }
    }

    item { Spacer(modifier = Modifier.height(16.dp)) }
}
}

@Composable
fun PlaceholderTab(icon: String, title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = TextSecondary, fontSize = 14.sp)
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,  // ← nouveau
    onLongClick: (() -> Unit)? = null,
    showMenu: Boolean = false,
    isFavorite: Boolean = false,
    accentColor: Color = LightPurple,
    onToggleFavorite: (() -> Unit)? = null,
    onShowInfo: (() -> Unit)? = null,
    onShowPlaylistPicker: (() -> Unit)? = null,
    onPlayNow: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (showMenu) menuExpanded = true
                    onLongClick?.invoke()
                }
            ),
        cornerRadius = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isPlaying)
                        Brush.linearGradient(listOf(accentColor.copy(0.15f), themeSecondaryAccent(accentColor).copy(0.1f)))
                    else
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkPurple),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_songs),
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isPlaying) accentColor else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(song.duration),
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(8.dp))

            if (isPlaying) {
                Icon(
                    painter = painterResource(R.drawable.ic_songs),
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (showMenu) {
                Box {
                    Icon(
                        painter = painterResource(R.drawable.ic_options),
                        contentDescription = "Options",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { menuExpanded = true }
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2D1B4E), Color(0xFF1A0A2E))
                            )
                        )
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Lire maintenant", color = TextPrimary, fontSize = 14.sp)
                                }
                            },
                            onClick = { menuExpanded = false; onPlayNow?.invoke() }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(R.drawable.ic_queue), contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Jouer ensuite", color = TextPrimary, fontSize = 14.sp)
                                }
                            },
                            onClick = { menuExpanded = false; onPlayNext?.invoke() }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(R.drawable.ic_playlists), contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Ajouter à la file", color = TextPrimary, fontSize = 14.sp)
                                }
                            },
                            onClick = { menuExpanded = false; onAddToQueue?.invoke() }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline),
                                        contentDescription = null,
                                        tint = Color(0xFFFF6B9D),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                                        color = TextPrimary,
                                        fontSize = 14.sp
                                    )
                                }
                            },
                            onClick = { menuExpanded = false; onToggleFavorite?.invoke() }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Ajouter à une playlist", color = TextPrimary, fontSize = 14.sp)
                                }
                            },
                            onClick = { menuExpanded = false; onShowPlaylistPicker?.invoke() }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Infos de la chanson", color = TextPrimary, fontSize = 14.sp)
                                }
                            },
                            onClick = { menuExpanded = false; onShowInfo?.invoke() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    iconRes: Int,
    label: String,
    value: String,
    accentColor: Color = LightPurple
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun RecentlyPlayedScreen(
    songs: List<Song>,
    onClose: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2D1B4E),
                        Color(0xFF1A0A2E),
                        Color(0xFF0D0D1A)
                    )
                )
            )
            .pointerInput(Unit) { detectTapGestures { } } // ← absorbe TOUS les taps
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onClose() },  // ← garde le même lambda qu'avant
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "Retour",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Récemment écoutés",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${songs.size} chansons",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightPurple, RoundedCornerShape(12.dp))
                    .clickable {
                        if (songs.isNotEmpty()) onSongClick(songs.first())
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tout jouer",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(songs) { song ->
                    SongItem(
                        song = song,
                        isPlaying = false,
                        onClick = { onSongClick(song) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}


@Composable
fun PlayerScreen(
    song: Song,
    isPlaying: Boolean,
    accentColor: Color,
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onSwipeDown: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    player: androidx.media3.common.Player,
    onShowInfo: () -> Unit = {},
    onShare: () -> Unit = {},
    onSetRingtone: () -> Unit = {},
    playbackSpeed: Float = 1f,
    onOpenPlaybackSpeed: () -> Unit = {}
) {
    val albumAccent = rememberAlbumArtAccentColor(song.albumArtUri, accentColor)
    val displayAccent = blendWithAlbumArt(accentColor, albumAccent)
    val displaySecondaryAccent = themeSecondaryAccent(displayAccent)
    val secondaryAccent = themeSecondaryAccent(accentColor)
    var progress by remember { mutableStateOf(0f) }
    var isShuffled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0L) }
    var currentPosition by remember { mutableStateOf(0L) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var verticalDragOffset by remember { mutableStateOf(0f) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    val swipeThreshold = 100.dp
    val density = androidx.compose.ui.platform.LocalDensity.current


    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(1L)
            progress = currentPosition.toFloat() / duration.toFloat()
            kotlinx.coroutines.delay(500)
        }
    }

    fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return "%d:%02d".format(minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        when {
                            verticalDragOffset > 120f -> onSwipeDown()
                            verticalDragOffset < -120f -> onSwipeUp()
                        }
                        verticalDragOffset = 0f
                    },
                    onDragCancel = { verticalDragOffset = 0f },
                    onVerticalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                        change.consume()
                        verticalDragOffset += dragAmount
                    }
                )
            }
    ) {
        // Fond flouté
        if (song.albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
            )
        }
        
        // Couche d'assombrissement
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            displayAccent.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Header — retour + titre + menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_close_down),
                        contentDescription = "Fermer",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "EN LECTURE",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { showOptionsMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_options),
                        contentDescription = "Options",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        modifier = Modifier.background(CardSurface)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_queue),
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("File d'attente", color = TextPrimary)
                                }
                            },
                            onClick = { 
                                showOptionsMenu = false
                                onOpenQueue()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Infos de la chanson", color = TextPrimary)
                                }
                            },
                            onClick = {
                                showOptionsMenu = false
                                onShowInfo()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(R.drawable.ic_options), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Partager", color = TextPrimary)
                                }
                            },
                            onClick = {
                                showOptionsMenu = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Définir comme sonnerie", color = TextPrimary)
                                }
                            },
                            onClick = {
                                showOptionsMenu = false
                                onSetRingtone()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Vitesse (${if (playbackSpeed == playbackSpeed.toLong().toFloat()) "${playbackSpeed.toLong()}x" else "${playbackSpeed}x"})", color = TextPrimary)
                                }
                            },
                            onClick = {
                                showOptionsMenu = false
                                onOpenPlaybackSpeed()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pochette
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(dragOffsetX.toInt(), 0) }
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .border(1.dp, displayAccent.copy(alpha = 0.22f), RoundedCornerShape(SgRadius.xl))
                    .clip(RoundedCornerShape(SgRadius.xl))
                    .background(SurfaceElevated)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val thresholdPx = with(density) { swipeThreshold.toPx() }
                                when {
                                    dragOffsetX < -thresholdPx -> player.seekToNextMediaItem()
                                    dragOffsetX > thresholdPx -> player.seekToPreviousMediaItem()
                                }
                                dragOffsetX = 0f
                            },
                            onDragCancel = { dragOffsetX = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffsetX += dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_songs),
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Titre + Favori
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.artist,
                            color = displayAccent,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        painter = painterResource(if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline),
                        contentDescription = "Favori",
                        tint = if (isFavorite) Color(0xFFFF6B9D) else TextSecondary,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onToggleFavorite() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Slider
            var isSeeking by remember { mutableStateOf(false) }
            var seekPosition by remember { mutableStateOf(0f) }

            androidx.compose.material3.Slider(
                value = if (isSeeking) seekPosition else progress.coerceIn(0f, 1f),
                onValueChange = { value ->
                    isSeeking = true
                    seekPosition = value
                },
                onValueChangeFinished = {
                    player.seekTo((seekPosition * duration).toLong())
                    isSeeking = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),   // ← réduit la taille du slider
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = displayAccent,
                    activeTrackColor = displayAccent,
                    inactiveTrackColor = CardSurface
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (isSeeking) (seekPosition * duration).toLong() else currentPosition),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(text = formatTime(duration), color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Contrôles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            isShuffled = !isShuffled
                            player.shuffleModeEnabled = isShuffled
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shuffle),
                        contentDescription = "Shuffle",
                        tint = if (isShuffled) displayAccent else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { player.seekToPreviousMediaItem() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_previous),
                        contentDescription = "Précédent",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            Brush.radialGradient(listOf(displayAccent, displaySecondaryAccent)),
                            CircleShape
                        )
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (isPlaying) "Pause" else "Lecture",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { player.seekToNextMediaItem() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Suivant",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            repeatMode = (repeatMode + 1) % 3
                            player.repeatMode = when (repeatMode) {
                                1 -> androidx.media3.common.Player.REPEAT_MODE_ALL
                                2 -> androidx.media3.common.Player.REPEAT_MODE_ONE
                                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (repeatMode == 2) R.drawable.ic_repeat_one else R.drawable.ic_repeat),
                        contentDescription = "Répéter",
                        tint = if (repeatMode > 0) displayAccent else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerActionButton(
                    iconRes = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline,
                    label = "Favori",
                    tint = if (isFavorite) Color(0xFFFF6B9D) else TextSecondary,
                    onClick = onToggleFavorite
                )
                PlayerActionButton(
                    iconRes = R.drawable.ic_queue,
                    label = "File",
                    tint = TextSecondary,
                    onClick = onOpenQueue
                )
                PlayerActionButton(
                    iconRes = R.drawable.ic_sort,
                    label = "Vitesse",
                    tint = TextSecondary,
                    onClick = onOpenPlaybackSpeed
                )
                PlayerActionButton(
                    iconRes = R.drawable.ic_songs,
                    label = "Infos",
                    tint = TextSecondary,
                    onClick = onShowInfo
                )
            }
        }
    }
}

@Composable
private fun PlayerActionButton(
    iconRes: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(SgRadius.pill))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

fun loadSongs(context: android.content.Context): List<Song> {
    val songs = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, selection, null, sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val albumNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol) ?: "Inconnu"
            val artist = cursor.getString(artistCol) ?: "Inconnu"
            val albumId = cursor.getLong(albumIdCol)
            val albumName = cursor.getString(albumNameCol) ?: "Inconnu"
            val dataPath = cursor.getString(dataCol) ?: ""
            val duration = cursor.getLong(durationCol)
            val dateAdded = cursor.getLong(dateAddedCol)
            val folderPath = dataPath.substringBeforeLast("/", "")
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
            )
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), albumId
            )
            songs.add(Song(id, title, artist, uri, albumArtUri, albumName, folderPath, duration, dateAdded))
        }
    }
    return songs
}

@Composable
fun ProfileTab(
    songs: List<Song>,
    recentlyPlayed: List<Song>,
    favoriteSongs: List<Song>,
    playlists: List<Playlist>,
    listeningTimeLabel: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onOpenSettings: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onSongClick: (Song) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("soundgroove_prefs", android.content.Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(prefs.getString("profile_name", "Credson") ?: "Credson") }
    var showEditDialog by remember { mutableStateOf(false) }

    val topArtists = remember(recentlyPlayed) {
        recentlyPlayed
            .groupBy { it.artist }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { it.key }
    }
    val topAlbums = remember(recentlyPlayed) {
        recentlyPlayed
            .filter { it.albumName.isNotBlank() && it.albumName != "Inconnu" }
            .groupBy { it.albumName }
            .entries
            .sortedByDescending { it.value.size }
            .take(3)
            .map { it.key to it.value.size }
    }
    val mostPlayedRecentSongs = remember(recentlyPlayed) {
        recentlyPlayed
            .groupBy { it.id }
            .values
            .sortedByDescending { it.size }
            .mapNotNull { it.firstOrNull()?.let { song -> song to it.size } }
            .take(3)
    }
    val albumCount = remember(songs) { songs.map { it.albumName }.filter { it.isNotBlank() && it != "Inconnu" }.distinct().size }
    val folderCount = remember(songs) { songs.map { it.folderPath }.filter { it.isNotBlank() }.distinct().size }
    val totalLibraryDuration = remember(songs) { songs.sumOf { it.duration } }
    val libraryDurationLabel = remember(totalLibraryDuration) {
        val totalMinutes = totalLibraryDuration / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        when {
            hours > 0 -> "${hours} h ${minutes.toString().padStart(2, '0')}"
            minutes > 0 -> "$minutes min"
            else -> "< 1 min"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(40.dp))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEditDialog = true },
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(accentColor.copy(0.18f), Color.Transparent))
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                Brush.radialGradient(listOf(accentColor, accentColor.copy(alpha = 0.5f))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.first().uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = userName,
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Appuie pour modifier", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.weight(1f), cornerRadius = SgRadius.lg, accentColor = accentColor) {
                    Column(modifier = Modifier.padding(SgSpacing.lg)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(accentColor.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_playlists),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${songs.size}",
                            color = accentColor,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Titres", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                GlassCard(modifier = Modifier.weight(1f), cornerRadius = SgRadius.lg, accentColor = accentColor) {
                    Column(modifier = Modifier.padding(SgSpacing.lg)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(FavoritePink.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_favorite_filled),
                                contentDescription = null,
                                tint = FavoritePink,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${favoriteSongs.size}",
                            color = FavoritePink,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Favoris", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = SgRadius.lg, accentColor = accentColor) {
                Column(modifier = Modifier.padding(SgSpacing.lg)) {
                    Text(
                        text = "TON ÉCOUTE",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ProfileMetric("Écouté", listeningTimeLabel, modifier = Modifier.weight(1f))
                        ProfileMetric("Playlists", "${playlists.size}", modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ProfileMetric("Albums", "$albumCount", modifier = Modifier.weight(1f))
                        ProfileMetric("Durée locale", libraryDurationLabel, modifier = Modifier.weight(1f))
                    }
                    if (folderCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ProfileMetric("Dossiers détectés", "$folderCount", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileShortcut(
                    title = "Favoris",
                    subtitle = "${favoriteSongs.size} titres",
                    iconRes = R.drawable.ic_favorite_filled,
                    tint = FavoritePink,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFavorites
                )
                ProfileShortcut(
                    title = "Réglages",
                    subtitle = "Confort et thèmes",
                    iconRes = R.drawable.ic_settings,
                    tint = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSettings
                )
            }
        }
        if (topArtists.isNotEmpty()) {
            item {
                Text(
                    text = "TOP ARTISTES",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    topArtists.take(3).forEachIndexed { index, artist ->
                        val rankColors = listOf(
                            Color(0xFFFFD700), // #1 — or
                            Color(0xFFC0C0C0), // #2 — argent
                            Color(0xFFCD7F32)  // #3 — bronze
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(
                                            Brush.radialGradient(listOf(MediumPurple, DarkPurple)),
                                            CircleShape
                                        )
                                        .border(2.dp, rankColors[index], CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Cherche une pochette pour cet artiste dans recentlyPlayed
                                    val artistSong = recentlyPlayed.firstOrNull { it.artist == artist }
                                    if (artistSong?.albumArtUri != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(artistSong.albumArtUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = artist.firstOrNull()?.uppercase() ?: "?",
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Badge rang
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(rankColors[index], CircleShape)
                                        .border(1.5.dp, Color(0xFF1A0A2E), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color(0xFF1A0A2E),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = artist,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Artistes #4 et #5 en liste compacte dessous
                if (topArtists.size > 3) {
                    Spacer(modifier = Modifier.height(16.dp))
                    topArtists.drop(3).forEachIndexed { index, artist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${index + 4}",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(36.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        Brush.radialGradient(listOf(MediumPurple, DarkPurple)),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = artist.firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = artist,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (topAlbums.isNotEmpty()) {
            item {
                Text(
                    text = "TOP ALBUMS RÉCENTS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    topAlbums.forEachIndexed { index, (album, count) ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    color = accentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp)
                                )
                                Icon(
                                    imageVector = Icons.Filled.Album,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = album,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$count écoutes",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (mostPlayedRecentSongs.isNotEmpty()) {
            item {
                Text(
                    text = "MORCEAUX À RETROUVER",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(mostPlayedRecentSongs) { (song, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongItem(song = song, isPlaying = false, onClick = { onSongClick(song) })
                    Text(
                        text = "x$count",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        item {
            Text(
                text = "RÉCEMMENT ÉCOUTÉS",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(recentlyPlayed.take(5)) { song ->
            SongItem(song = song, isPlaying = false, onClick = { onSongClick(song) })
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showEditDialog) {
        var tempName by remember { mutableStateOf(userName) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .background(CardSurface, RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Modifier le profil",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.TextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Nom", color = TextSecondary) },
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = DarkPurple,
                        unfocusedContainerColor = DarkPurple,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = LightPurple,
                        focusedIndicatorColor = LightPurple,
                        unfocusedIndicatorColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Annuler",
                        color = TextSecondary,
                        modifier = Modifier
                            .clickable { showEditDialog = false }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(LightPurple, RoundedCornerShape(12.dp))
                            .clickable {
                                userName = tempName.ifBlank { "Credson" }
                                prefs.edit().putString("profile_name", userName).apply()
                                showEditDialog = false
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Enregistrer",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface.copy(alpha = 0.32f))
            .border(1.dp, GlassBorder.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileShortcut(
    title: String,
    subtitle: String,
    iconRes: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.clickable { onClick() },
        cornerRadius = SgRadius.lg,
        accentColor = tint
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(tint.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LibraryTab(
    songs: List<Song>,
    sortedSongs: List<Song> = emptyList(),
    sortMode: Int = 0,
    onSortModeChange: (Int) -> Unit = {},
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongs: List<Song>,
    playlists: List<Playlist>,
    onPlaylistCreate: (String) -> Unit,
    onPlaylistAddSong: (Playlist, Song) -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayPlaylist: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlaylistDelete: (Playlist) -> Unit,
    onPlaylistRename: (Playlist, String) -> Unit,
    onRemoveSongFromPlaylist: (Playlist, Long) -> Unit = { _, _ -> },
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onShowSongInfo: (Song) -> Unit = {},
    onShowPlaylistPicker: (Song) -> Unit = {},
    selectedTab: Int = 0,
    onSelectedTabChange: (Int) -> Unit = {},
    accentColor: Color
) {
    var selectedAlbum by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var selectedArtist by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var selectedFolder by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    
    BackHandler(enabled = selectedAlbum != null) { selectedAlbum = null }
    BackHandler(enabled = selectedArtist != null) { selectedArtist = null }
    BackHandler(enabled = selectedFolder != null) { selectedFolder = null }
    val tabs = listOf("Chansons", "Albums", "Artistes", "Playlists", "Dossiers", "Favoris")
    val albums = remember(songs) {
        songs.groupBy { it.albumName }
            .entries
            .sortedBy { it.key }
            .map { Pair(it.key, it.value) }
    }

    val artists = remember(songs) {
        songs.map { it.artist }.distinct().sorted()
    }

    val folders = remember(songs) {
        songs.groupBy { song ->
            song.folderPath.takeIf { it.isNotBlank() } ?: "Dossier inconnu"
        }.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Contenu principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                    .padding(horizontal = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Ma Musique",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val tabIcons = listOf(
                R.drawable.ic_songs,
                R.drawable.ic_playlists,
                R.drawable.ic_profile,
                R.drawable.ic_queue,
                R.drawable.ic_songs,
                R.drawable.ic_favorite_outline
            )
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = selectedTab.coerceIn(0, tabs.lastIndex),
                pageCount = { tabs.size }
            )
            val tabsListState = androidx.compose.foundation.lazy.rememberLazyListState()
            val scope = rememberCoroutineScope()
            val activeTab = pagerState.currentPage.coerceIn(0, tabs.lastIndex)

            LaunchedEffect(selectedTab) {
                val target = selectedTab.coerceIn(0, tabs.lastIndex)
                if (pagerState.currentPage != target) {
                    pagerState.animateScrollToPage(target)
                }
            }
            LaunchedEffect(activeTab) {
                if (selectedTab != activeTab) {
                    onSelectedTabChange(activeTab)
                }
                tabsListState.animateScrollToItem(activeTab)
            }

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = tabsListState,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tabs.size) { index ->
                    val selected = activeTab == index
                    val iconRes = if (index == 5 && selected) R.drawable.ic_favorite_filled else tabIcons.getOrElse(index) { R.drawable.ic_songs }
                    val chipBg by animateColorAsState(
                        targetValue = if (selected) accentColor else GlassSurface,
                        animationSpec = SgMotion.tweenFastOf(),
                        label = "libChipBg"
                    )
                    val chipBorder by animateColorAsState(
                        targetValue = if (selected) accentColor.copy(0.5f) else GlassBorder,
                        animationSpec = SgMotion.tweenFastOf(),
                        label = "libChipBorder"
                    )
                    val chipScale by animateFloatAsState(
                        targetValue = if (selected) 1.03f else 1f,
                        animationSpec = SgMotion.SpringSoft,
                        label = "libChipScale"
                    )
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = chipScale
                                scaleY = chipScale
                            }
                            .clip(RoundedCornerShape(SgRadius.pill))
                            .background(
                                if (selected) Brush.horizontalGradient(listOf(chipBg, themeSecondaryAccent(accentColor)))
                                else Brush.horizontalGradient(listOf(chipBg, chipBg))
                            )
                            .border(1.dp, chipBorder, RoundedCornerShape(SgRadius.pill))
                            .clickable {
                                onSelectedTabChange(index)
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                    tabsListState.animateScrollToItem(index)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(iconRes),
                            contentDescription = null,
                            tint = if (selected) Color.White else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tabs[index],
                            color = if (selected) Color.White else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        var showSortSheet by remember { mutableStateOf(false) }
                        var menuSong by remember { mutableStateOf<Song?>(null) }
                        val sortLabels = listOf("Nom (A → Z)", "Nom (Z → A)", "Artiste", "Récent")
                        val displaySongs = sortedSongs.ifEmpty { songs }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${songs.size} titre(s)",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(SgRadius.pill))
                                        .background(GlassSurface)
                                        .border(1.dp, GlassBorder, RoundedCornerShape(SgRadius.pill))
                                        .clickable { showSortSheet = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_sort),
                                        contentDescription = "Trier",
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = sortLabels[sortMode],
                                        color = accentColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(displaySongs, key = { it.id }) { song ->
                                    com.credo.soundgroove.ui.components.SongListItem(
                                        song = song,
                                        isFavorite = favoriteSongs.any { it.id == song.id },
                                        isCurrentSong = currentSong?.id == song.id,
                                        accentColor = accentColor,
                                        onClick = { onPlayPlaylist(song, displaySongs) },
                                        onMenuClick = { menuSong = song }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                        }

                        if (showSortSheet) {
                            com.credo.soundgroove.ui.components.SortBottomSheet(
                                currentMode = sortMode,
                                onModeSelected = onSortModeChange,
                                onDismiss = { showSortSheet = false }
                            )
                        }

                        menuSong?.let { song ->
                            com.credo.soundgroove.ui.components.SongContextMenuSheet(
                                song = song,
                                isFavorite = favoriteSongs.any { it.id == song.id },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onPlayNext = { onPlayNext(song); menuSong = null },
                                onAddToQueue = { onAddToQueue(song); menuSong = null },
                                onAddToPlaylist = { onShowPlaylistPicker(song); menuSong = null },
                                onViewInfo = { onShowSongInfo(song); menuSong = null },
                                onDismiss = { menuSong = null }
                            )
                        }
                    }

                1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val rows = albums.chunked(2)
                    items(rows, key = { row -> row.joinToString("|") { it.first } }) { rowAlbums ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowAlbums.forEach { (albumName, albumSongs) ->
                                GlassCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clickable { onNavigateToAlbum(albumName) },
                                    cornerRadius = 16.dp
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.BottomStart
                                    ) {
                                        val coverSong =
                                            albumSongs.firstOrNull { it.albumArtUri != null }
                                        if (coverSong?.albumArtUri != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(coverSong.albumArtUri)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            Color.Black.copy(0.8f)
                                                        )
                                                    )
                                                )
                                        )
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = albumName,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${albumSongs.size} titres",
                                                color = TextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowAlbums.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                2 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(artists, key = { it }) { artist ->
                        val artistSongs = songs.filter { it.artist == artist }
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToArtist(artist) },
                            cornerRadius = 14.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(DarkPurple),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val coverSong =
                                        artistSongs.firstOrNull { it.albumArtUri != null }
                                    if (coverSong?.albumArtUri != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(coverSong.albumArtUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = artist.firstOrNull()?.uppercase() ?: "?",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${artistSongs.size} chanson(s)",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }


                3 -> {
                    // Playlists
                    var showCreateDialog by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Playlists",
                                        color = TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${playlists.size} playlist(s)",
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(SgRadius.pill))
                                        .background(Brush.horizontalGradient(listOf(accentColor, themeSecondaryAccent(accentColor))))
                                        .clickable { showCreateDialog = true }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("Créer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (playlists.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(painter = painterResource(R.drawable.ic_queue), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Aucune playlist",
                                            color = TextSecondary,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "Appuie sur + pour créer",
                                            color = TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(playlists, key = { it.id }) { playlist ->
                                        var showMenu by remember { mutableStateOf(false) }
                                        var showRenameDialog by remember { mutableStateOf(false) }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    CardSurface.copy(alpha = 0.32f),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .clickable { onNavigateToPlaylist(playlist.id) }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Cover playlist (ton code existant)
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        Brush.radialGradient(
                                                            listOf(
                                                                LightPurple,
                                                                MediumPurple
                                                            )
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (playlist.songs.isNotEmpty() && playlist.songs.first().albumArtUri != null) {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(LocalContext.current)
                                                            .data(playlist.songs.first().albumArtUri)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_songs),
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playlist.name,
                                                    color = TextPrimary,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${playlist.songs.size} chanson(s)",
                                                    color = TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            // Bouton ⋮ avec menu
                                            Box {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_options),
                                                    contentDescription = "Options",
                                                    tint = TextSecondary,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable { showMenu = true }
                                                )
                                                DropdownMenu(
                                                    expanded = showMenu,
                                                    onDismissRequest = { showMenu = false },
                                                    modifier = Modifier.background(CardSurface)
                                                ) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.ic_options),
                                                                    contentDescription = null,
                                                                    tint = TextPrimary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    "Renommer",
                                                                    color = TextPrimary
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            showMenu = false
                                                            showRenameDialog = true
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.ic_trash),
                                                                    contentDescription = null,
                                                                    tint = Color(0xFFFF6B6B),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    "Supprimer",
                                                                    color = Color(0xFFFF6B6B)
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            showMenu = false
                                                            onPlaylistDelete(playlist)
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // Dialog renommer
                                        if (showRenameDialog) {
                                            var newName by remember { mutableStateOf(playlist.name) }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.7f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(32.dp)
                                                        .background(
                                                            CardSurface,
                                                            RoundedCornerShape(20.dp)
                                                        )
                                                        .padding(24.dp)
                                                ) {
                                                    Text(
                                                        text = "Renommer la playlist",
                                                        color = TextPrimary,
                                                        fontSize = 20.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    androidx.compose.material3.TextField(
                                                        value = newName,
                                                        onValueChange = { newName = it },
                                                        label = {
                                                            Text(
                                                                "Nouveau nom",
                                                                color = TextSecondary
                                                            )
                                                        },
                                                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                                                            focusedContainerColor = DarkPurple,
                                                            unfocusedContainerColor = DarkPurple,
                                                            focusedTextColor = TextPrimary,
                                                            unfocusedTextColor = TextPrimary,
                                                            cursorColor = LightPurple,
                                                            focusedIndicatorColor = LightPurple,
                                                            unfocusedIndicatorColor = TextSecondary
                                                        ),
                                                        modifier = Modifier.fillMaxWidth(),
                                                        singleLine = true
                                                    )
                                                    Spacer(modifier = Modifier.height(24.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        Text(
                                                            text = "Annuler",
                                                            color = TextSecondary,
                                                            modifier = Modifier
                                                                .clickable {
                                                                    showRenameDialog = false
                                                                }
                                                                .padding(
                                                                    horizontal = 16.dp,
                                                                    vertical = 8.dp
                                                                )
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    if (newName.isNotBlank()) LightPurple else TextSecondary,
                                                                    RoundedCornerShape(12.dp)
                                                                )
                                                                .clickable {
                                                                    if (newName.isNotBlank()) {
                                                                        onPlaylistRename(
                                                                            playlist,
                                                                            newName.trim()
                                                                        )
                                                                        showRenameDialog = false
                                                                    }
                                                                }
                                                                .padding(
                                                                    horizontal = 20.dp,
                                                                    vertical = 8.dp
                                                                )
                                                        ) {
                                                            Text(
                                                                text = "Enregistrer",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    item { Spacer(modifier = Modifier.height(16.dp)) }
                                }
                            }
                        }

                        // Dialog créer playlist
                        if (showCreateDialog) {
                            var playlistName by remember { mutableStateOf("") }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .navigationBarsPadding()
                                        .background(
                                            Brush.verticalGradient(listOf(SurfaceOverlay.copy(0.98f), CardSurface)),
                                            RoundedCornerShape(28.dp)
                                        )
                                        .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                                        .padding(22.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(42.dp)
                                            .height(4.dp)
                                            .background(GlassBorder, RoundedCornerShape(2.dp))
                                            .align(Alignment.CenterHorizontally)
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        text = "Créer une playlist",
                                        color = TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Donne-lui un nom, tu pourras ajouter des titres ensuite.",
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    androidx.compose.material3.OutlinedTextField(
                                        value = playlistName,
                                        onValueChange = { playlistName = it },
                                        placeholder = { Text("Nom de la playlist", color = TextSecondary) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accentColor,
                                            unfocusedBorderColor = GlassBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            cursorColor = accentColor,
                                            focusedContainerColor = GlassSurface,
                                            unfocusedContainerColor = GlassSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(GlassSurface)
                                                .clickable { showCreateDialog = false }
                                                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Annuler", color = TextSecondary, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (playlistName.isNotBlank()) Brush.horizontalGradient(listOf(accentColor, themeSecondaryAccent(accentColor)))
                                                    else Brush.horizontalGradient(listOf(TextSecondary.copy(alpha = 0.35f), TextSecondary.copy(alpha = 0.25f)))
                                                )
                                                .clickable {
                                                    if (playlistName.isNotBlank()) {
                                                        onPlaylistCreate(playlistName.trim())
                                                        showCreateDialog = false
                                                        playlistName = ""
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Créer",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> {
                    val folderDetail = selectedFolder
                    if (folderDetail != null) {
                        FolderDetailContent(
                            folderName = folderDetail.first,
                            folderSongs = folderDetail.second,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            favoriteSongs = favoriteSongs,
                            accentColor = accentColor,
                            onBack = { selectedFolder = null },
                            onSongClick = { song -> onPlayPlaylist(song, folderDetail.second) },
                            onPlayAll = {
                                folderDetail.second.firstOrNull()?.let { firstSong ->
                                    onPlayPlaylist(firstSong, folderDetail.second)
                                }
                            },
                            onToggleFavorite = onToggleFavorite,
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onShowSongInfo = onShowSongInfo,
                            onShowPlaylistPicker = onShowPlaylistPicker
                        )
                    } else if (folders.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Aucun dossier trouvé", color = TextSecondary, fontSize = 16.sp)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(folders, key = { it.first }) { (folderName, folderSongs) ->
                                val folderLabel = folderName.substringAfterLast('/').ifBlank { "Dossier inconnu" }
                                val parentPath = folderName.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedFolder = folderName to folderSongs },
                                    cornerRadius = 14.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(GlassSurface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(folderLabel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                text = parentPath ?: "Stockage local",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text("${folderSongs.size} chanson(s)", color = TextSecondary, fontSize = 12.sp)
                                        }
                                        Icon(painter = painterResource(R.drawable.ic_back), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }

                5 -> {
                    if (favoriteSongs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painter = painterResource(R.drawable.ic_favorite_outline), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Aucun favori", color = TextSecondary, fontSize = 16.sp)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(favoriteSongs, key = { it.id }) { song ->
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlayPlaylist(song, favoriteSongs) },
                                    cornerRadius = 14.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        Color(0xFFFF6B9D).copy(
                                                            0.1f
                                                        ), Color.Transparent
                                                    )
                                                )
                                            )
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(DarkPurple),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (song.albumArtUri != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(song.albumArtUri)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = LightPurple, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = song.artist,
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                        Icon(
                                            painter = painterResource(R.drawable.ic_favorite_filled),
                                            contentDescription = "Retirer des favoris",
                                            tint = Color(0xFFFF6B9D),
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clickable { onToggleFavorite(song) }
                                        )
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
fun FolderDetailContent(
    folderName: String,
    folderSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongs: List<Song>,
    accentColor: Color,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onShowSongInfo: (Song) -> Unit,
    onShowPlaylistPicker: (Song) -> Unit
) {
    var menuSong by remember { mutableStateOf<Song?>(null) }
    val folderLabel = folderName.substringAfterLast('/').ifBlank { "Dossier inconnu" }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Retour",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderLabel,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folderSongs.size} chanson(s)",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SgRadius.pill))
                    .background(Brush.horizontalGradient(listOf(accentColor, themeSecondaryAccent(accentColor))))
                    .clickable(enabled = folderSongs.isNotEmpty()) { onPlayAll() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Tout lire", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(folderSongs, key = { it.id }) { song ->
                com.credo.soundgroove.ui.components.SongListItem(
                    song = song,
                    isFavorite = favoriteSongs.any { it.id == song.id },
                    isCurrentSong = currentSong?.id == song.id && isPlaying,
                    accentColor = accentColor,
                    onClick = { onSongClick(song) },
                    onMenuClick = { menuSong = song }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    menuSong?.let { song ->
        com.credo.soundgroove.ui.components.SongContextMenuSheet(
            song = song,
            isFavorite = favoriteSongs.any { it.id == song.id },
            onToggleFavorite = { onToggleFavorite(song) },
            onPlayNext = { onPlayNext(song); menuSong = null },
            onAddToQueue = { onAddToQueue(song); menuSong = null },
            onAddToPlaylist = { onShowPlaylistPicker(song); menuSong = null },
            onViewInfo = { onShowSongInfo(song); menuSong = null },
            onDismiss = { menuSong = null }
        )
    }
}

@Composable
fun PlaylistScreen(
    title: String,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2D1B4E), Color(0xFF1A0A2E), Color(0xFF0D0D1A))
                )
            )
            .pointerInput(Unit) { detectTapGestures { } } // ← absorbe TOUS les taps
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onClose() },  // ← garde le même lambda qu'avant
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "Retour",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${songs.size} chansons",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightPurple, RoundedCornerShape(12.dp))
                    .clickable { onPlayAll() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tout jouer",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(songs) { song ->
                    SongItem(
                        song = song,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        onClick = { onSongClick(song) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────
// QueueScreen — file d'attente interactive
// ─────────────────────────────────────────────────────────────
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    playlist: List<Song>,
    currentIndex: Int,
    accentColor: Color = LightPurple,
    onClose: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit
) {
    var verticalDragOffset by remember { mutableStateOf(0f) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var itemDragOffset by remember { mutableStateOf(0f) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val safeCurrentIndex = currentIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))

    LaunchedEffect(playlist.size, safeCurrentIndex) {
        if (playlist.isNotEmpty()) {
            listState.scrollToItem(safeCurrentIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.74f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SurfaceOverlay,
                        DeepPurple,
                        Color(0xFF06030C)
                    )
                )
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (verticalDragOffset > 150f) onClose()
                        verticalDragOffset = 0f
                    },
                    onDragCancel = { verticalDragOffset = 0f },
                    onVerticalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                        change.consume()
                        verticalDragOffset += dragAmount
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Indicateur de drag (pill) en haut
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(GlassBorder, RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_close_down),
                        contentDescription = "Fermer",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "FILE D'ATTENTE",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "${playlist.size} chanson(s)",
                        color = accentColor,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (playlist.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_queue),
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Aucune chanson en file", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(playlist, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = index == safeCurrentIndex
                    val isDragging = draggingIndex == index
                    val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != androidx.compose.material3.SwipeToDismissBoxValue.Settled && !isCurrent) {
                                onRemoveSong(index)
                                true
                            } else false
                        }
                    )

                    androidx.compose.material3.SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val direction = dismissState.dismissDirection
                            val color by animateColorAsState(
                                targetValue = when (dismissState.targetValue) {
                                    androidx.compose.material3.SwipeToDismissBoxValue.Settled -> Color.Transparent
                                    else -> Color(0xFFFF4444).copy(0.8f)
                                },
                                label = "swipe_color"
                            )
                            val alignment = when (direction) {
                                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = alignment
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_trash),
                                    contentDescription = "Supprimer",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    ) {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset {
                                    IntOffset(
                                        x = 0,
                                        y = if (isDragging) itemDragOffset.toInt() else 0
                                    )
                                },
                            cornerRadius = 16.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isCurrent)
                                            Brush.linearGradient(listOf(LightPurple.copy(0.18f), Color.Transparent))
                                        else
                                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    )
                                    .clickable { onPlaySong(index) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pochette
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkPurple),
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
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_songs),
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_play),
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Titre + artiste
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = if (isCurrent) accentColor else TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .pointerInput(index, playlist.size) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    draggingIndex = index
                                                    itemDragOffset = 0f
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val currentDraggingIndex = draggingIndex ?: index
                                                    itemDragOffset += dragAmount
                                                    val rowHeightPx = 68f
                                                    when {
                                                        itemDragOffset > rowHeightPx && currentDraggingIndex < playlist.lastIndex -> {
                                                            onMoveSong(currentDraggingIndex, currentDraggingIndex + 1)
                                                            draggingIndex = currentDraggingIndex + 1
                                                            itemDragOffset -= rowHeightPx
                                                        }
                                                        itemDragOffset < -rowHeightPx && currentDraggingIndex > 0 -> {
                                                            onMoveSong(currentDraggingIndex, currentDraggingIndex - 1)
                                                            draggingIndex = currentDraggingIndex - 1
                                                            itemDragOffset += rowHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingIndex = null
                                                    itemDragOffset = 0f
                                                },
                                                onDragCancel = {
                                                    draggingIndex = null
                                                    itemDragOffset = 0f
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_drag),
                                        contentDescription = "Déplacer",
                                        tint = if (isDragging) accentColor else TextSecondary.copy(alpha = 0.75f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
