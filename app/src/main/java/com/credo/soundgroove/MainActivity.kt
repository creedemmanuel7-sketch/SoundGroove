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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.ui.components.formatDuration
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
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
    onPlaybackSpeedChange: (Float) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showRecentlyPlayed by remember { mutableStateOf(false) }
    var currentPlaylist by remember { mutableStateOf<List<Song>>(emptyList()) }

    // Base de données
    val db = remember { SoundGrooveDatabase.getInstance(context) }

    // Données persistantes
    var favoriteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var recentlyPlayed by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    // État des overlays globaux
    var overlayedSong by remember { mutableStateOf<Song?>(null) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    // Charger depuis la DB au démarrage
    LaunchedEffect(Unit) {
        db.favoriteDao().getAll().collect { entities ->
            favoriteSongs = entities.map { it.toSong() }
        }
    }

    LaunchedEffect(Unit) {
        db.recentlyPlayedDao().getAll().collect { entities ->
            recentlyPlayed = entities.map { it.toSong() }
        }
    }

    LaunchedEffect(Unit) {
        db.playlistDao().getAllPlaylists().collect { playlistEntities ->
            val loadedPlaylists = playlistEntities.map { playlistEntity ->
                val songEntities = db.playlistDao()
                    .getSongsForPlaylist(playlistEntity.id).first()
                Playlist(
                    id = playlistEntity.id,
                    name = playlistEntity.name,
                    songs = songEntities.map { s ->
                        Song(
                            id = s.songId,
                            title = s.title,
                            artist = s.artist,
                            uri = Uri.parse(s.uri),
                            albumArtUri = s.albumArtUri?.let { Uri.parse(it) }
                        )
                    }
                )
            }
            playlists = loadedPlaylists
        }
    }

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

    LaunchedEffect(hasPermission) {
        if (hasPermission) songs = loadSongs(context)
        else permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
    }

    LaunchedEffect(player) {
        while (true) {
            val index = player.currentMediaItemIndex
            if (index >= 0 && index < currentPlaylist.size && currentPlaylist.isNotEmpty()) {
                currentSong = currentPlaylist[index]
                isPlaying = player.isPlaying
            }
            kotlinx.coroutines.delay(300)
        }
    }

    fun playSong(song: Song, playlist: List<Song>) {
        currentPlaylist = playlist
        val mediaItems = playlist.map { s -> MediaItem.fromUri(s.uri) }
        val index = playlist.indexOf(song)
        player.setMediaItems(mediaItems, index, 0L)
        player.prepare()
        player.play()
    }

    fun insertPlayNext(song: Song) {
        val mediaItem = MediaItem.fromUri(song.uri)
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
        val mediaItem = MediaItem.fromUri(song.uri)
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
    BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }
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
                        imageVector = Icons.Filled.AudioFile,
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
                when (selectedTab) {
                    0 -> HomeTab(
                        songs = songs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        recentlyPlayed = recentlyPlayed,
                        favoriteSongs = favoriteSongs,
                        playlists = playlists,
                        onSeeAllRecent = { showRecentlyPlayed = true },
                        onSongClick = { song ->
                            currentSong = song
                            playSong(song, songs)
                            isPlaying = true
                            showPlayer = true
                            scope.launch {
                                db.recentlyPlayedDao().insert(song.toRecentlyPlayedEntity())
                                db.recentlyPlayedDao().trimToLimit()
                            }
                        },
                        onToggleFavorite = { song ->
                            scope.launch {
                                if (favoriteSongs.any { it.id == song.id })
                                    db.favoriteDao().delete(song.id)
                                else
                                    db.favoriteDao().insert(song.toFavoriteEntity())
                            }
                        },
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
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        favoriteSongs = favoriteSongs,
                        playlists = playlists,
                        onPlaylistCreate = { name ->
                            scope.launch {
                                val newPlaylist = PlaylistEntity(
                                    id = System.currentTimeMillis(),
                                    name = name
                                )
                                db.playlistDao().insertPlaylist(newPlaylist)
                            }
                        },
                        onPlaylistAddSong = { playlist, song ->
                            scope.launch {
                                val position = playlist.songs.size
                                db.playlistDao().insertSong(
                                    PlaylistSongEntity(
                                        playlistId = playlist.id,
                                        songId = song.id,
                                        title = song.title,
                                        artist = song.artist,
                                        uri = song.uri.toString(),
                                        albumArtUri = song.albumArtUri?.toString(),
                                        position = position
                                    )
                                )
                            }
                        },
                        onSongClick = { song ->
                            currentSong = song
                            playSong(song, songs)
                            isPlaying = true
                            showPlayer = true
                            scope.launch {
                                db.recentlyPlayedDao().insert(song.toRecentlyPlayedEntity())
                                db.recentlyPlayedDao().trimToLimit()
                            }
                        },
                        onPlayPlaylist = { song, playlist ->
                            currentSong = song
                            playSong(song, playlist)
                            isPlaying = true
                            showPlayer = true
                            scope.launch {
                                db.recentlyPlayedDao().insert(song.toRecentlyPlayedEntity())
                                db.recentlyPlayedDao().trimToLimit()
                            }
                        },
                        onToggleFavorite = { song ->
                            scope.launch {
                                if (favoriteSongs.any { it.id == song.id }) {
                                    db.favoriteDao().delete(song.id)
                                } else {
                                    db.favoriteDao().insert(song.toFavoriteEntity())
                                }
                            }
                        },
                        onPlaylistDelete = { playlist ->
                            scope.launch {
                                db.playlistDao().clearPlaylist(playlist.id)
                                db.playlistDao().deletePlaylist(playlist.id)
                            }
                        },
                        onPlaylistRename = { playlist, newName ->
                            scope.launch {
                                db.playlistDao().renamePlaylist(playlist.id, newName)
                            }
                        },
                        onRemoveSongFromPlaylist = { playlist, songId ->
                            scope.launch {
                                db.playlistDao().removeSong(playlist.id, songId)
                            }
                        },
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToArtist = onNavigateToArtist,
                        accentColor = accentColor
                    )

                    2 -> SearchTab(
                        songs = songs,
                        onSongClick = { song ->
                            currentSong = song
                            playSong(song, songs)
                            isPlaying = true
                            showPlayer = true
                            scope.launch {
                                db.recentlyPlayedDao().insert(song.toRecentlyPlayedEntity())
                                db.recentlyPlayedDao().trimToLimit()
                            }
                        }
                    )

                    3 -> ProfileTab(
                        songs = songs,
                        recentlyPlayed = recentlyPlayed,
                        favoriteSongs = favoriteSongs,
                        accentColor = accentColor,
                        onSongClick = { song ->
                            currentSong = song
                            playSong(song, recentlyPlayed)
                            isPlaying = true
                            showPlayer = true
                            scope.launch {
                                db.recentlyPlayedDao().insert(song.toRecentlyPlayedEntity())
                                db.recentlyPlayedDao().trimToLimit()
                            }
                        }
                    )
                }
            }
            AnimatedVisibility(
                visible = !showPlayer && !showRecentlyPlayed && currentSong != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                currentSong?.let { song ->
                    MiniPlayer(
                        song = song,
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                        onPlayPause = {
                            if (isPlaying) player.pause() else player.play()
                            isPlaying = !isPlaying
                        },
                        onOpen = { showPlayer = true },
                        onSwipeNext = { player.seekToNextMediaItem() },
                        onSwipePrev = { player.seekToPreviousMediaItem() },
                        player = player
                    )
                }
            }


                BottomNavBar(
                    selectedTab = selectedTab,
                    accentColor = accentColor,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showRecentlyPlayed,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        RecentlyPlayedScreen(
            songs = recentlyPlayed,
            onClose = { showRecentlyPlayed = false },
            onSongClick = { song ->
                currentSong = song
                playSong(song, recentlyPlayed)
                isPlaying = true
                showPlayer = true
            }
        )
    }

    AnimatedVisibility(
        visible = showPlayer,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        PlayerScreen(
            song = currentSong!!,
            isPlaying = isPlaying,
            accentColor = accentColor,
            onPlayPause = {
                if (isPlaying) player.pause() else player.play()
                isPlaying = !isPlaying
            },
            onClose = { showPlayer = false },
            onSwipeDown = { showPlayer = false },
            onSwipeUp = { showQueue = true },
            player = player,
            isFavorite = currentSong?.let { s -> favoriteSongs.any { it.id == s.id } } ?: false,
            onToggleFavorite = {
                currentSong?.let { s ->
                    scope.launch {
                        if (favoriteSongs.any { it.id == s.id }) {
                            db.favoriteDao().delete(s.id)
                        } else {
                            db.favoriteDao().insert(s.toFavoriteEntity())
                        }
                    }
                }
            },
            onOpenQueue = { showQueue = true },
            onShowInfo = {
                overlayedSong = currentSong
                showSongInfo = true
            },
            onShare = {
                currentSong?.let { com.credo.soundgroove.util.PlayerActions.shareSong(context, it) }
            },
            onSetRingtone = {
                currentSong?.let { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, it) }
            },
            playbackSpeed = playbackSpeed,
            onOpenPlaybackSpeed = { showPlaybackSpeedSheet = true }
        )
    }

    AnimatedVisibility(
        visible = showSettings,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        com.credo.soundgroove.ui.screens.SettingsScreen(
            currentTheme = currentTheme,
            accentColor = accentColor,
            appVersion = appVersion,
            songCount = songs.size,
            favoriteCount = favoriteSongs.size,
            playlistCount = playlists.size,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            onBack = { showSettings = false },
            onThemeSelected = onThemeSelected,
            onOpenSleepTimer = { showSleepTimerSheet = true },
            onCancelSleepTimer = onCancelSleepTimer,
            playbackSpeed = playbackSpeed,
            onOpenPlaybackSpeed = { showPlaybackSpeedSheet = true }
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
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
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
                                Icon(Icons.Filled.MusicNote, null, tint = accentColor, modifier = Modifier.size(32.dp))
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
                    InfoRow(Icons.Filled.Person, "Artiste", song.artist, accentColor)
                    InfoRow(Icons.Filled.MusicNote, "Titre", song.title, accentColor)
                    InfoRow(Icons.Filled.Album, "Album", song.albumName, accentColor)
                    InfoRow(Icons.Filled.Timer, "Durée", formatDuration(song.duration), accentColor)
                    if (song.folderPath.isNotBlank()) {
                        InfoRow(Icons.Filled.Folder, "Dossier", song.folderPath, accentColor)
                    } else {
                        InfoRow(Icons.Filled.AudioFile, "Fichier", song.uri.lastPathSegment ?: "—", accentColor)
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
                                    scope.launch {
                                        if (favoriteSongs.any { it.id == song.id })
                                            db.favoriteDao().delete(song.id)
                                        else
                                            db.favoriteDao().insert(song.toFavoriteEntity())
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (favoriteSongs.any { it.id == song.id }) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    null, tint = Color(0xFFFF6B9D), modifier = Modifier.size(18.dp)
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
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
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
                                        scope.launch {
                                            db.playlistDao().insertSong(
                                                PlaylistSongEntity(
                                                    playlistId = playlist.id,
                                                    songId = song.id,
                                                    title = song.title,
                                                    artist = song.artist,
                                                    uri = song.uri.toString(),
                                                    albumArtUri = song.albumArtUri?.toString(),
                                                    position = playlist.songs.size
                                                )
                                            )
                                        }
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
                                    Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("${playlist.songs.size} chanson(s)", color = TextSecondary, fontSize = 12.sp)
                                }
                                Icon(Icons.Filled.Add, null, tint = LightPurple, modifier = Modifier.size(20.dp))
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

    LaunchedEffect(showQueue, player.mediaItemCount) {
        if (showQueue && player.mediaItemCount > 0) {
            currentPlaylist = (0 until player.mediaItemCount).mapNotNull { i ->
                val mediaId = player.getMediaItemAt(i).mediaId
                songs.find { it.uri.toString() == mediaId }
            }
        }
    }

    AnimatedVisibility(
        visible = showQueue,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        QueueScreen(
            playlist = currentPlaylist,
            currentIndex = player.currentMediaItemIndex,
            accentColor = accentColor,
            onClose = { showQueue = false },
            onPlaySong = { index ->
                player.seekToDefaultPosition(index)
                player.play()
                isPlaying = true
            },
            onRemoveSong = { index ->
                player.removeMediaItem(index)
                val newList = currentPlaylist.toMutableList()
                newList.removeAt(index)
                currentPlaylist = newList
            },
            onMoveSong = { from, to ->
                player.moveMediaItem(from, to)
                val newList = currentPlaylist.toMutableList()
                val item = newList.removeAt(from)
                newList.add(to, item)
                currentPlaylist = newList
            }
        )
    }

}


@Composable
fun BottomNavBar(selectedTab: Int, accentColor: Color, onTabSelected: (Int) -> Unit) {
    data class NavItem(
        val label: String,
        val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
        val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
    )

    val tabs = listOf(
        NavItem("Accueil", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("Bibliothèque", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
        NavItem("Recherche", Icons.Filled.Search, Icons.Outlined.Search),
        NavItem("Profil", Icons.Filled.Person, Icons.Outlined.Person)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SgSpacing.lg, vertical = SgSpacing.sm)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = SgRadius.pill,
            accentColor = accentColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SgSpacing.sm, vertical = SgSpacing.sm),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, item ->
                    val selected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(SgRadius.pill))
                            .background(
                                if (selected) accentColor.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .clickable { onTabSelected(index) }
                            .padding(vertical = SgSpacing.sm, horizontal = SgSpacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = if (selected) accentColor else TextTertiary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) accentColor else TextTertiary,
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

    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Bonjour"
        hour < 18 -> "Bon après-midi"
        else -> "Bonsoir"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(52.dp))
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
                    imageVector = Icons.Filled.Settings,
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
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
                                    listOf(accentColor.copy(0.4f), secondaryAccent.copy(0.2f))
                                )
                            )
                            .padding(16.dp),
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
                                    imageVector = if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
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
                                .size(100.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
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
                                    imageVector = Icons.Filled.MusicNote,
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

    item {
        Text(
            text = if (searchQuery.isEmpty())
                "TOUTES LES CHANSONS  •  ${songs.size}"
            else
                "${filteredSongs.size} RÉSULTAT(S) POUR \"${searchQuery.uppercase()}\"",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }

        items(filteredSongs) { song ->
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

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    accentColor: Color,
    sleepTimerRemainingSeconds: Int? = null,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    onSwipeNext: () -> Unit = {},
    onSwipePrev: () -> Unit = {},
    player: androidx.media3.common.Player
) {
    val secondaryAccent = themeSecondaryAccent(accentColor)
    var progress by remember { mutableStateOf(0f) }
    var swipeOffsetX by remember { mutableStateOf(0f) }
    val swipeThresholdPx = 80f

    LaunchedEffect(Unit) {
        while (true) {
            val duration = player.duration.coerceAtLeast(1L)
            progress = player.currentPosition.toFloat() / duration.toFloat()
            kotlinx.coroutines.delay(500)
        }
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SgSpacing.md, vertical = SgSpacing.xs)
            .offset { androidx.compose.ui.unit.IntOffset(swipeOffsetX.toInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            swipeOffsetX < -swipeThresholdPx -> onSwipeNext()
                            swipeOffsetX > swipeThresholdPx -> onSwipePrev()
                        }
                        swipeOffsetX = 0f
                    },
                    onDragCancel = { swipeOffsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(-150f, 150f)
                    }
                )
            },
        cornerRadius = SgRadius.xl,
        accentColor = accentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(SurfaceOverlay.copy(0.95f), DeepPurple.copy(0.98f))
                    )
                )
        ) {
            // Barre de progression fine tout en haut
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(CardSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(accentColor, secondaryAccent)
                            )
                        )
                )
            }

            // Contenu principal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zone cliquable (pochette + infos)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpen() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pochette
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .border(1.5.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(SgRadius.sm))
                            .clip(RoundedCornerShape(SgRadius.sm))
                            .background(SurfaceElevated),
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
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Titre + artiste
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            color = accentColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        com.credo.soundgroove.ui.screens.formatSleepTimerDisplay(sleepTimerRemainingSeconds)?.let { timerText ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Bedtime,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(timerText, color = accentColor, fontSize = 9.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Boutons de contrôle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Précédent
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { player.seekToPreviousMediaItem() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Précédent",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Play/Pause
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(accentColor, CircleShape)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause
                            else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Suivant
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { player.seekToNextMediaItem() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Suivant",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
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
        cornerRadius = 14.dp
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
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
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
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (showMenu) {
                Box {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
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
                                    Icon(Icons.Filled.PlayArrow, null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Lire maintenant", color = TextPrimary, fontSize = 14.sp)
                                }
                            },
                            onClick = { menuExpanded = false; onPlayNow?.invoke() }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Jouer ensuite", color = TextPrimary, fontSize = 14.sp)
                                }
                            },
                            onClick = { menuExpanded = false; onPlayNext?.invoke() }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlaylistAdd, null, tint = accentColor, modifier = Modifier.size(18.dp))
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
                                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        null,
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
                                    Icon(Icons.Filled.PlaylistAdd, null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Ajouter à une playlist", color = TextPrimary, fontSize = 14.sp)
                                }
                            },
                            onClick = { menuExpanded = false; onShowPlaylistPicker?.invoke() }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            imageVector = icon,
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
                        imageVector = Icons.Filled.ArrowBack,
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
                    imageVector = Icons.Filled.PlayArrow,
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
fun SearchTab(
    songs: List<Song>,
    onSongClick: (Song) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) }
    val filters = listOf("Chansons", "Artistes", "Albums")

    val filteredSongs = remember(searchQuery, selectedFilter, songs) {
        if (searchQuery.isEmpty()) emptyList()
        else when (selectedFilter) {
            0 -> songs.filter { it.title.contains(searchQuery, ignoreCase = true) }
            1 -> songs.filter { it.artist.contains(searchQuery, ignoreCase = true) }
            2 -> songs.filter { it.artist.contains(searchQuery, ignoreCase = true) }
                .distinctBy { it.artist }

            else -> emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(52.dp))

        Text(
            text = "Recherche",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Barre de recherche glass
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                androidx.compose.material3.TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(text = "Artiste, chanson...", color = TextSecondary, fontSize = 14.sp)
                    },
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = LightPurple
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filtres glass
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEachIndexed { index, filter ->
                GlassCard(
                    modifier = Modifier
                        .clickable { selectedFilter = index },
                    cornerRadius = 20.dp
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (selectedFilter == index)
                                    Brush.linearGradient(listOf(LightPurple, MediumPurple))
                                else
                                    Brush.linearGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Transparent
                                        )
                                    )
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (selectedFilter == index) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selectedFilter == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tape pour rechercher",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Chansons, artistes, albums...",
                        color = TextSecondary.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }
        } else if (filteredSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aucun résultat",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "pour \"$searchQuery\"",
                        color = TextSecondary.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            Text(
                text = "${filteredSongs.size} résultat(s)",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filteredSongs) { song ->
                    SongItem(song = song, isPlaying = false, onClick = { onSongClick(song) })
                }
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
                    .blur(100.dp)
            )
        }
        
        // Couche d'assombrissement
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(52.dp))

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
                        imageVector = Icons.Filled.KeyboardArrowDown,
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
                        imageVector = Icons.Filled.MoreVert,
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
                                    Icon(Icons.Filled.Queue, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
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
                                    Icon(Icons.Filled.Info, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
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
                                    Icon(Icons.Filled.Share, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
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
                                    Icon(Icons.Filled.Notifications, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
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
                                    Icon(Icons.Filled.Speed, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
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

            Spacer(modifier = Modifier.height(36.dp))

            // Pochette
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(dragOffsetX.toInt(), 0) }
                    .size(280.dp)
                    .border(2.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(SgRadius.xl))
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
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Titre + Favori
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            color = accentColor,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) Color(0xFFFF6B9D) else TextSecondary,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onToggleFavorite() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
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

            Spacer(modifier = Modifier.height(24.dp))

            // Contrôles
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                cornerRadius = 24.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (isShuffled) accentColor else GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable {
                                isShuffled = !isShuffled
                                player.shuffleModeEnabled = isShuffled
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffled) Color.White else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { player.seekToPreviousMediaItem() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Précédent",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                Brush.radialGradient(listOf(accentColor, secondaryAccent)),
                                CircleShape
                            )
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { player.seekToNextMediaItem() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Suivant",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (repeatMode > 0) accentColor else GlassSurface,
                                CircleShape
                            )
                            .border(1.dp, GlassBorder, CircleShape)
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
                            imageVector = if (repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Répéter",
                            tint = if (repeatMode > 0) Color.White else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
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
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onSongClick: (Song) -> Unit
) {
    var userName by remember { mutableStateOf("Credson") }
    var showEditDialog by remember { mutableStateOf(false) }

    val topArtists = remember(recentlyPlayed) {
        recentlyPlayed
            .groupBy { it.artist }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { it.key }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(52.dp))
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
                            Brush.linearGradient(listOf(MediumPurple.copy(0.3f), Color.Transparent))
                        )
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
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
                                imageVector = Icons.Filled.LibraryMusic,
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
                                imageVector = Icons.Filled.Favorite,
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
                                userName = tempName
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
fun LibraryTab(
    songs: List<Song>,
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
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    accentColor: Color
) {
    var selectedTab by remember { mutableStateOf(0) }
    var selectedAlbum by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var selectedArtist by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    
    BackHandler(enabled = selectedAlbum != null) { selectedAlbum = null }
    BackHandler(enabled = selectedArtist != null) { selectedArtist = null }
    val tabs = listOf("Chansons", "Albums", "Artistes", "Playlists", "Favoris")
    val albums = remember(songs) {
        songs.groupBy { it.albumName }
            .entries
            .sortedBy { it.key }
            .map { Pair(it.key, it.value) }
    }

    val artists = remember(songs) {
        songs.map { it.artist }.distinct().sorted()
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Contenu principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            Text(
                text = "Ma Musique",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Onglets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { selectedTab = index }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tab,
                            color = if (selectedTab == index) accentColor else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (selectedTab == index) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(2.dp)
                                    .background(accentColor, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }

            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { tabs.size })

            LaunchedEffect(selectedTab) {
                if (pagerState.currentPage != selectedTab) {
                    pagerState.animateScrollToPage(selectedTab)
                }
            }
            LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                if (!pagerState.isScrollInProgress && selectedTab != pagerState.currentPage) {
                    selectedTab = pagerState.currentPage
                }
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        // État du tri
                        var sortMode by remember { mutableStateOf(0) }
                        val sortLabels = listOf("A-Z", "Z-A", "Artiste", "Récent")

                    // Chansons triées selon le mode choisi
                    val sortedSongs = remember(sortMode, songs) {
                        when (sortMode) {
                            0 -> songs.sortedBy { it.title.lowercase() }
                            1 -> songs.sortedByDescending { it.title.lowercase() }
                            2 -> songs.sortedBy { it.artist.lowercase() }
                            3 -> songs.reversed() // MediaStore renvoie déjà par date d'ajout
                            else -> songs
                        }
                    }

                    // Barre de tri
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sort,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        sortLabels.forEachIndexed { index, label ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (sortMode == index) accentColor.copy(alpha = 0.2f) else GlassSurface,
                                        RoundedCornerShape(SgRadius.pill)
                                    )
                                    .border(
                                        1.dp,
                                        if (sortMode == index) accentColor.copy(alpha = 0.5f) else GlassBorder,
                                        RoundedCornerShape(SgRadius.pill)
                                    )
                                    .clickable { sortMode = index }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (sortMode == index) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (sortMode == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Liste triée
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sortedSongs, key = { it.id }) { song ->
                            val isFav = favoriteSongs.any { it.id == song.id }
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayPlaylist(song, sortedSongs) },
                                cornerRadius = 14.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isFav) Brush.linearGradient(
                                                listOf(
                                                    Color(0xFFFF6B9D).copy(0.1f),
                                                    Color.Transparent
                                                )
                                            )
                                            else Brush.linearGradient(
                                                listOf(Color.Transparent, Color.Transparent)
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
                                            Icon(
                                                imageVector = Icons.Filled.MusicNote,
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
                                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Favori",
                                        tint = if (isFav) Color(0xFFFF6B9D) else TextSecondary,
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

                1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val rows = albums.chunked(2)
                    items(rows) { rowAlbums ->
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
                    items(artists) { artist ->
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
                    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
                    
                    BackHandler(enabled = selectedPlaylist != null) { selectedPlaylist = null }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Bouton créer
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val playlist = null
                                        selectedPlaylist = playlist
                                    },
                                cornerRadius = 14.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${playlists.size} playlist(s)",
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(LightPurple, RoundedCornerShape(20.dp))
                                            .clickable { showCreateDialog = true }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "+",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Nouvelle playlist",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            if (playlists.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(imageVector = Icons.Filled.QueueMusic, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
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
                                    items(playlists) { playlist ->
                                        var showMenu by remember { mutableStateOf(false) }
                                        var showRenameDialog by remember { mutableStateOf(false) }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    CardSurface.copy(alpha = 0.5f),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .clickable { selectedPlaylist = playlist }
                                                .padding(12.dp),
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
                                                        imageVector = Icons.Filled.MusicNote,
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
                                                    imageVector = Icons.Filled.MoreVert,
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
                                                                    Icons.Filled.Edit,
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
                                                                    Icons.Filled.Delete,
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
                                        text = "Nouvelle playlist",
                                        color = TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    androidx.compose.material3.TextField(
                                        value = playlistName,
                                        onValueChange = { playlistName = it },
                                        label = {
                                            Text(
                                                "Nom de la playlist",
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
                                                .clickable { showCreateDialog = false }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (playlistName.isNotBlank()) LightPurple else TextSecondary,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    if (playlistName.isNotBlank()) {
                                                        onPlaylistCreate(playlistName.trim())
                                                        showCreateDialog = false
                                                        playlistName = ""
                                                    }
                                                }
                                                .padding(horizontal = 20.dp, vertical = 8.dp)
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

                        // Écran playlist sélectionnée
                        selectedPlaylist?.let { playlist ->
                            PlaylistDetailScreen(
                                playlist = playlist,
                                allSongs = songs,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                onClose = { selectedPlaylist = null },
                                onPlayAll = {
                                    if (playlist.songs.isNotEmpty())
                                        onPlayPlaylist(playlist.songs.first(), playlist.songs)
                                },
                                onSongClick = { song -> onPlayPlaylist(song, playlist.songs) },
                                onAddSong = { song -> onPlaylistAddSong(playlist, song) },
                                onDeletePlaylist = {
                                    onPlaylistDelete(playlist)
                                    selectedPlaylist = null
                                },
                                onRemoveSongs = { idsToRemove ->
                                    idsToRemove.forEach { songId ->
                                        onRemoveSongFromPlaylist(playlist, songId)
                                    }
                                }
                            )
                        }
                    }
                }

                4 -> {
                    // Favoris (ancien cas 3)
                    if (favoriteSongs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Outlined.FavoriteBorder, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Aucun favori encore",
                                    color = TextSecondary,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Appuie sur l'icône cœur pour ajouter\ndes chansons à tes favoris",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(favoriteSongs) { song ->
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
                                                Icon(imageVector = Icons.Filled.MusicNote, contentDescription = null, tint = LightPurple, modifier = Modifier.size(18.dp))
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
                                            imageVector = Icons.Filled.Favorite,
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
                        imageVector = Icons.Filled.ArrowBack,
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
                    imageVector = Icons.Filled.PlayArrow,
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

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    allSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAddSong: (Song) -> Unit,
    onDeletePlaylist: () -> Unit,        // ← nouveau
    onRemoveSongs: (Set<Long>) -> Unit   // ← nouveau
) {
    var showAddSongs by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var localAddedIds by remember {
        mutableStateOf(playlist.songs.map { it.id }.toSet())
    }

    val displaySongs = remember(playlist.songs, localAddedIds) {
        val roomIds = playlist.songs.map { it.id }.toSet()
        val allIds = roomIds + localAddedIds
        allSongs.filter { it.id in allIds }
            .sortedBy { if (it.id in roomIds) 0 else 1 }
    }

    BackHandler(enabled = showAddSongs) {
        showAddSongs = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2D1B4E), Color(0xFF1A0A2E), Color(0xFF0D0D1A))
                )
            )
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Hero section ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    // Image de fond
                    val coverSong = displaySongs.firstOrNull { it.albumArtUri != null }
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(MediumPurple, DarkPurple, Color(0xFF0D0D1A))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = LightPurple.copy(alpha = 0.5f),
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }

                    // Dégradé sombre du bas
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.5f to Color.Black.copy(alpha = 0.3f),
                                        1.0f to Color(0xFF1A0A2E)
                                    )
                                )
                            )
                    )

                    // Bouton retour en haut à gauche
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable { onClose() }
                            .align(Alignment.TopStart),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Bouton ⋮ en haut à droite (remplace le bouton + Ajouter du hero)
                    // showOptionsMenu is declared at function level

                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable { showOptionsMenu = true }
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Titre et nb chansons en bas de l'image
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = playlist.name,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${displaySongs.size} chanson(s)",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // ── Boutons Lire + Aléatoire ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bouton Lire
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(LightPurple, RoundedCornerShape(14.dp))
                            .clickable {
                                if (displaySongs.isNotEmpty()) onPlayAll()
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Lire",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bouton Aléatoire
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(MediumPurple.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .border(1.dp, LightPurple.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable {
                                if (displaySongs.isNotEmpty()) {
                                    onSongClick(displaySongs.random())
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = null,
                                tint = LightPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Aléatoire",
                                color = LightPurple,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Liste vide ──
            if (displaySongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Playlist vide",
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Appuie sur + Ajouter",
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // ── Liste des chansons ──
            items(displaySongs) { song ->
                SongItem(
                    song = song,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    onClick = { onSongClick(song) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }

        // Menu options playlist
        var showManage by remember { mutableStateOf(false) }
        if (showOptionsMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) { detectTapGestures { showOptionsMenu = false } },
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
                        // Poignée
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(GlassBorder, RoundedCornerShape(2.dp))
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Option : Ajouter des chansons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showOptionsMenu = false
                                    showAddSongs = true
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(LightPurple.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PlaylistAdd, null, tint = LightPurple, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Ajouter des chansons", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))

                        // Option : Gérer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showOptionsMenu = false
                                    showManage = true
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(CyanAccent.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Tune, null, tint = CyanAccent, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Gérer", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))

                        // Option : Supprimer la playlist
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showOptionsMenu = false
                                    onDeletePlaylist()
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Supprimer la playlist", color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Écran Gérer
        if (showManage) {
            var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
            var searchQuery by remember { mutableStateOf("") }

            val filteredManageSongs = remember(searchQuery, displaySongs) {
                if (searchQuery.isEmpty()) displaySongs
                else displaySongs.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                            it.artist.contains(searchQuery, ignoreCase = true)
                }
            }

            BackHandler { showManage = false }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2D1B4E), Color(0xFF1A0A2E), Color(0xFF0D0D1A))
                        )
                    )
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 52.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(GlassSurface, CircleShape)
                                .border(1.dp, GlassBorder, CircleShape)
                                .clickable { showManage = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.ArrowBack, null, tint = TextPrimary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${selectedIds.size} sélectionné(s)",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Barre de recherche
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        cornerRadius = 16.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            androidx.compose.material3.TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Rechercher la musique", color = TextSecondary, fontSize = 14.sp) },
                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = LightPurple
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sélectionner tout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIds = if (selectedIds.size == filteredManageSongs.size)
                                    emptySet()
                                else
                                    filteredManageSongs.map { it.id }.toSet()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(
                                    2.dp,
                                    if (selectedIds.size == filteredManageSongs.size && filteredManageSongs.isNotEmpty())
                                        LightPurple else TextSecondary,
                                    CircleShape
                                )
                                .background(
                                    if (selectedIds.size == filteredManageSongs.size && filteredManageSongs.isNotEmpty())
                                        LightPurple else Color.Transparent,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedIds.size == filteredManageSongs.size && filteredManageSongs.isNotEmpty()) {
                                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Sélectionner tout", color = LightPurple, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    // Liste
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(filteredManageSongs) { song ->
                            val isSelected = song.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) LightPurple.copy(alpha = 0.1f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedIds = if (isSelected)
                                            selectedIds - song.id
                                        else
                                            selectedIds + song.id
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Cercle de sélection
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .border(2.dp, if (isSelected) LightPurple else TextSecondary, CircleShape)
                                        .background(if (isSelected) LightPurple else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                // Pochette
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
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
                                        Icon(Icons.Filled.MusicNote, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = if (isSelected) LightPurple else TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(text = song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }

                // Barre d'actions en bas
                if (selectedIds.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF2D1B4E).copy(0.95f), Color(0xFF1A0A2E))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Enlever
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    onRemoveSongs(selectedIds)
                                    selectedIds = emptySet()
                                    showManage = false
                                }
                            ) {
                                Icon(Icons.Filled.RemoveCircleOutline, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Enlever", color = Color(0xFFFF6B6B), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }


        // ── Écran ajouter chansons ──
        if (showAddSongs) {
            val filteredSongs = remember(searchQuery, allSongs) {
                if (searchQuery.isEmpty()) allSongs
                else allSongs.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                            it.artist.contains(searchQuery, ignoreCase = true)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2D1B4E), Color(0xFF1A0A2E), Color(0xFF0D0D1A))
                        )
                    )
                    .pointerInput(Unit) { detectTapGestures { } }
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
                                .clickable { showAddSongs = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Ajouter des chansons",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(localAddedIds.size - playlist.songs.size).coerceAtLeast(0)} ajoutée(s) cette session",
                                color = LightPurple,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            androidx.compose.material3.TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text("Rechercher...", color = TextSecondary, fontSize = 14.sp)
                                },
                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = LightPurple
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filteredSongs) { song ->
                            val alreadyAdded = song.id in localAddedIds
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!alreadyAdded) {
                                            localAddedIds = localAddedIds + song.id
                                            onAddSong(song)
                                        }
                                    },
                                cornerRadius = 14.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (alreadyAdded)
                                                Brush.linearGradient(listOf(LightPurple.copy(0.15f), Color.Transparent))
                                            else
                                                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                        )
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
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
                                                imageVector = Icons.Filled.MusicNote,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = if (alreadyAdded) LightPurple else TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (alreadyAdded) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = song.artist,
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                if (alreadyAdded) LightPurple else GlassSurface,
                                                CircleShape
                                            )
                                            .border(1.dp, GlassBorder, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (alreadyAdded) Icons.Filled.Check else Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = if (alreadyAdded) Color.White else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
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
            Spacer(modifier = Modifier.height(52.dp))

            // Indicateur de drag (pill) en haut
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(GlassBorder, RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))

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
                        imageVector = Icons.Filled.KeyboardArrowDown,
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

            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(playlist, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = index == currentIndex
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
                                    Icons.Filled.Delete,
                                    contentDescription = "Supprimer",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
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
                                            Icons.Filled.MusicNote, null,
                                            tint = LightPurple,
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
                                                Icons.Filled.VolumeUp, null,
                                                tint = PurpleAccent,
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
                                        color = if (isCurrent) PurpleAccent else TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        color = LightPurple,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Icône Drag Handle (compact — remplace les grosses flèches)
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = "Déplacer",
                                    tint = TextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
