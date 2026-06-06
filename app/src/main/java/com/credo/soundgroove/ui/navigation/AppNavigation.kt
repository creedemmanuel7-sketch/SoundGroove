package com.credo.soundgroove.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.credo.soundgroove.ui.screens.PlaylistDetailScreen
import com.credo.soundgroove.ui.screens.AlbumDetailScreen
import com.credo.soundgroove.ui.screens.ArtistDetailScreen
import com.credo.soundgroove.PlayerScreen
import android.net.Uri
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.ui.components.MiniPlayer
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
            // The legacy MainScreen still handles the tabbed home experience
            // It is wired to the ViewModel via state collection
            // This will be refactored progressively
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
                    onMenuClick = { /* TODO: show bottom sheet from search */ }
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
                        onSwipeUp = { },
                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                        onOpenQueue = { },
                        player = player,
                        onShowInfo = { },
                        onShare = { com.credo.soundgroove.util.PlayerActions.shareSong(context, song) },
                        onSetRingtone = { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, song) },
                        playbackSpeed = playbackSpeed,
                        onOpenPlaybackSpeed = { }
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
                        onDeletePlaylist = { viewModel.deletePlaylist(it.id) },
                        onRenamePlaylist = { newName -> viewModel.renamePlaylist(it.id, newName) },
                        onPlayNext = { song -> viewModel.playNext(song) },
                        onAddToQueue = { song -> viewModel.addToQueue(song) },
                        onAddToPlaylist = { /* playlist picker non disponible depuis le détail */ }
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
                    onAddToPlaylist = { /* playlist picker non disponible depuis le détail */ }
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
                    onAddToPlaylist = { /* playlist picker non disponible depuis le détail */ }
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
    }
}
