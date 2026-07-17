package com.credo.soundgroove.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.credo.soundgroove.ui.screens.LyricsScreen
import com.credo.soundgroove.ui.screens.LyricsWebSearchScreen
import com.credo.soundgroove.ui.screens.PlayerScreen
import com.credo.soundgroove.ui.screens.PlayerQueueBanner
import com.credo.soundgroove.ui.screens.QueueScreen
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.ui.components.MiniPlayer
import com.credo.soundgroove.ui.components.CrossfadeBottomSheet
import com.credo.soundgroove.ui.components.PlayerOptionsBottomSheet
import com.credo.soundgroove.ui.components.EditMetadataBottomSheet
import com.credo.soundgroove.ui.components.EqualizerBottomSheet
import com.credo.soundgroove.ui.components.PlaybackSpeedBottomSheet
import com.credo.soundgroove.ui.components.SleepTimerBottomSheet
import com.credo.soundgroove.ui.components.SongInfoBottomSheet
import com.credo.soundgroove.ui.components.rememberSongCoverArtPicker
import com.credo.soundgroove.ui.screens.AlbumDetailScreen
import com.credo.soundgroove.ui.screens.ArtistDetailScreen
import com.credo.soundgroove.ui.screens.FolderDetailContent
import com.credo.soundgroove.ui.screens.PlaylistDetailScreen
import com.credo.soundgroove.ui.theme.LocalSgAnimatedVisibilityScope
import com.credo.soundgroove.ui.theme.LocalSharedTransitionScope
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
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


@OptIn(ExperimentalSharedTransitionApi::class)
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
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsState()
    val crossfadeDurationMs by viewModel.crossfadeDurationMs.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val vinylModeEnabled by viewModel.vinylModeEnabled.collectAsState()
    val equalizerEnabled by viewModel.equalizerEnabled.collectAsState()
    val equalizerPreset by viewModel.equalizerPreset.collectAsState()
    val equalizerBands by viewModel.equalizerBands.collectAsState()
    val metadataEditMessage by viewModel.metadataEditMessage.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()
    val playbackQueueIndex by viewModel.playbackQueueIndex.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberSgReducedMotion()

    // File d'attente : PAS un overlay qui cache le Player (cf. correction utilisateur) —
    // le Player se réduit en bandeau (~1/4 écran, cf. PlayerQueueBanner) pendant que la
    // Queue occupe le reste (~3/4). `queueBannerProgress` pilote ce split : 0 = Player
    // plein écran normal, 1 = bandeau + Queue. Même mécanique que `lyricsPeekProgress`
    // (mount paresseux + anim asymétrique ouverture/fermeture), sans le drag interactif
    // (pas demandé ici : ouverture seulement via tap/swipe-up, pas de peek au doigt).
    var showQueue by remember { mutableStateOf(false) }
    val queueBannerProgress = remember { Animatable(0f) }

    fun openQueue() {
        showQueue = true
        scope.launch {
            if (reducedMotion) queueBannerProgress.snapTo(1f)
            else queueBannerProgress.animateTo(1f, animationSpec = tween(SgMotion.MediumMs, easing = SgMotion.EmphasizedDecelerate))
        }
    }

    fun closeQueue() {
        scope.launch {
            if (reducedMotion) {
                queueBannerProgress.snapTo(0f)
            } else {
                queueBannerProgress.animateTo(0f, animationSpec = tween(SgMotion.MediumMs, easing = SgMotion.EmphasizedAccelerate))
            }
            showQueue = false
        }
    }
    // Transition "peek" annulable Player ↔ Paroles : 0 = Player plein écran, 1 =
    // Paroles plein écran. Propriété d'AppNavigation (pas de PlayerScreen ni de
    // LyricsScreen) car c'est le seul endroit qui voit les deux écrans à la fois —
    // cf. Motion.kt / PlayerScreen.kt / LyricsScreen.kt pour le détail du contrat.
    val lyricsPeekProgress = remember { Animatable(0f) }
    // Monté paresseusement (premier tap "Paroles" OU premier pixel de drag), pour ne
    // jamais précharger/interroger le cache de paroles tant que l'utilisateur n'a
    // montré aucune intention — cf. contrainte "ne casse pas le cache paroles".
    var lyricsMounted by remember { mutableStateOf(false) }
    // Accumulateur de drag brut, indépendant de `lyricsPeekProgress` : permet de
    // connaître le point d'arrivée exact même quand `reducedMotion` empêche le
    // rendu intermédiaire (peek visuel désactivé, mais le geste doit rester utilisable).
    var pendingLyricsDragFraction by remember { mutableStateOf(0f) }
    val lyricsPeekCommitFraction = 0.38f
    var showLyricsWebSearch by remember { mutableStateOf(false) }
    var lyricsWebSearchDraft by remember { mutableStateOf<String?>(null) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showPlaybackSpeedSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showEditMetadata by remember { mutableStateOf(false) }
    var showCrossfadeSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showPlayerOptionsSheet by remember { mutableStateOf(false) }

    // Ouverture "discrète" (tap sur le bouton Paroles, pas un drag) : anime jusqu'au
    // bout avec la même courbe que l'ancien SgMotion.lyricsEnter (conservé pour
    // compat), mais via le progrès partagé plutôt qu'un AnimatedVisibility séparé —
    // pas de différence perceptible, mais réutilise le même mécanisme que le drag.
    fun openLyricsDiscrete() {
        lyricsMounted = true
        scope.launch {
            if (reducedMotion) lyricsPeekProgress.snapTo(1f)
            else lyricsPeekProgress.animateTo(1f, animationSpec = tween(SgMotion.SlowMs, easing = SgMotion.EmphasizedDecelerate))
        }
    }

    fun closeLyricsDiscrete() {
        scope.launch {
            if (reducedMotion) {
                lyricsPeekProgress.snapTo(0f)
            } else {
                lyricsPeekProgress.animateTo(0f, animationSpec = tween(SgMotion.MediumMs, easing = SgMotion.EmphasizedAccelerate))
            }
            if (lyricsPeekProgress.value <= 0.001f) lyricsMounted = false
        }
    }

    // Geste en cours (depuis PlayerScreen OU LyricsScreen, cf. leur callback commun) :
    // on part de la valeur COURANTE (pas de 0) pour que le drag reste continu même
    // s'il démarre en plein peek (ex. un premier swipe annulé, suivi d'un second).
    fun onLyricsPeekDragStart() {
        pendingLyricsDragFraction = lyricsPeekProgress.value
        lyricsMounted = true
    }

    fun onLyricsPeekDrag(deltaFraction: Float) {
        pendingLyricsDragFraction = (pendingLyricsDragFraction + deltaFraction).coerceIn(0f, 1f)
        if (!reducedMotion) {
            scope.launch { lyricsPeekProgress.snapTo(pendingLyricsDragFraction) }
        }
    }

    fun onLyricsPeekDragEnd() {
        val shouldOpen = pendingLyricsDragFraction >= lyricsPeekCommitFraction
        scope.launch {
            if (reducedMotion) {
                // "Skip peek, cut simple" : pas de spring, juste le résultat final.
                lyricsPeekProgress.snapTo(if (shouldOpen) 1f else 0f)
            } else {
                lyricsPeekProgress.animateTo(
                    if (shouldOpen) 1f else 0f,
                    animationSpec = SgMotion.SpringSoft
                )
            }
            if (lyricsPeekProgress.value <= 0.001f) lyricsMounted = false
        }
    }

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
            lyricsMounted = false
            lyricsPeekProgress.snapTo(0f)
            pendingLyricsDragFraction = 0f
            showLyricsWebSearch = false
            showQueue = false
            queueBannerProgress.snapTo(0f)
        }
    }

    // Shared element réel (cf. docs/FEATURES_C_SHARED_ELEMENT.md) : tout le contenu
    // (NavHost + mini-player overlay) vit dans le même SharedTransitionLayout, afin
    // que la pochette puisse morpher entre le mini-player et le Player plein écran
    // quel que soit le point d'entrée (overlay ci-dessous, ou celui intégré à
    // MainScreen — cf. LocalSharedTransitionScope, ambiant depuis ce niveau).
    SharedTransitionLayout {
    val sharedTransitionScope = this
    CompositionLocalProvider(LocalSharedTransitionScope provides sharedTransitionScope) {
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
                    // Fournit l'AnimatedContentScope de cette destination NavHost comme
                    // AnimatedVisibilityScope du shared element (cf. Motion.kt) : c'est ce
                    // scope, combiné à celui du mini-player (AnimatedVisibility ci-dessous
                    // ou dans MainScreen), qui permet à Modifier.sharedElement de savoir
                    // que la pochette "entre" ici et "sort" de l'autre côté.
                    CompositionLocalProvider(LocalSgAnimatedVisibilityScope provides this@composable) {
                    // Réduction du Player en bandeau pendant que la Queue est ouverte : léger
                    // scale-down + fade + léger décalage vers le haut (PAS le même morph que
                    // le dismiss vers le mini-player — volontairement plus discret, cf. "light
                    // dismiss/expand" demandé, pas un shrink façon Apple Music). L'alpha à 0
                    // rend en plus le Player non "visible" sous le bandeau/la Queue, qui sont
                    // dessinés après lui (donc au-dessus) et interceptent le toucher.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = queueBannerProgress.value
                                val scale = 1f - 0.06f * p
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - p
                                translationY = -0.05f * p * size.height
                            }
                    ) {
                        PlayerScreen(
                            song = song,
                            isPlaying = isPlaying,
                            accentColor = accentColor,
                            isFavorite = favoriteSongs.any { it.id == song.id },
                            onPlayPause = { viewModel.togglePlayPause() },
                            onClose = { navController.popBackStack() },
                            onSwipeDown = { navController.popBackStack() },
                            onSwipeUp = { openQueue() },
                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                            onOpenQueue = { openQueue() },
                            player = player,
                            onOpenPlayerOptions = { showPlayerOptionsSheet = true },
                            onOpenLyrics = { openLyricsDiscrete() },
                            lyricsPeekProgress = lyricsPeekProgress.value,
                            onLyricsPeekDragStart = { onLyricsPeekDragStart() },
                            onLyricsPeekDrag = { delta -> onLyricsPeekDrag(delta) },
                            onLyricsPeekDragEnd = { onLyricsPeekDragEnd() },
                            gaplessEnabled = gaplessEnabled,
                            crossfadeDurationMs = crossfadeDurationMs,
                            equalizerEnabled = equalizerEnabled,
                            equalizerPresetLabel = equalizerPreset.label,
                            playbackSpeed = playbackSpeed,
                            playbackPitch = playbackPitch,
                            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                            vinylModeEnabled = vinylModeEnabled
                        )
                    }
                    }
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
        // Enveloppé dans AnimatedVisibility (au lieu d'un simple if) pour deux raisons :
        // une transition d'apparition/disparition plus douce, et surtout pour exposer
        // l'AnimatedVisibilityScope requis par le shared element pochette (cf. Motion.kt).
        currentSong?.let { song ->
            AnimatedVisibility(
                visible = currentRoute != Routes.HOME && currentRoute != Routes.PLAYER,
                // Pas de fade/slide sur le conteneur : le morph shared element (pochette +
                // métadonnées) est la seule animation au pop Player → mini — un fadeEnter
                // en parallèle provoquait le "jump" de resynchronisation à la relâche.
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                CompositionLocalProvider(LocalSgAnimatedVisibilityScope provides this@AnimatedVisibility) {
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
                        gaplessEnabled = gaplessEnabled,
                        crossfadeDurationMs = crossfadeDurationMs
                    )
                }
            }
        }

        // Monté tant que l'anim d'ouverture/fermeture n'est pas terminée (comme
        // `lyricsMounted`) — pas seulement `showQueue`, sinon le bandeau/la Queue
        // disparaîtraient net à la fermeture au lieu de suivre `queueBannerProgress`
        // jusqu'à 0 (cf. "ré-expanse avec animation légère").
        if ((showQueue || queueBannerProgress.value > 0.001f) && currentRoute == Routes.PLAYER) {
            val song = currentSong
            Box(modifier = Modifier.fillMaxSize()) {
                // Bandeau Player réduit (~1/4 écran, cf. correction utilisateur) — remplace
                // le Player plein écran (rendu transparent juste au-dessus, cf. graphicsLayer
                // sur PlayerScreen) pendant que la Queue est ouverte.
                if (song != null) {
                    PlayerQueueBanner(
                        song = song,
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSkipPrevious = { viewModel.skipPrevious() },
                        onSkipNext = { viewModel.skipNext() },
                        onExpand = { closeQueue() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxHeight(0.25f)
                            .graphicsLayer { alpha = queueBannerProgress.value }
                    )
                }
                QueueScreen(
                    playlist = playbackQueue,
                    currentIndex = playbackQueueIndex,
                    isPlaying = isPlaying,
                    accentColor = accentColor,
                    onClose = { closeQueue() },
                    onPlaySong = { index -> viewModel.seekToQueueIndex(index) },
                    onRemoveSong = { index -> viewModel.removeFromPlaybackQueue(index) },
                    onMoveSong = { from, to -> viewModel.moveInPlaybackQueue(from, to) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            val p = queueBannerProgress.value
                            alpha = p
                            translationY = (1f - p) * size.height * 0.12f
                        }
                )
            }
        }

        // Paroles = écran plein écran "pair" du Player (pas un bottom sheet), affiché
        // dès qu'il est monté (`lyricsMounted`) — visible ou pas dépend uniquement de
        // `lyricsPeekProgress` (translationX appliqué dans LyricsScreen lui-même), plus
        // d'AnimatedVisibility séparé : le peek interactif a besoin des DEUX écrans
        // rendus en continu pendant le drag, pas d'un simple show/hide au relâchement.
        if (lyricsMounted && currentRoute == Routes.PLAYER) {
            val song = currentSong
            val player = controller
            if (song != null && player != null) {
                LyricsScreen(
                    song = song,
                    player = player,
                    playbackPosition = playbackPosition,
                    accentColor = accentColor,
                    onClose = { closeLyricsDiscrete() },
                    onOpenWebSearch = { showLyricsWebSearch = true },
                    pendingPasteText = lyricsWebSearchDraft,
                    onPendingPasteConsumed = { lyricsWebSearchDraft = null },
                    isPlaying = isPlaying,
                    onPlayPause = { viewModel.togglePlayPause() },
                    peekProgress = lyricsPeekProgress.value,
                    onPeekDragStart = { onLyricsPeekDragStart() },
                    onPeekDrag = { delta -> onLyricsPeekDrag(delta) },
                    onPeekDragEnd = { onLyricsPeekDragEnd() }
                )
            }
        }

        if (showLyricsWebSearch && currentRoute == Routes.PLAYER) {
            val song = currentSong
            if (song != null) {
                LyricsWebSearchScreen(
                    song = song,
                    accentColor = accentColor,
                    playbackPositionMs = playbackPosition,
                    isPlaying = isPlaying,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onClose = { showLyricsWebSearch = false },
                    onPasteFromClipboard = { text ->
                        showLyricsWebSearch = false
                        lyricsWebSearchDraft = text
                        openLyricsDiscrete()
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
                    onSetCoverArt = { currentSong?.let { launchCoverPicker(it) } },
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

        if (showEqualizerSheet && currentRoute == Routes.PLAYER) {
            EqualizerBottomSheet(
                enabled = equalizerEnabled,
                preset = equalizerPreset,
                bands = equalizerBands,
                accentColor = accentColor,
                onEnabledChange = { viewModel.setEqualizerEnabled(it) },
                onPresetSelected = { viewModel.setEqualizerPreset(it) },
                onBandLevelChange = { band, level -> viewModel.setEqualizerBandLevel(band, level) },
                onDismiss = { showEqualizerSheet = false }
            )
        }

        // Crossfade + minuterie de sommeil exposés directement depuis le Player
        // (req. 3) — jusqu'ici accessibles uniquement via Paramètres.
        if (showCrossfadeSheet && currentRoute == Routes.PLAYER) {
            CrossfadeBottomSheet(
                currentMs = crossfadeDurationMs,
                gaplessEnabled = gaplessEnabled,
                accentColor = accentColor,
                onDurationSelected = { viewModel.setCrossfadeDurationMs(it) },
                onDismiss = { showCrossfadeSheet = false }
            )
        }

        if (showSleepTimerSheet && currentRoute == Routes.PLAYER) {
            SleepTimerBottomSheet(
                accentColor = accentColor,
                onDismiss = { showSleepTimerSheet = false },
                onSelectMinutes = { viewModel.setSleepTimer(it) },
                onSelectEndOfTrack = { viewModel.setSleepTimerEndOfTrack() },
                onCancel = { viewModel.cancelSleepTimer() }
            )
        }

        if (showPlayerOptionsSheet && currentRoute == Routes.PLAYER) {
            currentSong?.let { song ->
                PlayerOptionsBottomSheet(
                    accentColor = accentColor,
                    gaplessEnabled = gaplessEnabled,
                    crossfadeDurationMs = crossfadeDurationMs,
                    sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                    playbackSpeed = playbackSpeed,
                    playbackPitch = playbackPitch,
                    equalizerEnabled = equalizerEnabled,
                    equalizerPresetLabel = equalizerPreset.label,
                    vinylModeEnabled = vinylModeEnabled,
                    onOpenCrossfade = { showCrossfadeSheet = true },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onOpenPlaybackSpeed = { showPlaybackSpeedSheet = true },
                    onOpenEqualizer = {
                        viewModel.refreshEqualizerBands()
                        showEqualizerSheet = true
                    },
                    onToggleVinylMode = { viewModel.toggleVinylMode() },
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
                    onDismiss = { showPlayerOptionsSheet = false }
                )
            }
        }
    }
    }
    }
}
