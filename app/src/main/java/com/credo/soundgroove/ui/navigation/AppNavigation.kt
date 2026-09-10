package com.credo.soundgroove.ui.navigation

// Contrat navigation + mini-player : docs/NAVIGATION_CONTRACT.md
// Visibilité mini-player : MiniPlayerVisibility (source unique de vérité)

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
import com.credo.soundgroove.ui.screens.CarModeScreen
import com.credo.soundgroove.queue.QueuePane
import com.credo.soundgroove.ui.screens.LyricsScreen
import com.credo.soundgroove.ui.screens.LyricsWebSearchScreen
import com.credo.soundgroove.ui.screens.PlayerScreen
import com.credo.soundgroove.ui.screens.PlayerQueueBanner
import com.credo.soundgroove.data.model.Playlist
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.credo.soundgroove.ui.components.AddToPlaylistSheet
import com.credo.soundgroove.ui.components.CreatePlaylistSheet
import com.credo.soundgroove.ui.components.MiniPlayer
import com.credo.soundgroove.ui.components.CrossfadeBottomSheet
import com.credo.soundgroove.ui.components.PlayerOptionsBottomSheet
import com.credo.soundgroove.ui.components.EditMetadataBottomSheet
import com.credo.soundgroove.ui.components.EqualizerBottomSheet
import com.credo.soundgroove.ui.components.PlaybackSpeedBottomSheet
import com.credo.soundgroove.ui.components.SleepTimerBottomSheet
import com.credo.soundgroove.ui.components.SongInfoBottomSheet
import com.credo.soundgroove.ui.components.rememberSongCoverArtPicker
import com.credo.soundgroove.ui.player.PlayerUiState
import com.credo.soundgroove.ui.screens.AlbumDetailScreen
import com.credo.soundgroove.ui.screens.ArtistDetailScreen
import com.credo.soundgroove.ui.screens.FolderDetailContent
import com.credo.soundgroove.ui.screens.PlaylistDetailScreen
import com.credo.soundgroove.ui.theme.LocalSgAnimatedVisibilityScope
import com.credo.soundgroove.ui.theme.LocalSgPerformanceMode
import com.credo.soundgroove.ui.theme.LocalSharedTransitionScope
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
import com.credo.soundgroove.viewmodel.SoundGrooveViewModel

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val CAR_MODE = "car_mode"
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
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isControllerConnecting by viewModel.isControllerConnecting.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val playbackDuration by viewModel.playbackDuration.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val songsWithLyrics by viewModel.songsWithLyrics.collectAsState()
    val controller by viewModel.mediaController.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val playbackPitch by viewModel.playbackPitch.collectAsState()
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsState()
    val crossfadeDurationMs by viewModel.crossfadeDurationMs.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val vinylModeEnabled by viewModel.vinylModeEnabled.collectAsState()
    val lyricsSyncOffsetMs by viewModel.lyricsSyncOffsetMs.collectAsState()
    val albumCoverAccentEnabled by viewModel.albumCoverAccentEnabled.collectAsState()
    val equalizerEnabled by viewModel.equalizerEnabled.collectAsState()
    val equalizerPreset by viewModel.equalizerPreset.collectAsState()
    val equalizerBands by viewModel.equalizerBands.collectAsState()
    val currentTrackEqPinned by viewModel.currentTrackEqPinned.collectAsState()
    val metadataEditMessage by viewModel.metadataEditMessage.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()
    val playbackQueueIndex by viewModel.playbackQueueIndex.collectAsState()
    val performanceModeEnabled by viewModel.performanceModeEnabled.collectAsState()
    val persistentMiniPlayerEnabled by viewModel.persistentMiniPlayerEnabled.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Fournit le flag prefs AVANT rememberSgReducedMotion (OR logique).
    CompositionLocalProvider(LocalSgPerformanceMode provides performanceModeEnabled) {
    AppNavigationContent(
        viewModel = viewModel,
        accentColor = accentColor,
        navController = navController,
        playlists = playlists,
        songs = songs,
        currentSong = currentSong,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        isControllerConnecting = isControllerConnecting,
        playbackPosition = playbackPosition,
        playbackDuration = playbackDuration,
        favoriteSongs = favoriteSongs,
        recentlyPlayed = recentlyPlayed,
        recentSearches = recentSearches,
        songsWithLyrics = songsWithLyrics,
        controller = controller,
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
        gaplessEnabled = gaplessEnabled,
        crossfadeDurationMs = crossfadeDurationMs,
        sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
        vinylModeEnabled = vinylModeEnabled,
        lyricsSyncOffsetMs = lyricsSyncOffsetMs,
        albumCoverAccentEnabled = albumCoverAccentEnabled,
        equalizerEnabled = equalizerEnabled,
        equalizerPreset = equalizerPreset,
        equalizerBands = equalizerBands,
        currentTrackEqPinned = currentTrackEqPinned,
        metadataEditMessage = metadataEditMessage,
        playbackQueue = playbackQueue,
        playbackQueueIndex = playbackQueueIndex,
        currentRoute = currentRoute,
        persistentMiniPlayerEnabled = persistentMiniPlayerEnabled,
        context = context,
        scope = scope
    )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppNavigationContent(
    viewModel: SoundGrooveViewModel,
    accentColor: Color,
    navController: androidx.navigation.NavHostController,
    playlists: List<Playlist>,
    songs: List<com.credo.soundgroove.data.model.Song>,
    currentSong: com.credo.soundgroove.data.model.Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isControllerConnecting: Boolean,
    playbackPosition: Long,
    playbackDuration: Long,
    favoriteSongs: List<com.credo.soundgroove.data.model.Song>,
    recentlyPlayed: List<com.credo.soundgroove.data.model.Song>,
    recentSearches: List<String>,
    songsWithLyrics: List<com.credo.soundgroove.data.model.Song>,
    controller: androidx.media3.session.MediaController?,
    playbackSpeed: Float,
    playbackPitch: Float,
    gaplessEnabled: Boolean,
    crossfadeDurationMs: Int,
    sleepTimerRemainingSeconds: Int?,
    vinylModeEnabled: Boolean,
    lyricsSyncOffsetMs: Long,
    albumCoverAccentEnabled: Boolean,
    equalizerEnabled: Boolean,
    equalizerPreset: com.credo.soundgroove.util.EqualizerPreset,
    equalizerBands: List<com.credo.soundgroove.util.EqualizerBandInfo>,
    currentTrackEqPinned: Boolean,
    metadataEditMessage: String?,
    playbackQueue: List<com.credo.soundgroove.data.model.Song>,
    playbackQueueIndex: Int,
    currentRoute: String?,
    persistentMiniPlayerEnabled: Boolean,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val reducedMotion = rememberSgReducedMotion()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    // Overlay Accueil « Récemment joué » : masque le mini sur HOME uniquement.
    var homeMiniPlayerSuppressed by remember { mutableStateOf(false) }

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
            else queueBannerProgress.animateTo(1f, animationSpec = SgMotion.queueSheetSpec())
        }
    }

    fun closeQueue() {
        scope.launch {
            if (reducedMotion) {
                queueBannerProgress.snapTo(0f)
            } else {
                queueBannerProgress.animateTo(0f, animationSpec = SgMotion.queueSheetSpec())
            }
            showQueue = false
        }
    }
    // Transition "peek" annulable Player ↔ Paroles : 0 = Player plein écran, 1 =
    // Paroles plein écran. Propriété d'AppNavigation (pas de PlayerScreen ni de
    // LyricsScreen) car c'est le seul endroit qui voit les deux écrans à la fois —
    // cf. Motion.kt / PlayerScreen.kt / LyricsScreen.kt pour le détail du contrat.
    val lyricsPeekProgress = remember { Animatable(0f) }
    // Monté paresseusement (premier tap aperçu paroles / a11y OU premier pixel de drag), pour ne
    // jamais précharger/interroger le cache de paroles tant que l'utilisateur n'a
    // montré aucune intention — cf. contrainte "ne casse pas le cache paroles".
    var lyricsMounted by remember { mutableStateOf(false) }
    // Accumulateur de drag brut, indépendant de `lyricsPeekProgress` : permet de
    // connaître le point d'arrivée exact même quand `reducedMotion` empêche le
    // rendu intermédiaire (peek visuel désactivé, mais le geste doit rester utilisable).
    var pendingLyricsDragFraction by remember { mutableStateOf(0f) }
    var lyricsBackAnchor by remember { mutableFloatStateOf(1f) }
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
    var searchInfoSong by remember { mutableStateOf<com.credo.soundgroove.data.model.Song?>(null) }
    var searchEditSong by remember { mutableStateOf<com.credo.soundgroove.data.model.Song?>(null) }
    // Picker playlist unifié (offline) — tue les fantômes Search / détail album-artiste-playlist-dossier.
    var songForPlaylistPicker by remember { mutableStateOf<com.credo.soundgroove.data.model.Song?>(null) }
    var pendingCreatePlaylistSong by remember { mutableStateOf<com.credo.soundgroove.data.model.Song?>(null) }
    var folderInfoSong by remember { mutableStateOf<com.credo.soundgroove.data.model.Song?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val playlistMessage by viewModel.playlistMessage.collectAsState()
    // Snapshot lecture pour réduire le drilling Player / Mini (sans rewrite NavHost).
    val playerUiState = remember(
        currentSong, isPlaying, isBuffering, isControllerConnecting,
        playbackPosition, playbackDuration, playbackQueue, playbackQueueIndex,
        shuffleEnabled, repeatMode, playbackSpeed, playbackPitch,
        gaplessEnabled, crossfadeDurationMs, sleepTimerRemainingSeconds,
        vinylModeEnabled, equalizerEnabled, equalizerPreset, equalizerBands,
        currentTrackEqPinned, lyricsSyncOffsetMs, albumCoverAccentEnabled, playbackError
    ) {
        PlayerUiState(
            currentSong = currentSong,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            isControllerConnecting = isControllerConnecting,
            playbackPosition = playbackPosition,
            playbackDuration = playbackDuration,
            playbackQueue = playbackQueue,
            playbackQueueIndex = playbackQueueIndex,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            gaplessEnabled = gaplessEnabled,
            crossfadeDurationMs = crossfadeDurationMs,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            vinylModeEnabled = vinylModeEnabled,
            equalizerEnabled = equalizerEnabled,
            equalizerPreset = equalizerPreset,
            equalizerBands = equalizerBands,
            currentTrackEqPinned = currentTrackEqPinned,
            lyricsSyncOffsetMs = lyricsSyncOffsetMs,
            albumCoverAccentEnabled = albumCoverAccentEnabled,
            playbackError = playbackError,
        )
    }

    // Ouverture "discrète" (tap aperçu paroles / a11y, pas un drag) : anime jusqu'au
    // bout avec la même courbe que l'ancien SgMotion.lyricsEnter (conservé pour
    // compat), mais via le progrès partagé plutôt qu'un AnimatedVisibility séparé —
    // pas de différence perceptible, mais réutilise le même mécanisme que le drag.
    fun openLyricsDiscrete() {
        lyricsMounted = true
        scope.launch {
            if (reducedMotion) lyricsPeekProgress.snapTo(1f)
            // Aligné lyricsEnter : slide H SlowMs (porteur) — peek progress unique.
            else lyricsPeekProgress.animateTo(1f, animationSpec = tween(SgMotion.SlowMs, easing = SgMotion.EmphasizedDecelerate))
        }
    }

    fun closeLyricsDiscrete() {
        scope.launch {
            if (reducedMotion) {
                lyricsPeekProgress.snapTo(0f)
            } else {
                lyricsPeekProgress.animateTo(0f, animationSpec = tween(SgMotion.FastMs, easing = SgMotion.EmphasizedAccelerate))
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
                    animationSpec = SgMotion.SpringSnappy
                )
            }
            if (lyricsPeekProgress.value <= 0.001f) lyricsMounted = false
        }
    }

    suspend fun applyLyricsPredictiveBack(rawProgress: Float) {
        val p = rawProgress.coerceIn(0f, 1f)
        if (p <= 0.01f) lyricsBackAnchor = lyricsPeekProgress.value.coerceAtLeast(0.001f)
        lyricsPeekProgress.snapTo((lyricsBackAnchor * (1f - p)).coerceIn(0f, 1f))
    }

    suspend fun cancelLyricsPredictiveBack() {
        if (reducedMotion) {
            lyricsPeekProgress.snapTo(lyricsBackAnchor)
        } else {
            lyricsPeekProgress.animateTo(lyricsBackAnchor, animationSpec = SgMotion.SpringSnappy)
        }
    }

    fun lyricsPredictiveBackProgress(): Float {
        val anchor = lyricsBackAnchor.coerceAtLeast(0.001f)
        return (1f - lyricsPeekProgress.value / anchor).coerceIn(0f, 1f)
    }

    suspend fun applyQueuePredictiveBack(rawProgress: Float) {
        val p = rawProgress.coerceIn(0f, 1f)
        queueBannerProgress.snapTo((1f - p).coerceIn(0f, 1f))
    }

    suspend fun cancelQueuePredictiveBack() {
        if (reducedMotion) {
            queueBannerProgress.snapTo(1f)
        } else {
            queueBannerProgress.animateTo(1f, animationSpec = SgMotion.queueSheetSpec())
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

    LaunchedEffect(playbackError) {
        playbackError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearPlaybackError()
        }
    }

    LaunchedEffect(playlistMessage) {
        playlistMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearPlaylistMessage()
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
    // quel que soit le point d'entrée — cf. LocalSharedTransitionScope, ambiant depuis
    // ce niveau ; mini-player unique via MiniPlayerVisibility.
    SharedTransitionLayout {
    val sharedTransitionScope = this
    CompositionLocalProvider(LocalSharedTransitionScope provides sharedTransitionScope) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.navForwardEnter()
            },
            exitTransition = {
                if (reducedMotion) SgMotion.navSnapExit() else SgMotion.navForwardExit()
            },
            popEnterTransition = {
                if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.navPopEnter()
            },
            popExitTransition = {
                if (reducedMotion) SgMotion.navSnapExit() else SgMotion.navPopExit()
            }
        ) {
            composable(
                route = Routes.HOME,
                // Vers album/artiste : fade (pas de slide H) — le sharedBounds porte le morph.
                exitTransition = {
                    val target = targetState.destination.route
                    when {
                        reducedMotion -> SgMotion.navSnapExit()
                        target == Routes.ALBUM_DETAIL || target == Routes.ARTIST_DETAIL ->
                            SgMotion.fadeExit()
                        else -> SgMotion.navForwardExit()
                    }
                },
                popEnterTransition = {
                    val initial = initialState.destination.route
                    when {
                        reducedMotion -> SgMotion.navSnapEnter()
                        initial == Routes.ALBUM_DETAIL || initial == Routes.ARTIST_DETAIL ->
                            SgMotion.fadeEnter()
                        else -> SgMotion.navPopEnter()
                    }
                }
            ) {
                CompositionLocalProvider(LocalSgAnimatedVisibilityScope provides this@composable) {
                LegacyMainHost(
                    viewModel = viewModel,
                    accentColor = accentColor,
                    onNavigateToPlaylist = { playlistId ->
                        navController.navigate(Routes.playlistDetail(playlistId))
                    },
                    onNavigateToSearch = {
                        viewModel.updateMainSelectedTab(2)
                        navController.navigate(Routes.SEARCH)
                    },
                    onNavigateToAlbum = { albumName ->
                        navController.navigate(Routes.albumDetail(albumName))
                    },
                    onNavigateToArtist = { artistName ->
                        navController.navigate(Routes.artistDetail(artistName))
                    },
                    onNavigateToPlayer = {
                        showQueue = false
                        scope.launch { queueBannerProgress.snapTo(0f) }
                        navController.navigate(Routes.PLAYER)
                    },
                    onNavigateToCarMode = {
                        navController.navigate(Routes.CAR_MODE)
                    },
                    onHomeMiniPlayerSuppressedChange = { homeMiniPlayerSuppressed = it },
                    onOpenSleepTimerSheet = { showSleepTimerSheet = true },
                    onOpenPlaybackSpeedSheet = { showPlaybackSpeedSheet = true },
                    onOpenCrossfadeSheet = { showCrossfadeSheet = true },
                    onOpenEqualizerSheet = {
                        viewModel.refreshEqualizerBands()
                        showEqualizerSheet = true
                    }
                )
                }
            }

            composable(
                route = Routes.SEARCH,
                enterTransition = {
                    if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.fadeEnter()
                },
                // Vers album/artiste : fade (déjà le défaut SEARCH) — sharedBounds porte le morph.
                exitTransition = {
                    val target = targetState.destination.route
                    when {
                        reducedMotion -> SgMotion.navSnapExit()
                        target == Routes.ALBUM_DETAIL || target == Routes.ARTIST_DETAIL ->
                            SgMotion.fadeExit()
                        else -> SgMotion.fadeExit()
                    }
                },
                popEnterTransition = {
                    // Retour depuis album/artiste : fade (symétrique HOME) pour le sharedBounds.
                    val initial = initialState.destination.route
                    when {
                        reducedMotion -> SgMotion.navSnapEnter()
                        initial == Routes.ALBUM_DETAIL || initial == Routes.ARTIST_DETAIL ->
                            SgMotion.fadeEnter()
                        else -> SgMotion.fadeEnter()
                    }
                },
                popExitTransition = {
                    if (reducedMotion) SgMotion.navSnapExit() else SgMotion.fadeExit()
                }
            ) {
                CompositionLocalProvider(LocalSgAnimatedVisibilityScope provides this@composable) {
                SgPredictivePopHost(onBack = { navController.popBackStack() }) {
                com.credo.soundgroove.ui.screens.SearchScreen(
                    allSongs = songs,
                    playlists = playlists,
                    favoriteSongs = favoriteSongs,
                    songsWithLyrics = songsWithLyrics,
                    currentSong = currentSong,
                    accentColor = accentColor,
                    recentSearches = recentSearches,
                    onBack = {
                        navController.popBackStack()
                        if (viewModel.mainSelectedTab.value == 2) {
                            viewModel.updateMainSelectedTab(0)
                        }
                    },
                    onPlaySong = { song, queue ->
                        viewModel.playSongs(queue, song)
                        navController.navigate(Routes.PLAYER)
                    },
                    onAlbumClick = { albumName -> navController.navigate(Routes.albumDetail(albumName)) },
                    onArtistClick = { artistName -> navController.navigate(Routes.artistDetail(artistName)) },
                    onPlaylistClick = { playlistId -> navController.navigate(Routes.playlistDetail(playlistId)) },
                    onFolderClick = { folderPath -> navController.navigate(Routes.folderDetail(folderPath)) },
                    onMenuClick = { /* SongContextMenuSheet ouvert via menuSong interne */ },
                    onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                    onPlayNext = { song -> viewModel.playNext(song) },
                    onAddToQueue = { song -> viewModel.addToQueue(song) },
                    onAddToPlaylist = { song -> songForPlaylistPicker = song },
                    onViewSongInfo = { song -> searchInfoSong = song },
                    onShareCard = { song ->
                        com.credo.soundgroove.util.PlayerActions.shareSongCard(
                            context,
                            song,
                            accentColor.hashCode()
                        )
                    },
                    onEditMetadata = { song -> searchEditSong = song },
                    onSearchSubmitted = { viewModel.addRecentSearch(it) },
                    onClearSearchHistory = { viewModel.clearSearchHistory() }
                )
                }
                }
            }

            composable(
                route = Routes.CAR_MODE,
                enterTransition = {
                    if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.fadeEnter()
                },
                exitTransition = {
                    if (reducedMotion) SgMotion.navSnapExit() else SgMotion.fadeExit()
                },
                popEnterTransition = {
                    if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.fadeEnter()
                },
                popExitTransition = {
                    if (reducedMotion) SgMotion.navSnapExit() else SgMotion.fadeExit()
                },
            ) {
                CarModeScreen(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    favoriteSongs = favoriteSongs,
                    recentlyPlayed = recentlyPlayed,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onSkipPrevious = { viewModel.skipPrevious() },
                    onSkipNext = { viewModel.skipNext() },
                    onPlaySong = { song, queue -> viewModel.playSongs(queue, song) },
                    onExit = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.PLAYER,
                // Mêmes courbes que l'overlay Player interne (MainScreen) : le lecteur doit
                // s'animer à l'identique quelle que soit la façon dont on y accède, sinon on
                // perçoit un "saut" en changeant de point d'entrée.
                // playerEnter = fade FastMs only (pas de slide) — shared art porte le morph.
                enterTransition = {
                    if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.playerEnter()
                },
                exitTransition = {
                    if (reducedMotion) SgMotion.navSnapExit() else SgMotion.playerExit()
                },
                popEnterTransition = {
                    if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.playerPopEnter()
                },
                popExitTransition = {
                    if (reducedMotion) SgMotion.navSnapExit() else SgMotion.playerPopExit()
                }
            ) {
                val song = currentSong
                val player = controller
                if (song != null && player != null) {
                    // Fournit l'AnimatedContentScope de cette destination NavHost comme
                    // AnimatedVisibilityScope du shared element (cf. Motion.kt) : c'est ce
                    // scope, combiné à celui du mini-player global (overlay AppNavigation),
                    // qui permet à Modifier.sharedElement de morpher la pochette.
                    CompositionLocalProvider(LocalSgAnimatedVisibilityScope provides this@composable) {
                    val queueMorph = queueBannerProgress.value
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (queueMorph > 0.02f) {
                            PlayerQueueBanner(
                                song = song,
                                isPlaying = isPlaying,
                                accentColor = accentColor,
                                onPlayPause = { viewModel.togglePlayPause() },
                                onSkipPrevious = { viewModel.skipPrevious() },
                                onSkipNext = { viewModel.skipNext() },
                                onExpand = { closeQueue() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.22f + queueMorph * 0.03f),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                PlayerScreen(
                                    song = song,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering,
                                    isControllerConnecting = isControllerConnecting,
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
                                    vinylModeEnabled = vinylModeEnabled,
                                    lyricsSyncOffsetMs = lyricsSyncOffsetMs,
                                    albumCoverAccentEnabled = albumCoverAccentEnabled,
                                    queueOpen = false,
                                    shuffleEnabled = shuffleEnabled,
                                    repeatMode = repeatMode,
                                    onSkipNext = { viewModel.skipNext() },
                                    onSkipPrevious = { viewModel.skipPrevious() },
                                    onToggleShuffle = { viewModel.toggleShuffle() },
                                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                                )
                            }
                        }
                        if (queueMorph > 0.02f) {
                            QueuePane(
                                playlist = playbackQueue,
                                currentIndex = playbackQueueIndex,
                                isPlaying = isPlaying,
                                accentColor = accentColor,
                                morphProgress = queueMorph,
                                playbackPositionMs = playbackPosition,
                                onClose = { closeQueue() },
                                onPlaySong = { index -> viewModel.seekToQueueIndex(index) },
                                onRemoveSong = { index -> viewModel.removeFromPlaybackQueue(index) },
                                onMoveSong = { from, to -> viewModel.moveInPlaybackQueue(from, to) },
                                onClearUpcoming = { viewModel.clearUpcoming() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight((0.22f + queueMorph * 0.78f).coerceIn(0.22f, 1f)),
                            )
                        }
                    }
                    }
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }

            composable(Routes.PLAYLIST_DETAIL) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull()
                val playlist = playlists.find { it.id == playlistId }

                if (playlist == null) {
                    // Playlist absente (supprimée / id invalide) : ne jamais laisser un
                    // composable vide (= écran blanc) sur cette route.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(com.credo.soundgroove.ui.theme.GraphiteAbyss)
                    )
                    LaunchedEffect(playlistId) {
                        navController.popBackStack()
                    }
                } else {
                    SgPredictivePopHost(onBack = { navController.popBackStack() }) {
                    PlaylistDetailScreen(
                        playlist = playlist,
                        librarySongs = songs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        favoriteSongs = favoriteSongs,
                        accentColor = accentColor,
                        onBack = { navController.popBackStack() },
                        onPlaySong = { song -> viewModel.playPlaylist(playlist, song) },
                        onShufflePlay = {
                            val shuffled = playlist.copy(songs = playlist.songs.shuffled())
                            viewModel.playPlaylist(shuffled)
                        },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                        onRemoveSongFromPlaylist = { songId ->
                            viewModel.removeSongFromPlaylist(playlist.id, songId)
                        },
                        onDeletePlaylist = {
                            // Pop d'abord pour quitter le détail avant que le Flow
                            // n'enlève la playlist (évite race écran blanc).
                            navController.popBackStack()
                            viewModel.deletePlaylist(playlist.id)
                        },
                        onRenamePlaylist = { newName -> viewModel.renamePlaylist(playlist.id, newName) },
                        onAddSongsToPlaylist = { selected ->
                            viewModel.addSongsToPlaylist(
                                playlist.id,
                                selected,
                                startPosition = playlist.songs.size
                            )
                        },
                        onPlayNext = { song -> viewModel.playNext(song) },
                        onAddToQueue = { song -> viewModel.addToQueue(song) },
                        onAddToPlaylist = { song -> songForPlaylistPicker = song },
                        onSaveMetadata = { song, title, artist, album ->
                            viewModel.saveSongMetadata(song, title, artist, album)
                        },
                        onSetCoverArt = { song, uri -> viewModel.saveSongCoverArt(song, uri) }
                    )
                    }
                }
            }

            composable(Routes.FOLDER_DETAIL) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("folderPath") ?: ""
                val folderPath = Uri.decode(encodedPath)
                val folderSongs = songs.filter { song ->
                    (song.folderPath.takeIf { it.isNotBlank() } ?: "Dossier inconnu") == folderPath
                }

                SgPredictivePopHost(
                    onBack = { navController.popBackStack() },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(com.credo.soundgroove.ui.theme.GraphiteAbyss)
                        .statusBarsPadding()
                        .navigationBarsPadding()
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
                        onShowSongInfo = { song -> folderInfoSong = song },
                        onShowPlaylistPicker = { song -> songForPlaylistPicker = song },
                        onSetCoverArt = { song, uri -> viewModel.saveSongCoverArt(song, uri) }
                    )
                }
            }

            composable(
                route = Routes.ALBUM_DETAIL,
                // Fade only — sharedBounds grille → hero porte le morph (évite double slide H).
                enterTransition = {
                    if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.fadeEnter()
                },
                exitTransition = {
                    if (reducedMotion) SgMotion.navSnapExit() else SgMotion.fadeExit()
                },
                popEnterTransition = {
                    if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.fadeEnter()
                },
                popExitTransition = {
                    if (reducedMotion) SgMotion.navSnapExit() else SgMotion.fadeExit()
                }
            ) { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("albumName") ?: ""
                val albumName = Uri.decode(encodedName)
                val albumSongs = songs.filter { it.albumName == albumName }

                CompositionLocalProvider(LocalSgAnimatedVisibilityScope provides this@composable) {
                SgPredictivePopHost(onBack = { navController.popBackStack() }) {
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
                    onAddToPlaylist = { song -> songForPlaylistPicker = song },
                    onSaveMetadata = { song, title, artist, album ->
                        viewModel.saveSongMetadata(song, title, artist, album)
                    },
                    onSetCoverArt = { song, uri -> viewModel.saveSongCoverArt(song, uri) }
                )
                }
                }
            }

            composable(
                route = Routes.ARTIST_DETAIL,
                enterTransition = {
                    if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.fadeEnter()
                },
                exitTransition = {
                    if (reducedMotion) SgMotion.navSnapExit() else SgMotion.fadeExit()
                },
                popEnterTransition = {
                    if (reducedMotion) SgMotion.navSnapEnter() else SgMotion.fadeEnter()
                },
                popExitTransition = {
                    if (reducedMotion) SgMotion.navSnapExit() else SgMotion.fadeExit()
                }
            ) { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("artistName") ?: ""
                val artistName = Uri.decode(encodedName)
                val artistSongs = songs.filter { it.artist == artistName }

                CompositionLocalProvider(LocalSgAnimatedVisibilityScope provides this@composable) {
                SgPredictivePopHost(onBack = { navController.popBackStack() }) {
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
                    onAddToPlaylist = { song -> songForPlaylistPicker = song },
                    onSaveMetadata = { song, title, artist, album ->
                        viewModel.saveSongMetadata(song, title, artist, album)
                    },
                    onSetCoverArt = { song, uri -> viewModel.saveSongCoverArt(song, uri) }
                )
                }
                }
            }
        }

        // Mini-player global unique — cf. MiniPlayerVisibility + docs/NAVIGATION_CONTRACT.md
        // navigationBarsPadding() = inset réel (3 boutons / gestes) ; bottomPadding = chrome app.
        currentSong?.let { song ->
            val miniVisible = MiniPlayerVisibility.shouldShow(
                currentRoute = currentRoute,
                hasCurrentSong = true,
                persistentEnabled = persistentMiniPlayerEnabled,
                homeSuppressed = homeMiniPlayerSuppressed,
            )
            AnimatedVisibility(
                visible = miniVisible,
                // Pas de fade/slide conteneur : morph shared element pochette (Motion.kt).
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = MiniPlayerVisibility.bottomPadding(currentRoute))
            ) {
                CompositionLocalProvider(LocalSgAnimatedVisibilityScope provides this@AnimatedVisibility) {
                    val duration = playbackDuration.takeIf { it > 0L }
                        ?: song.duration.takeIf { it > 0L }
                        ?: 1L
                    MiniPlayer(
                        song = song,
                        isPlaying = playerUiState.isPlaying,
                        isBuffering = playerUiState.isBuffering,
                        isControllerConnecting = playerUiState.isControllerConnecting,
                        progress = (playerUiState.playbackPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                        accentColor = accentColor,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSkipPrevious = { viewModel.skipPrevious() },
                        onSkipNext = { viewModel.skipNext() },
                        onOpen = { navController.navigate(Routes.PLAYER) },
                        gaplessEnabled = playerUiState.gaplessEnabled,
                        crossfadeDurationMs = playerUiState.crossfadeDurationMs,
                        albumCoverAccentEnabled = playerUiState.albumCoverAccentEnabled,
                    )
                }
            }
        }

        // Back handler queue intégré dans la route Player (Column morph).
        if ((showQueue || queueBannerProgress.value > 0.001f) && currentRoute == Routes.PLAYER) {
            SgPredictiveBackHandler(
                enabled = true,
                reducedMotion = reducedMotion,
                currentProgress = { (1f - queueBannerProgress.value).coerceIn(0f, 1f) },
                onApplyProgress = { applyQueuePredictiveBack(it) },
                onCancelProgress = { cancelQueuePredictiveBack() },
                onBackCommitted = {
                    if (it && queueBannerProgress.value <= (1f - lyricsPeekCommitFraction)) {
                        scope.launch {
                            queueBannerProgress.snapTo(0f)
                            showQueue = false
                        }
                    } else {
                        closeQueue()
                    }
                },
            )
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
                    lyricsSyncOffsetMs = lyricsSyncOffsetMs,
                    peekProgress = lyricsPeekProgress.value,
                    onPeekDragStart = { onLyricsPeekDragStart() },
                    onPeekDrag = { delta -> onLyricsPeekDrag(delta) },
                    onPeekDragEnd = { onLyricsPeekDragEnd() },
                    onPredictiveBackApply = { applyLyricsPredictiveBack(it) },
                    onPredictiveBackCancel = { cancelLyricsPredictiveBack() },
                    predictiveBackProgress = { lyricsPredictiveBackProgress() },
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

        searchInfoSong?.let { song ->
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
                onEditMetadata = {
                    searchInfoSong = null
                    searchEditSong = song
                },
                onSetCoverArt = {
                    searchInfoSong = null
                    launchCoverPicker(song)
                },
                onSetRingtone = { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, song) },
                onDismiss = { searchInfoSong = null }
            )
        }

        searchEditSong?.let { song ->
            EditMetadataBottomSheet(
                song = song,
                accentColor = accentColor,
                onSave = { title, artist, album ->
                    viewModel.saveSongMetadata(song, title, artist, album)
                    searchEditSong = null
                },
                onSetCoverArt = { launchCoverPicker(song) },
                onDismiss = { searchEditSong = null }
            )
        }

        if (showPlaybackSpeedSheet) {
            PlaybackSpeedBottomSheet(
                currentSpeed = playbackSpeed,
                currentPitch = playbackPitch,
                accentColor = accentColor,
                onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                onPitchSelected = { viewModel.setPlaybackPitch(it) },
                onDismiss = { showPlaybackSpeedSheet = false }
            )
        }

        if (showEqualizerSheet) {
            val trackId = currentSong?.id ?: 0L
            EqualizerBottomSheet(
                enabled = equalizerEnabled,
                preset = equalizerPreset,
                bands = equalizerBands,
                accentColor = accentColor,
                onEnabledChange = { viewModel.setEqualizerEnabled(it) },
                onPresetSelected = { viewModel.setEqualizerPreset(it, forCurrentTrack = false) },
                onBandLevelChange = { band, level ->
                    viewModel.setEqualizerBandLevel(
                        band,
                        level,
                        forCurrentTrack = currentTrackEqPinned
                    )
                },
                onDismiss = { showEqualizerSheet = false },
                hasCurrentTrack = trackId != 0L,
                pinForCurrentTrack = currentTrackEqPinned,
                onPinForCurrentTrackChange = { pinned ->
                    if (!pinned && trackId != 0L) {
                        viewModel.clearTrackEqualizerPreset(trackId)
                    }
                },
                onPresetSelectedForTrack = { preset ->
                    viewModel.setEqualizerPreset(preset, forCurrentTrack = true)
                }
            )
        }

        // Crossfade + minuterie de sommeil exposés directement depuis le Player
        // (req. 3) — jusqu'ici accessibles uniquement via Paramètres.
        if (showCrossfadeSheet) {
            CrossfadeBottomSheet(
                currentMs = crossfadeDurationMs,
                gaplessEnabled = gaplessEnabled,
                accentColor = accentColor,
                onDurationSelected = { viewModel.setCrossfadeDurationMs(it) },
                onDismiss = { showCrossfadeSheet = false }
            )
        }

        if (showSleepTimerSheet) {
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
                    lyricsSyncOffsetMs = lyricsSyncOffsetMs,
                    onLyricsSyncOffsetChange = { viewModel.setLyricsSyncOffsetMs(it) },
                    onOpenCrossfade = { showCrossfadeSheet = true },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onOpenPlaybackSpeed = { showPlaybackSpeedSheet = true },
                    onOpenEqualizer = {
                        viewModel.refreshEqualizerBands()
                        showEqualizerSheet = true
                    },
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
                    onOpenLyrics = { openLyricsDiscrete() },
                    onDismiss = { showPlayerOptionsSheet = false }
                )
            }
        }

        songForPlaylistPicker?.let { song ->
            AddToPlaylistSheet(
                song = song,
                playlists = playlists,
                onAddToPlaylist = { playlist ->
                    viewModel.addSongToPlaylist(playlist.id, song, playlist.songs.size)
                    songForPlaylistPicker = null
                },
                onCreateAndAdd = {
                    pendingCreatePlaylistSong = song
                    songForPlaylistPicker = null
                },
                onDismiss = { songForPlaylistPicker = null }
            )
        }

        pendingCreatePlaylistSong?.let { song ->
            CreatePlaylistSheet(
                onDismiss = { pendingCreatePlaylistSong = null },
                onCreate = { name ->
                    viewModel.createPlaylist(name) { playlistId ->
                        viewModel.addSongToPlaylist(playlistId, song, 0)
                    }
                    pendingCreatePlaylistSong = null
                }
            )
        }

        folderInfoSong?.let { song ->
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
                onEditMetadata = {
                    folderInfoSong = null
                    searchEditSong = song
                },
                onSetCoverArt = {
                    folderInfoSong = null
                    launchCoverPicker(song)
                },
                onSetRingtone = { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, song) },
                onDismiss = { folderInfoSong = null }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
        )
    }
    }
    }
}
