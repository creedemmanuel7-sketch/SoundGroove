package com.credo.soundgroove.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.credo.soundgroove.ui.screens.LyricsScreen
import com.credo.soundgroove.ui.screens.PlayerScreen
import com.credo.soundgroove.ui.screens.QueueScreen
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.ui.components.MiniPlayer
import com.credo.soundgroove.ui.components.CrossfadeBottomSheet
import com.credo.soundgroove.ui.components.EditMetadataBottomSheet
import com.credo.soundgroove.ui.components.PlaybackSpeedBottomSheet
import com.credo.soundgroove.ui.components.SongInfoBottomSheet
import com.credo.soundgroove.ui.components.rememberSongCoverArtPicker
import com.credo.soundgroove.ui.screens.AlbumDetailScreen
import com.credo.soundgroove.ui.screens.ArtistDetailScreen
import com.credo.soundgroove.ui.screens.FolderDetailContent
import com.credo.soundgroove.ui.screens.PlaylistDetailScreen
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.viewmodel.SoundGrooveViewModel

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val FOLDER_DETAIL = "folder/{folderPath}"
    const val ALBUM_DETAIL = "album/{albumName}"
    const val ARTIST_DETAIL = "artist/{artistName}"

    fun playlistDetail(playlistId: Long) = "playlist/$playlistId"
    fun folderDetail(folderPath: String) = "folder/${Uri.encode(folderPath)}"
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
    val recentSearches by viewModel.recentSearches.collectAsState()
    val controller by viewModel.mediaController.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val playbackPitch by viewModel.playbackPitch.collectAsState()
    val metadataEditMessage by viewModel.metadataEditMessage.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()
    val playbackQueueIndex by viewModel.playbackQueueIndex.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showPlaybackSpeedSheet by remember { mutableStateOf(false) }
    var showEditMetadata by remember { mutableStateOf(false) }

    val launchCoverPicker = rememberSongCoverArtPicker { song, uri ->
        viewModel.saveSongCoverArt(song, uri)
    }

    LaunchedEffect(metadataEditMessage) {
        metadataEditMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearMetadataEditMessage()
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.PLAYER) {
            showLyrics = false
            showQueue = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = { SgMotion.navForwardEnter() },
            exitTransition = { SgMotion.navForwardExit() },
            popEnterTransition = { SgMotion.navPopEnter() },
            popExitTransition = { SgMotion.navPopExit() }
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
                    },
                    onNavigateToPlayer = {
                        navController.navigate(Routes.PLAYER)
                    }
                )
            }

            composable(
                route = Routes.SEARCH,
                enterTransition = { SgMotion.fadeEnter() },
                exitTransition = { SgMotion.fadeExit() },
                popEnterTransition = { SgMotion.fadeEnter() },
                popExitTransition = { SgMotion.fadeExit() }
            ) {
                com.credo.soundgroove.ui.screens.SearchScreen(
                    allSongs = songs,
                    playlists = playlists,
                    favoriteSongs = favoriteSongs,
                    currentSong = currentSong,
                    accentColor = accentColor,
                    recentSearches = recentSearches,
                    onBack = { navController.popBackStack() },
                    onPlaySong = { song, queue -> viewModel.playSongs(queue, song) },
                    onAlbumClick = { albumName -> navController.navigate(Routes.albumDetail(albumName)) },
                    onArtistClick = { artistName -> navController.navigate(Routes.artistDetail(artistName)) },
                    onPlaylistClick = { playlistId -> navController.navigate(Routes.playlistDetail(playlistId)) },
                    onFolderClick = { folderPath -> navController.navigate(Routes.folderDetail(folderPath)) },
                    onMenuClick = { /* menu contextuel depuis la recherche : à brancher */ },
                    onSearchSubmitted = { viewModel.addRecentSearch(it) },
                    onClearSearchHistory = { viewModel.clearSearchHistory() }
                )
            }

            composable(
                route = Routes.PLAYER,
                // Mêmes courbes que l'overlay Player interne (MainScreen) : le lecteur doit
                // s'animer à l'identique quelle que soit la façon dont on y accède, sinon on
                // perçoit un "saut" en changeant de point d'entrée.
                enterTransition = { SgMotion.playerEnter() },
                exitTransition = { SgMotion.playerExit() },
                popEnterTransition = { SgMotion.playerPopEnter() },
                popExitTransition = { SgMotion.playerPopExit() }
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
                        onShareCard = {
                            com.credo.soundgroove.util.PlayerActions.shareSongCard(
                                context,
                                song,
                                accentColor.hashCode()
                            )
                        },
                        onEditMetadata = { showEditMetadata = true },
                        onSetRingtone = { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, song) },
                        playbackSpeed = playbackSpeed,
                        onOpenPlaybackSpeed = { showPlaybackSpeedSheet = true },
                        onOpenLyrics = { showLyrics = true }
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
                        onAddToPlaylist = { /* sélecteur de playlist non disponible depuis le détail */ },
                        onSaveMetadata = { song, title, artist, album ->
                            viewModel.saveSongMetadata(song, title, artist, album)
                        },
                        onSetCoverArt = { song, uri -> viewModel.saveSongCoverArt(song, uri) }
                    )
                }
            }

            composable(Routes.FOLDER_DETAIL) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("folderPath") ?: ""
                val folderPath = Uri.decode(encodedPath)
                val folderSongs = songs.filter { song ->
                    (song.folderPath.takeIf { it.isNotBlank() } ?: "Dossier inconnu") == folderPath
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(com.credo.soundgroove.ui.theme.GraphiteAbyss)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                ) {
                    FolderDetailContent(
                        folderName = folderPath,
                        folderSongs = folderSongs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        favoriteSongs = favoriteSongs,
                        accentColor = accentColor,
                        onBack = { navController.popBackStack() },
                        onSongClick = { song -> viewModel.playSongs(folderSongs, song) },
                        onPlayAll = {
                            folderSongs.firstOrNull()?.let { first ->
                                viewModel.playSongs(folderSongs, first)
                            }
                        },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                        onPlayNext = { song -> viewModel.playNext(song) },
                        onAddToQueue = { song -> viewModel.addToQueue(song) },
                        onShowSongInfo = { /* info chanson non branchée depuis le détail dossier */ },
                        onShowPlaylistPicker = { /* sélecteur de playlist non disponible depuis le détail dossier */ },
                        onSetCoverArt = { song, uri -> viewModel.saveSongCoverArt(song, uri) }
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
                    onAddToPlaylist = { /* sélecteur de playlist non disponible depuis le détail */ },
                    onSaveMetadata = { song, title, artist, album ->
                        viewModel.saveSongMetadata(song, title, artist, album)
                    },
                    onSetCoverArt = { song, uri -> viewModel.saveSongCoverArt(song, uri) }
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
                    onAddToPlaylist = { /* sélecteur de playlist non disponible depuis le détail */ },
                    onSaveMetadata = { song, title, artist, album ->
                        viewModel.saveSongMetadata(song, title, artist, album)
                    },
                    onSetCoverArt = { song, uri -> viewModel.saveSongCoverArt(song, uri) }
                )
            }
        }

        // Le mini-player ne s'affiche jamais sur l'écran Player plein écran : deux
        // surfaces "now playing" simultanées violeraient la loi de Jakob (l'utilisateur
        // connaît déjà ce pattern via Spotify/Apple Music — un seul lecteur visible à
        // la fois, mini en arrière-plan ou plein écran, jamais les deux).
        if (currentRoute != Routes.HOME && currentRoute != Routes.PLAYER) {
            currentSong?.let { song ->
                val duration = song.duration.takeIf { it > 0L } ?: 1L
                MiniPlayer(
                    song = song,
                    isPlaying = isPlaying,
                    progress = (playbackPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                    accentColor = accentColor,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onSkipPrevious = { viewModel.skipPrevious() },
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
                    playlist = playbackQueue,
                    currentIndex = playbackQueueIndex,
                    accentColor = accentColor,
                    onClose = { showQueue = false },
                    onPlaySong = { index -> viewModel.seekToQueueIndex(index) },
                    onRemoveSong = { index -> viewModel.removeFromPlaybackQueue(index) },
                    onMoveSong = { from, to -> viewModel.moveInPlaybackQueue(from, to) }
                )
            }
        }

        if (showLyrics && currentRoute == Routes.PLAYER) {
            val song = currentSong
            val player = controller
            if (song != null && player != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .pointerInput(Unit) { detectTapGestures { showLyrics = false } },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    LyricsScreen(
                        song = song,
                        player = player,
                        playbackPosition = playbackPosition,
                        accentColor = accentColor,
                        onClose = { showLyrics = false }
                    )
                }
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
                    onShareCard = {
                        com.credo.soundgroove.util.PlayerActions.shareSongCard(
                            context,
                            song,
                            accentColor.hashCode()
                        )
                    },
                    onEditMetadata = { showEditMetadata = true },
                    onSetCoverArt = { currentSong?.let { launchCoverPicker(it) } },
                    onSetRingtone = { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, song) },
                    onDismiss = { showSongInfo = false }
                )
            }
            if (showEditMetadata && currentRoute == Routes.PLAYER) {
                EditMetadataBottomSheet(
                    song = song,
                    accentColor = accentColor,
                    onSave = { title, artist, album ->
                        viewModel.saveSongMetadata(song, title, artist, album)
                    },
                    onDismiss = { showEditMetadata = false }
                )
            }
        }

        if (showPlaybackSpeedSheet && currentRoute == Routes.PLAYER) {
            PlaybackSpeedBottomSheet(
                currentSpeed = playbackSpeed,
                currentPitch = playbackPitch,
                accentColor = accentColor,
                onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                onPitchSelected = { viewModel.setPlaybackPitch(it) },
                onDismiss = { showPlaybackSpeedSheet = false }
            )
        }
    }
}
