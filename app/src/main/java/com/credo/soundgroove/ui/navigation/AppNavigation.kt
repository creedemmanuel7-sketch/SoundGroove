package com.credo.soundgroove.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.credo.soundgroove.ui.screens.PlaylistDetailScreen
import com.credo.soundgroove.ui.screens.AlbumDetailScreen
import com.credo.soundgroove.ui.screens.ArtistDetailScreen
import android.net.Uri
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.viewmodel.SoundGrooveViewModel

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
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
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

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
                favoriteSongs = favoriteSongs,
                currentSong = currentSong,
                accentColor = accentColor,
                onBack = { navController.popBackStack() },
                onPlaySong = { song -> viewModel.playSong(song) },
                onMenuClick = { /* TODO: show bottom sheet from search */ }
            )
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

            if (albumSongs.isNotEmpty()) {
                AlbumDetailScreen(
                    albumName = albumName,
                    songs = albumSongs,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    favoriteSongs = favoriteSongs,
                    accentColor = accentColor,
                    onBack = { navController.popBackStack() },
                    onPlaySong = { song -> viewModel.playSong(song) },
                    onShufflePlay = { viewModel.playPlaylist(Playlist(name = albumName, songs = albumSongs.shuffled())) },
                    onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                    onPlayNext = { song -> viewModel.playNext(song) },
                    onAddToQueue = { song -> viewModel.addToQueue(song) },
                    onAddToPlaylist = { /* playlist picker non disponible depuis le détail */ }
                )
            }
        }

        composable(Routes.ARTIST_DETAIL) { backStackEntry ->
            val encodedName = backStackEntry.arguments?.getString("artistName") ?: ""
            val artistName = Uri.decode(encodedName)
            val artistSongs = songs.filter { it.artist == artistName }

            if (artistSongs.isNotEmpty()) {
                ArtistDetailScreen(
                    artistName = artistName,
                    songs = artistSongs,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    favoriteSongs = favoriteSongs,
                    accentColor = accentColor,
                    onBack = { navController.popBackStack() },
                    onPlaySong = { song -> viewModel.playSong(song) },
                    onShufflePlay = { viewModel.playPlaylist(Playlist(name = artistName, songs = artistSongs.shuffled())) },
                    onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                    onPlayNext = { song -> viewModel.playNext(song) },
                    onAddToQueue = { song -> viewModel.addToQueue(song) },
                    onAddToPlaylist = { /* playlist picker non disponible depuis le détail */ }
                )
            }
        }
    }
}
