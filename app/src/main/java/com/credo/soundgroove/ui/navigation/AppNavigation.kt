package com.credo.soundgroove.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.credo.soundgroove.PlayerScreen
import com.credo.soundgroove.QueueScreen
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.MiniPlayer
import com.credo.soundgroove.ui.components.PlaybackSpeedBottomSheet
import com.credo.soundgroove.ui.components.SongInfoBottomSheet
import com.credo.soundgroove.ui.screens.AlbumDetailScreen
import com.credo.soundgroove.ui.screens.ArtistDetailScreen
import com.credo.soundgroove.ui.screens.PlaylistDetailScreen
import com.credo.soundgroove.viewmodel.SoundGrooveViewModel

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val ALBUM_DETAIL = "album/{albumName}"
    const val ARTIST_DETAIL = "artist/{artistName}"

    fun playlistDetail(playlistId: Long) = "playlist/$playlistId"
    fun albumDetail(albumName: String) = "album/${Uri.encode(albumName)}"
    fun artistDetail(artistName: String) = "artist/${Uri.encode(artistName)}"
}

private fun resolveSongFromMediaItem(item: MediaItem, songs: List<Song>): Song? {
    val mediaId = item.mediaId
    return songs.find { it.uri.toString() == mediaId }
        ?: item.localConfiguration?.uri?.let { uri -> songs.find { it.uri == uri } }
}

private fun rebuildPlaylistFromPlayer(player: Player, songs: List<Song>): List<Song> =
    (0 until player.mediaItemCount).mapNotNull { index ->
        resolveSongFromMediaItem(player.getMediaItemAt(index), songs)
    }

@Composable
fun AppNavigation(
    viewModel: SoundGrooveViewModel = viewModel(),
    accentColor: Color
) {
    val navController = rememberNavController()
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val controller by viewModel.mediaController.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    var showQueue by remember { mutableStateOf(false) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showPlaybackSpeedSheet by remember { mutableStateOf(false) }
    var currentPlaylist by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(showQueue, controller?.mediaItemCount, songs) {
        if (showQueue) {
            controller?.let { player ->
                val rebuilt = rebuildPlaylistFromPlayer(player, songs)
                if (rebuilt.isNotEmpty()) currentPlaylist = rebuilt
            }
        }
    }

    val queueCurrentIndex = controller?.currentMediaItemIndex?.coerceIn(
        0,
        (currentPlaylist.size - 1).coerceAtLeast(0)
    ) ?: 0

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(tween(150)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(150)) }
        ) {
            composable(Routes.HOME) {
                LegacyMainHost(
                    viewModel = viewModel,
                    accentColor = accentColor,
                    onNavigateToPlaylist = { playlistId ->
                        navController.navigate(Routes.playlistDetail(playlistId))
                    },
                    onNavigateToSearch = {
                        navController.navigate(Routes.SEARCH)
                    },
                    onNavigateToAlbum = { albumName ->
                        navController.navigate(Routes.albumDetail(albumName))
                    },
                    onNavigateToArtist = { artistName ->
                        navController.navigate(Routes.artistDetail(artistName))
                    }
                )
            }

            composable(
                route = Routes.SEARCH,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) {
                com.credo.soundgroove.ui.screens.SearchScreen(
                    allSongs = songs,
                    playlists = playlists,
                    favoriteSongs = favoriteSongs,
                    currentSong = currentSong,
                    accentColor = accentColor,
                    onBack = { navController.popBackStack() },
                    onPlaySong = { song -> viewModel.playSongs(songs, song) },
                    onAlbumClick = { albumName -> navController.navigate(Routes.albumDetail(albumName)) },
                    onArtistClick = { artistName -> navController.navigate(Routes.artistDetail(artistName)) },
                    onPlaylistClick = { playlistId -> navController.navigate(Routes.playlistDetail(playlistId)) },
                    onMenuClick = { /* menu contextuel depuis la recherche : à brancher */ }
                )
            }

            composable(
                route = Routes.PLAYER,
                enterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(tween(250)) },
                exitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(tween(180)) }
            ) {
                val song = currentSong
                val player = controller
                if (song != null && player != null) {
                    PlayerScreen(
                        song = song,
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        isFavorite = favoriteSongs.any { it.id == song.id },
                        onPlayPause = { viewModel.togglePlayPause() },
                        onClose = { navController.popBackStack() },
                        onSwipeDown = { navController.popBackStack() },
                        onSwipeUp = { showQueue = true },
                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                        onOpenQueue = { showQueue = true },
                        player = player,
                        onShowInfo = { showSongInfo = true },
                        onShare = { com.credo.soundgroove.util.PlayerActions.shareSong(context, song) },
                        onSetRingtone = { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, song) },
                        playbackSpeed = playbackSpeed,
                        onOpenPlaybackSpeed = { showPlaybackSpeedSheet = true }
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }

            composable(Routes.PLAYLIST_DETAIL) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull()
                val playlist = playlists.find { it.id == playlistId }

                playlist?.let {
                    PlaylistDetailScreen(
                        playlist = it,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        favoriteSongs = favoriteSongs,
                        accentColor = accentColor,
                        onBack = { navController.popBackStack() },
                        onPlaySong = { song -> viewModel.playPlaylist(it, song) },
                        onShufflePlay = {
                            val shuffled = it.copy(songs = it.songs.shuffled())
                            viewModel.playPlaylist(shuffled)
                        },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                        onRemoveSongFromPlaylist = { songId -> viewModel.removeSongFromPlaylist(it.id, songId) },
                        onDeletePlaylist = {
                            viewModel.deletePlaylist(it.id)
                            navController.popBackStack()
                        },
                        onRenamePlaylist = { newName -> viewModel.renamePlaylist(it.id, newName) },
                        onPlayNext = { song -> viewModel.playNext(song) },
                        onAddToQueue = { song -> viewModel.addToQueue(song) },
                        onAddToPlaylist = { /* sélecteur de playlist non disponible depuis le détail */ }
                    )
                }
            }

            composable(Routes.ALBUM_DETAIL) { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("albumName") ?: ""
                val albumName = Uri.decode(encodedName)
                val albumSongs = songs.filter { it.albumName == albumName }

                AlbumDetailScreen(
                    albumName = albumName,
                    songs = albumSongs,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    favoriteSongs = favoriteSongs,
                    accentColor = accentColor,
                    onBack = { navController.popBackStack() },
                    onPlaySong = { song -> viewModel.playSongs(albumSongs, song) },
                    onShufflePlay = {
                        if (albumSongs.isNotEmpty()) {
                            viewModel.playPlaylist(Playlist(name = albumName, songs = albumSongs.shuffled()))
                        }
                    },
                    onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                    onPlayNext = { song -> viewModel.playNext(song) },
                    onAddToQueue = { song -> viewModel.addToQueue(song) },
                    onAddToPlaylist = { /* sélecteur de playlist non disponible depuis le détail */ }
                )
            }

            composable(Routes.ARTIST_DETAIL) { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("artistName") ?: ""
                val artistName = Uri.decode(encodedName)
                val artistSongs = songs.filter { it.artist == artistName }

                ArtistDetailScreen(
                    artistName = artistName,
                    songs = artistSongs,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    favoriteSongs = favoriteSongs,
                    accentColor = accentColor,
                    onBack = { navController.popBackStack() },
                    onPlaySong = { song -> viewModel.playSongs(artistSongs, song) },
                    onShufflePlay = {
                        if (artistSongs.isNotEmpty()) {
                            viewModel.playPlaylist(Playlist(name = artistName, songs = artistSongs.shuffled()))
                        }
                    },
                    onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                    onPlayNext = { song -> viewModel.playNext(song) },
                    onAddToQueue = { song -> viewModel.addToQueue(song) },
                    onAddToPlaylist = { /* sélecteur de playlist non disponible depuis le détail */ }
                )
            }
        }

        if (currentRoute != Routes.HOME && currentRoute != Routes.PLAYER) {
            currentSong?.let { song ->
                val duration = song.duration.takeIf { it > 0L } ?: 1L
                MiniPlayer(
                    song = song,
                    isPlaying = isPlaying,
                    progress = (playbackPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                    accentColor = accentColor,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onSkipNext = { viewModel.skipNext() },
                    onOpen = { navController.navigate(Routes.PLAYER) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                )
            }
        }

        if (showQueue && currentRoute == Routes.PLAYER) {
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
                        val player = controller ?: return@QueueScreen
                        if (currentPlaylist.isEmpty()) return@QueueScreen
                        player.seekToDefaultPosition(index)
                        player.play()
                    },
                    onRemoveSong = { index ->
                        val player = controller ?: return@QueueScreen
                        if (currentPlaylist.isEmpty() || index !in currentPlaylist.indices) return@QueueScreen
                        player.removeMediaItem(index)
                        currentPlaylist = currentPlaylist.toMutableList().also { it.removeAt(index) }
                    },
                    onMoveSong = { from, to ->
                        val player = controller ?: return@QueueScreen
                        if (currentPlaylist.isEmpty() || from !in currentPlaylist.indices || to !in currentPlaylist.indices || from == to) {
                            return@QueueScreen
                        }
                        player.moveMediaItem(from, to)
                        currentPlaylist = currentPlaylist.toMutableList().also { list ->
                            val item = list.removeAt(from)
                            list.add(to, item)
                        }
                    }
                )
            }
        }

        currentSong?.let { song ->
            if (showSongInfo && currentRoute == Routes.PLAYER) {
                SongInfoBottomSheet(
                    song = song,
                    accentColor = accentColor,
                    isFavorite = favoriteSongs.any { it.id == song.id },
                    onToggleFavorite = { viewModel.toggleFavorite(song) },
                    onShare = { com.credo.soundgroove.util.PlayerActions.shareSong(context, song) },
                    onSetRingtone = { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, song) },
                    onDismiss = { showSongInfo = false }
                )
            }
        }

        if (showPlaybackSpeedSheet && currentRoute == Routes.PLAYER) {
            PlaybackSpeedBottomSheet(
                currentSpeed = playbackSpeed,
                accentColor = accentColor,
                onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                onDismiss = { showPlaybackSpeedSheet = false }
            )
        }
    }
}
