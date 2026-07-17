package com.credo.soundgroove.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.SgSeekBar
import com.credo.soundgroove.ui.components.formatDuration
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.util.PlaybackPreferences
import com.credo.soundgroove.util.PlayerGuards
import com.credo.soundgroove.util.blendWithAlbumArt
import com.credo.soundgroove.util.displayArtist
import com.credo.soundgroove.util.displayTitle
import com.credo.soundgroove.util.ensureContrast
import com.credo.soundgroove.util.rememberAlbumArtAccentColor
import com.credo.soundgroove.util.rememberAlbumArtRolePalette
import com.credo.soundgroove.util.rememberPlayerAmbiencePalette
import kotlinx.coroutines.launch

/** Interpolation linéaire simple — évite une dépendance à `androidx.compose.ui.util.lerp`. */
private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

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
    onOpenPlayerOptions: () -> Unit = {},
    onOpenLyrics: () -> Unit = {},
    // Transition "peek" annulable Player ↔ Paroles (cf. AppNavigation, propriétaire
    // du progrès partagé) : 0 = Player plein, 1 = Paroles plein. PlayerScreen ne fait
    // que rapporter le geste brut (delta de drag normalisé par la largeur d'écran) et
    // afficher le résultat (translationX) — toute la logique de seuil/cancel/spring
    // vit côté parent, pour rester la source de vérité unique du progrès partagé
    // avec LyricsScreen.
    lyricsPeekProgress: Float = 0f,
    onLyricsPeekDragStart: () -> Unit = {},
    onLyricsPeekDrag: (Float) -> Unit = {},
    onLyricsPeekDragEnd: () -> Unit = {},
    gaplessEnabled: Boolean = true,
    crossfadeDurationMs: Int = 0,
    equalizerEnabled: Boolean = true,
    equalizerPresetLabel: String = "Normal",
    playbackSpeed: Float = 1f,
    playbackPitch: Float = 1f,
    sleepTimerRemainingSeconds: Int? = null,
    vinylModeEnabled: Boolean = false,
    albumCoverAccentEnabled: Boolean = false,
) {
    val surfaceBg = MaterialTheme.colorScheme.background
    val rolePalette = rememberAlbumArtRolePalette(
        albumArtUri = song.albumArtUri,
        defaultColor = accentColor,
        surfaceBackground = surfaceBg,
        isLightTheme = IsLightTheme,
    )
    val albumAccent = rolePalette.primary
    val displayAccent = if (albumCoverAccentEnabled) {
        accentColor
    } else {
        blendWithAlbumArt(accentColor, albumAccent)
    }
    val displaySecondaryAccent = if (albumCoverAccentEnabled) {
        rolePalette.secondary
    } else {
        themeSecondaryAccent(displayAccent)
    }
    val fileActionColor = if (albumCoverAccentEnabled) {
        rolePalette.onSurface
    } else {
        TextPrimary
    }
    val lyricsActionColor = ensureContrast(displayAccent, surfaceBg, minRatio = 4.5f)
    val secondaryAccent = themeSecondaryAccent(accentColor)
    // Ambiance dérivée de la pochette pour la couche d'assombrissement du fond :
    // contrairement à Paroles (fond toujours sombre), le Player garde le fond du
    // thème actif et adapte seulement la teinte du scrim — en thème clair, la
    // pochette ne fonce jamais le fond (texte sombre = illisible sinon), elle
    // l'éclaircit et le teinte légèrement (cf. AlbumArtPalette.rememberPlayerAmbiencePalette).
    val ambiencePalette = rememberPlayerAmbiencePalette(
        albumArtUri = song.albumArtUri,
        fallbackAccent = accentColor,
        isLightTheme = IsLightTheme,
        themeBackground = MaterialTheme.colorScheme.background
    )
    var progress by remember { mutableStateOf(0f) }
    var isShuffled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0L) }
    var currentPosition by remember { mutableStateOf(0L) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var verticalDragOffset by remember { mutableStateOf(0f) }
    var isDismissDragging by remember { mutableStateOf(false) }
    // Progrès 0→1 du morph interactif Player → mini (suivi doigt). Quand > 0, le
    // shared element est temporairement désactivé sur la pochette/métas pour laisser
    // le morph manuel piloter taille+position sans conflit de bounds au pop.
    var dismissMorphProgress by remember { mutableFloatStateOf(0f) }
    var screenWidthPx by remember { mutableStateOf(1f) }
    val swipeThreshold = 100.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Distance de drag vertical correspondant à un dismiss "complet" (100%) vers le
    // mini-player, et fraction de cette distance à partir de laquelle on considère
    // le dismiss "engagé" (sinon → annulation/spring-back). Volontairement plus
    // courte que la hauteur d'écran : comme iOS, on n'exige pas un drag pleine
    // hauteur pour valider le geste.
    val dismissDragDistancePx = with(density) { 260.dp.toPx() }
    val dismissCommitFraction = 0.42f
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    // Vrais insets système (pas une estimation) : la marge de navigation varie
    // beaucoup selon le mode (gestes vs barre 3 boutons), donc indispensable pour
    // viser la vraie position du mini-player en bas d'écran (cf. runExitAnimation).
    val navBarBottomPx = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .getBottom(density)

    // Morph d'ouverture / fermeture inspiré de la transition "Now Playing" d'Apple Music :
    // la pochette part de la taille mini-player (scale ~0.14) et remonte vers le centre ;
    // le chrome apparaît / disparaît en fondu décalé. Symétrique à la fermeture.
    val artScale = remember { Animatable(SgMotion.PlayerArtMiniScale) }
    val artOffsetX = remember { Animatable(0f) }
    val artOffsetY = remember { Animatable(SgMotion.PlayerArtEnterOffsetY) }
    val chromeAlpha = remember { Animatable(0f) }
    val dismissRootOffsetY = remember { Animatable(0f) }
    var isExiting by remember { mutableStateOf(false) }
    val reducedMotion = rememberSgReducedMotion()

    // Fondu des "lampes" (scrim ambiance) : évite le snap brutal quand Palette
    // Coil renvoie les couleurs extraites de la pochette.
    val scrimColorSpec = if (reducedMotion) snap() else SgMotion.tweenFastOf<Color>()
    val scrimTop by animateColorAsState(
        targetValue = ambiencePalette.scrimTop,
        animationSpec = scrimColorSpec,
        label = "playerScrimTop"
    )
    val scrimMid by animateColorAsState(
        targetValue = ambiencePalette.scrimMid,
        animationSpec = scrimColorSpec,
        label = "playerScrimMid"
    )
    val scrimBottom by animateColorAsState(
        targetValue = ambiencePalette.scrimBottom,
        animationSpec = scrimColorSpec,
        label = "playerScrimBottom"
    )

    // Position "au repos" de la pochette (centre, coordonnées racine), mesurée en
    // continu via onGloballyPositioned — voir le Box de la pochette plus bas, où
    // ce modifier est placé AVANT `.offset{}` dans la chaîne : Compose positionne
    // ce nœud d'après le parent (Column) sans tenir compte du décalage que NOUS
    // appliquons nous-mêmes plus loin dans la même chaîne, donc cette valeur reste
    // stable pendant toute l'anim (contrairement à `positionInRoot()` lu après
    // `.offset{}`, qui inclurait notre propre décalage en cours d'anim).
    var artRestCenterPx by remember { mutableStateOf<Offset?>(null) }

    // Un vrai SharedTransitionLayout (Modifier.sharedElement, cf. sgSharedAlbumArt)
    // est branché par AppNavigation/MainScreen : dans ce cas c'est lui qui pilote la
    // taille/position de la pochette pendant le morph, donc on n'anime plus
    // artScale/artOffsetY nous-mêmes (double animation = à-coups). Seul le fondu du
    // "chrome" reste géré ici. Sans ce contexte (ex. preview), on retombe sur
    // l'ancien morph manuel scale+position.
    val hasRealSharedTransition = rememberSgSharedElementActive()

    suspend fun runEnterAnimation() {
        if (reducedMotion) {
            artScale.snapTo(1f)
            artOffsetY.snapTo(0f)
            dismissRootOffsetY.snapTo(0f)
            chromeAlpha.snapTo(1f)
            return
        }
        if (hasRealSharedTransition) {
            artScale.snapTo(1f)
            artOffsetY.snapTo(0f)
            dismissRootOffsetY.snapTo(0f)
            chromeAlpha.animateTo(1f, animationSpec = SgMotion.playerChromeEnterSpec())
            return
        }
        kotlinx.coroutines.coroutineScope {
            launch { artScale.animateTo(1f, animationSpec = SgMotion.playerArtEnterSpec()) }
            launch { artOffsetY.animateTo(0f, animationSpec = SgMotion.playerArtOffsetEnterSpec()) }
            launch { chromeAlpha.animateTo(1f, animationSpec = SgMotion.playerChromeEnterSpec()) }
        }
    }

    // Cible réelle = centre de la pochette du mini-player (bas gauche), pas un simple
    // rétrécissement sur place : on part de la position "au repos" mesurée
    // (artRestCenterPx, indépendante de nos propres offsets) et on vise la géométrie
    // connue du mini-player (SgMotion.MiniArtCenter*) + les vrais insets de barre de
    // navigation. Décalage nul en repli si la mesure n'est pas encore dispo (ne
    // devrait pas arriver en pratique). Extrait en fonction propre : utilisée à la
    // fois par `runExitAnimation` (fermeture "discrète", tap/back) et par le dismiss
    // vertical interactif (`applyDismissDrag`, suit le doigt en continu).
    fun miniArtDismissTarget(): Offset {
        val restCenter = artRestCenterPx
        return if (restCenter != null && view.height > 0) {
            val targetCenterX = with(density) { SgMotion.MiniArtCenterXDp.toPx() }
            val distanceFromBottom = navBarBottomPx + with(density) { SgMotion.MiniArtCenterFromBottomDp.toPx() }
            val targetCenterY = view.height.toFloat() - distanceFromBottom
            Offset(targetCenterX - restCenter.x, targetCenterY - restCenter.y)
        } else {
            Offset(0f, SgMotion.PlayerArtEnterOffsetY)
        }
    }

    suspend fun runExitAnimation() {
        if (reducedMotion) {
            dismissRootOffsetY.snapTo(0f)
            if (hasRealSharedTransition) {
                artScale.snapTo(1f)
                artOffsetX.snapTo(0f)
                artOffsetY.snapTo(0f)
            } else {
                artScale.snapTo(SgMotion.PlayerArtMiniScale)
                artOffsetX.snapTo(0f)
                artOffsetY.snapTo(SgMotion.PlayerArtEnterOffsetY)
            }
            chromeAlpha.snapTo(0f)
            return
        }
        if (hasRealSharedTransition) {
            // Identité stricte sur le nœud partagé avant le pop : le morph est 100 % shared element.
            dismissRootOffsetY.snapTo(0f)
            artScale.snapTo(1f)
            artOffsetX.snapTo(0f)
            artOffsetY.snapTo(0f)
            chromeAlpha.snapTo(0f)
            return
        }
        val target = miniArtDismissTarget()
        kotlinx.coroutines.coroutineScope {
            launch { chromeAlpha.animateTo(0f, animationSpec = SgMotion.playerChromeExitSpec()) }
            launch { artOffsetX.animateTo(target.x, animationSpec = SgMotion.playerArtOffsetExitSpec()) }
            launch { artOffsetY.animateTo(target.y, animationSpec = SgMotion.playerArtOffsetExitSpec()) }
            launch { artScale.animateTo(SgMotion.PlayerArtMiniScale, animationSpec = SgMotion.playerArtExitSpec()) }
        }
    }

    // Dismiss vertical interactif ("peek annulable" Player → Mini) : pendant le
    // swipe down, on interpole EN DIRECT (snapTo, pas d'anim) entre l'état ouvert
    // et la cible mini-player (même géométrie que `runExitAnimation`) — la pochette
    // et le chrome suivent le doigt au pixel près, sans attendre le relâchement.
    // `snapTo` dans un coroutine lancé à chaque évènement de drag est le pattern
    // standard Compose pour piloter un `Animatable` depuis un geste bas niveau
    // (pas de `AnchoredDraggableState` ici : Player/Lyrics/dismiss cohabitent sur
    // les 4 mêmes Animatable déjà utilisés par le morph ouverture/fermeture, plus
    // simple que d'introduire un second système de drag en parallèle).
    suspend fun applyDismissDrag(rawProgress: Float) {
        val p = rawProgress.coerceIn(0f, 1f)
        dismissMorphProgress = p
        dismissRootOffsetY.snapTo(0f)
        chromeAlpha.snapTo(lerpFloat(1f, 0f, p))
        val target = miniArtDismissTarget()
        artScale.snapTo(lerpFloat(1f, SgMotion.PlayerArtMiniScale, p))
        artOffsetX.snapTo(lerpFloat(0f, target.x, p))
        artOffsetY.snapTo(lerpFloat(0f, target.y, p))
    }

    // Relâché avant le seuil → annulation : spring back vers l'état plein écran
    // (pas un simple snap, pour un feedback "élastique" cohérent avec le reste
    // de la motion de l'app, cf. SgMotion.SpringSnappy déjà utilisé sur les press).
    suspend fun cancelDismissDrag() {
        dismissMorphProgress = 0f
        kotlinx.coroutines.coroutineScope {
            launch { dismissRootOffsetY.animateTo(0f, animationSpec = SgMotion.SpringSnappy) }
            launch { artScale.animateTo(1f, animationSpec = SgMotion.SpringSnappy) }
            launch { artOffsetX.animateTo(0f, animationSpec = SgMotion.SpringSnappy) }
            launch { artOffsetY.animateTo(0f, animationSpec = SgMotion.SpringSnappy) }
            launch { chromeAlpha.animateTo(1f, animationSpec = SgMotion.tweenFastOf()) }
        }
    }

    // Mode vinyle : rotation continue tant que isPlaying=true, gelée à l'angle
    // courant sinon ("pause = stop", pas de reset à 0 — cf. Animatable qui
    // conserve sa dernière valeur quand la coroutine qui l'anime est annulée).
    val vinylRotation = remember { Animatable(0f) }
    LaunchedEffect(vinylModeEnabled, isPlaying, reducedMotion) {
        if (vinylModeEnabled && isPlaying && !reducedMotion) {
            while (true) {
                vinylRotation.animateTo(
                    targetValue = vinylRotation.value + 360f,
                    animationSpec = tween(durationMillis = 8000, easing = LinearEasing)
                )
            }
        }
    }

    fun dismissPlayer(fromInteractiveDrag: Boolean = false) {
        if (isExiting) return
        scope.launch {
            isExiting = true
            if (fromInteractiveDrag && dismissMorphProgress > 0f) {
                // Relâchement après un drag : la pochette est déjà à la cible mini —
                // pop direct sans reset (évite le jump vers plein écran puis morph).
                applyDismissDrag(1f)
                dismissRootOffsetY.snapTo(0f)
                chromeAlpha.snapTo(0f)
                onClose()
            } else if (hasRealSharedTransition) {
                dismissMorphProgress = 0f
                dismissRootOffsetY.snapTo(0f)
                artScale.snapTo(1f)
                artOffsetX.snapTo(0f)
                artOffsetY.snapTo(0f)
                if (reducedMotion) {
                    chromeAlpha.snapTo(0f)
                } else {
                    launch { chromeAlpha.animateTo(0f, animationSpec = SgMotion.playerChromeExitSpec()) }
                }
                onClose()
            } else {
                runExitAnimation()
                onClose()
            }
        }
    }

    LaunchedEffect(reducedMotion) {
        runEnterAnimation()
    }

    BackHandler(onBack = { dismissPlayer() })

    LaunchedEffect(player) {
        while (true) {
            try {
                currentPosition = player.currentPosition
                duration = player.duration.coerceAtLeast(1L)
                progress = currentPosition.toFloat() / duration.toFloat()
            } catch (_: Exception) {
                break
            }
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
            .offset { IntOffset(0, dismissRootOffsetY.value.toInt()) }
            .onSizeChanged { screenWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            // Peek horizontal Player → Paroles : le Player glisse vers la gauche au
            // même rythme que Paroles entre par la droite (cf. LyricsScreen, même
            // `lyricsPeekProgress` partagé côté AppNavigation). `size` est la taille
            // réelle du layout au moment du dessin — pas besoin de re-mesurer.
            .graphicsLayer { translationX = -lyricsPeekProgress * size.width }
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val offset = verticalDragOffset
                        if (reducedMotion) {
                            // "Skip peek, cut simple" : pas de suivi au doigt, juste le
                            // seuil fixe d'origine, tranché net.
                            when {
                                offset > 120f -> dismissPlayer()
                                offset < -120f -> onSwipeUp()
                            }
                        } else {
                            val wasDismissDragging = isDismissDragging
                            scope.launch {
                                when {
                                    wasDismissDragging && offset / dismissDragDistancePx >= dismissCommitFraction ->
                                        dismissPlayer(fromInteractiveDrag = true)
                                    wasDismissDragging -> cancelDismissDrag()
                                    offset < -120f -> onSwipeUp()
                                }
                            }
                        }
                        verticalDragOffset = 0f
                        isDismissDragging = false
                    },
                    onDragCancel = {
                        if (!reducedMotion && isDismissDragging) {
                            scope.launch { cancelDismissDrag() }
                        }
                        verticalDragOffset = 0f
                        isDismissDragging = false
                        dismissMorphProgress = 0f
                    },
                    onVerticalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                        change.consume()
                        verticalDragOffset += dragAmount
                        // Seul le drag vers le BAS pilote le dismiss "peek" (le swipe vers
                        // le haut ouvre la file d'attente, geste distinct — cf. onSwipeUp,
                        // volontairement resté au seuil simple, hors périmètre de la demande).
                        if (!reducedMotion && !isExiting) {
                            if (verticalDragOffset > 0f) {
                                isDismissDragging = true
                                val p = verticalDragOffset / dismissDragDistancePx
                                scope.launch { applyDismissDrag(p) }
                            } else if (isDismissDragging) {
                                isDismissDragging = false
                                scope.launch { applyDismissDrag(0f) }
                            }
                        }
                    }
                )
            }
            // Peek horizontal Player → Paroles, au même niveau que le drag sur la
            // pochette (qui change de piste) : la pochette consomme déjà ses propres
            // deltas horizontaux (cf. plus bas), donc ce détecteur ne se déclenche
            // que pour un swipe démarré ailleurs sur l'écran (header, titre, slider,
            // contrôles) — pas de conflit avec le changement de piste au doigt.
            // Rapporte un delta normalisé (signe : swipe gauche → +progrès) à
            // AppNavigation, seul propriétaire du progrès partagé avec LyricsScreen.
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onLyricsPeekDragStart() },
                    onDragEnd = { onLyricsPeekDragEnd() },
                    onDragCancel = { onLyricsPeekDragEnd() },
                    onHorizontalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                        change.consume()
                        onLyricsPeekDrag(-dragAmount / screenWidthPx)
                    }
                )
            }
    ) {
        // Fond flouté — image sous-échantillonnée avant le flou (perf : un flou sur
        // une image déjà petite coûte beaucoup moins cher qu'un flou 100dp sur la
        // résolution native, surtout en fallback logiciel sous Android 12/API 31).
        // Mode perf / reduced motion : pas de blur — Palette plate uniquement.
        val coilCrossfadeMs = sgCoilCrossfadeMs(SgMotion.FastMs)
        if (!reducedMotion && song.albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUri)
                    .size(480, 960)
                    .crossfade(coilCrossfadeMs)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
            )
        }
        
        // Couche de teinte ambiance — dérivée de la pochette (ambiencePalette), pas
        // d'un simple accent brut : reste lisible et cohérente avec le thème actif
        // (sombre OU clair), cf. problème "Clair Argent ne suit pas la pochette".
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            scrimTop,
                            scrimMid,
                            scrimBottom
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
            // Le "chrome" (tout sauf la pochette) apparaît en fondu juste après
            // le lancement du morph de la pochette (cf. artScale/chromeAlpha).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = chromeAlpha.value },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cible tactile 48dp (Fitts's Law) — la zone cliquable dépasse le glyphe visuel.
                SgTapTarget(onClick = { dismissPlayer() }) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_close_down),
                            contentDescription = "Fermer",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = "EN LECTURE",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                SgTapTarget(onClick = onOpenPlayerOptions) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_options),
                            contentDescription = "Options de lecture",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pochette — bornes de taille/position partagées avec le mini-player
            // (sgSharedAlbumArt) quand un vrai SharedTransitionLayout est disponible ;
            // pendant le dismiss interactif (dismissMorphProgress > 0), le morph manuel
            // prend le relais pour suivre le doigt sans conflit de bounds au pop.
            val interactiveDismissActive = dismissMorphProgress > 0f
            Box(
                modifier = Modifier
                    // Doit rester AVANT `.offset{}` : voir le commentaire sur
                    // `artRestCenterPx` plus haut (position stable, non affectée
                    // par notre propre décalage appliqué juste après).
                    .onGloballyPositioned { coordinates ->
                        val posInRoot = coordinates.positionInRoot()
                        artRestCenterPx = Offset(
                            posInRoot.x + coordinates.size.width / 2f,
                            posInRoot.y + coordinates.size.height / 2f
                        )
                    }
                    .offset {
                        IntOffset(
                            (dragOffsetX + if (!hasRealSharedTransition || interactiveDismissActive) artOffsetX.value else 0f).toInt(),
                            if (!hasRealSharedTransition || interactiveDismissActive) artOffsetY.value.toInt() else 0
                        )
                    }
                    .then(
                        if (!hasRealSharedTransition || interactiveDismissActive) {
                            Modifier.graphicsLayer {
                                scaleX = artScale.value
                                scaleY = artScale.value
                            }
                        } else {
                            Modifier
                        }
                    )
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .then(
                        if (hasRealSharedTransition && !interactiveDismissActive) {
                            Modifier.sgSharedAlbumArt(key = "album_art_${song.id}")
                        } else {
                            Modifier
                        }
                    )
                    .pointerInput(player.mediaItemCount) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val thresholdPx = with(density) { swipeThreshold.toPx() }
                                val offset = dragOffsetX
                                scope.launch {
                                    when {
                                        offset < -thresholdPx -> PlayerGuards.safeSeekToNext(player)
                                        offset > thresholdPx -> PlayerGuards.safeSeekToPrevious(player)
                                    }
                                    dragOffsetX = 0f
                                }
                            },
                            onDragCancel = { dragOffsetX = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                // Consommé ici pour ne pas laisser remonter le geste vers le
                                // détecteur "swipe → Paroles" du Box parent (cf. plus haut) :
                                // sur la pochette, le drag horizontal ne doit signifier QUE
                                // "changer de piste", jamais les deux actions à la fois.
                                change.consume()
                                dragOffsetX += dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = vinylModeEnabled,
                    animationSpec = if (reducedMotion) snap() else SgMotion.tweenMediumOf(),
                    label = "vinylMode"
                ) { vinylOn ->
                    if (vinylOn) {
                        VinylDisc(
                            song = song,
                            accentColor = displayAccent,
                            rotationDegrees = vinylRotation.value,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, displayAccent.copy(alpha = 0.22f), RoundedCornerShape(SgRadius.xl))
                                .clip(RoundedCornerShape(SgRadius.xl))
                                .background(SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.albumArtUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(song.albumArtUri)
                                        .crossfade(coilCrossfadeMs)
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
                    }
                }

            }

            Spacer(modifier = Modifier.height(24.dp))

            // Chrome (titre, slider, contrôles, actions) : fondu après le morph de la pochette.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = chromeAlpha.value },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (hasRealSharedTransition && !interactiveDismissActive) {
                                    Modifier.sgSharedBounds(key = "track_meta_${song.id}")
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Text(
                            text = song.displayTitle(),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.displayArtist(),
                            color = ensureContrast(displayAccent, surfaceBg, minRatio = 4.5f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Cœur : fill + pulse 1→1.12→1 une seule fois (SpringSoft) ; pas de particules.
                    val favoriteScale = remember { Animatable(1f) }
                    var wasFavorite by remember { mutableStateOf(isFavorite) }
                    LaunchedEffect(isFavorite) {
                        if (isFavorite && !wasFavorite && !reducedMotion) {
                            favoriteScale.snapTo(1f)
                            favoriteScale.animateTo(1.12f, SgMotion.SpringSoft)
                            favoriteScale.animateTo(1f, SgMotion.SpringSoft)
                        } else if (!isFavorite) {
                            favoriteScale.snapTo(1f)
                        }
                        wasFavorite = isFavorite
                    }
                    Icon(
                        painter = painterResource(if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline),
                        contentDescription = "Favori",
                        tint = if (isFavorite) FavoritePink else TextSecondary,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = favoriteScale.value
                                scaleY = favoriteScale.value
                            }
                            .clickable { onToggleFavorite() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Indicateur discret des réglages actifs — une seule pilule tappable
            // ouvrant la sheet Options (gapless/crossfade/sleep regroupés là-bas).
            PlayerActiveModesIndicator(
                gaplessEnabled = gaplessEnabled,
                crossfadeDurationMs = crossfadeDurationMs,
                sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                playbackSpeed = playbackSpeed,
                playbackPitch = playbackPitch,
                equalizerEnabled = equalizerEnabled,
                equalizerPresetLabel = equalizerPresetLabel,
                vinylModeEnabled = vinylModeEnabled,
                accentColor = displayAccent,
                onClick = onOpenPlayerOptions
            )

            Spacer(modifier = Modifier.height(8.dp))
            // Seek : thumb 16, track 4, hitSlop 44 (SgSeekBar custom).
            var isSeeking by remember { mutableStateOf(false) }
            var seekPosition by remember { mutableStateOf(0f) }

            SgSeekBar(
                value = if (isSeeking) seekPosition else progress.coerceIn(0f, 1f),
                onValueChange = { value ->
                    isSeeking = true
                    seekPosition = value
                },
                onValueChangeFinished = {
                    player.seekTo((seekPosition * duration).toLong())
                    isSeeking = false
                },
                accentColor = displayAccent,
                inactiveTrackColor = CardSurface,
                modifier = Modifier.fillMaxWidth()
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

            // Contrôles — tint FastMs (pas de rotation 360°) ; play = morph icône + press.
            val controlTintSpec = if (reducedMotion) snap() else SgMotion.tweenFastOf<Color>()
            val shuffleTint by animateColorAsState(
                targetValue = if (isShuffled) displayAccent else TextSecondary,
                animationSpec = controlTintSpec,
                label = "shuffleTint"
            )
            val repeatTint by animateColorAsState(
                targetValue = if (repeatMode > 0) displayAccent else TextSecondary,
                animationSpec = controlTintSpec,
                label = "repeatTint"
            )
            val playInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SgTapTarget(
                    onClick = {
                        isShuffled = !isShuffled
                        player.shuffleModeEnabled = isShuffled
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shuffle),
                        contentDescription = "Shuffle",
                        tint = shuffleTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { PlayerGuards.safeSeekToPrevious(player) },
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
                        .then(
                            if (hasRealSharedTransition && !interactiveDismissActive) {
                                Modifier.sgSharedBounds(key = sgPlayControlSharedKey(song.id))
                            } else {
                                Modifier
                            }
                        )
                        .sgPressScale(playInteraction)
                        .background(
                            Brush.radialGradient(listOf(displayAccent, displaySecondaryAccent)),
                            CircleShape
                        )
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null
                        ) { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = if (reducedMotion) snap() else SgMotion.tweenFastOf(),
                        label = "playPauseIcon"
                    ) { playing ->
                        Icon(
                            painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = if (playing) "Pause" else "Lecture",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { PlayerGuards.safeSeekToNext(player) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Suivant",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                SgTapTarget(
                    onClick = {
                        repeatMode = (repeatMode + 1) % 3
                        player.repeatMode = when (repeatMode) {
                            1 -> androidx.media3.common.Player.REPEAT_MODE_ALL
                            2 -> androidx.media3.common.Player.REPEAT_MODE_ONE
                            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(if (repeatMode == 2) R.drawable.ic_repeat_one else R.drawable.ic_repeat),
                        contentDescription = "Répéter",
                        tint = repeatTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Accès secondaires essentiels : file d'attente + paroles (le reste
            // vit dans la sheet Options, bouton header ⋯).
            Row(
                modifier = Modifier.fillMaxWidth(0.88f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SgTapTarget(
                    onClick = onOpenQueue,
                    modifier = Modifier.weight(0.35f)
                ) {
                    // Outline + surface-elevated + onSurface palette (≥ 4.5:1 sur fond lecteur)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SgRadius.pill))
                            .background(SurfaceElevated)
                            .border(1.dp, fileActionColor.copy(alpha = 0.35f), RoundedCornerShape(SgRadius.pill))
                            .padding(horizontal = SgSpacing.md, vertical = SgSpacing.sm + 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_queue),
                            contentDescription = "File d'attente",
                            tint = fileActionColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "File",
                            color = fileActionColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                SgTapTarget(
                    onClick = onOpenLyrics,
                    modifier = Modifier.weight(0.65f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SgRadius.pill))
                            .background(lyricsActionColor.copy(alpha = 0.18f))
                            .border(1.dp, lyricsActionColor.copy(alpha = 0.45f), RoundedCornerShape(SgRadius.pill))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lyrics,
                            contentDescription = "Paroles",
                            tint = lyricsActionColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Paroles", color = lyricsActionColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            }
        }
    }
}

/**
 * Pilule discrète affichée uniquement quand au moins un réglage secondaire est
 * actif (crossfade, minuterie, vitesse, EQ, vinyle…). Un tap ouvre la sheet
 * Options — pas de rangée de chips permanente sur le viewport principal.
 */
@Composable
private fun PlayerActiveModesIndicator(
    gaplessEnabled: Boolean,
    crossfadeDurationMs: Int,
    sleepTimerRemainingSeconds: Int?,
    playbackSpeed: Float,
    playbackPitch: Float,
    equalizerEnabled: Boolean,
    equalizerPresetLabel: String,
    vinylModeEnabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val tokens = buildList {
        if (crossfadeDurationMs > 0) {
            add(PlaybackPreferences.crossfadeLabel(crossfadeDurationMs))
        } else if (!gaplessEnabled) {
            add("Sans gapless")
        }
        formatSleepTimerDisplay(sleepTimerRemainingSeconds)?.let { add(it) }
        if (playbackSpeed != 1f) {
            add(if (playbackSpeed == playbackSpeed.toLong().toFloat()) "${playbackSpeed.toLong()}x" else "${playbackSpeed}x")
        }
        if (playbackPitch != 1f) {
            add("Pitch ${if (playbackPitch == playbackPitch.toLong().toFloat()) playbackPitch.toLong() else playbackPitch}x")
        }
        if (equalizerEnabled && equalizerPresetLabel != "Normal") {
            add(equalizerPresetLabel)
        } else if (!equalizerEnabled) {
            add("EQ off")
        }
        if (vinylModeEnabled) add("Vinyle")
    }
    if (tokens.isEmpty()) return

    val summary = tokens.take(2).joinToString(" · ")
    val overflow = tokens.size - 2

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(SgRadius.pill))
            .background(accentColor.copy(alpha = 0.12f))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(SgRadius.pill))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Tune,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (overflow > 0) "$summary +$overflow" else summary,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
/**
 * Disque vinyle : pochette rendue comme le "label" central d'un disque circulaire
 * avec sillons concentriques et trou d'axe, tournant en continu tant que la
 * lecture est active (cf. `vinylRotation` dans PlayerScreen — la rotation se
 * fige à l'angle courant sur pause, ne revient pas à 0).
 */
@Composable
private fun VinylDisc(
    song: Song,
    accentColor: Color,
    rotationDegrees: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotationDegrees }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF2E2E2E), Color(0xFF0C0C0C), Color.Black)
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Sillons du vinyle : anneaux concentriques discrets.
        Box(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape)
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.80f)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape)
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.68f)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
        )

        // Label central = pochette de l'album.
        Box(
            modifier = Modifier
                .fillMaxSize(0.56f)
                .clip(CircleShape)
                .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape)
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.albumArtUri)
                        .crossfade(sgCoilCrossfadeMs(SgMotion.FastMs))
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
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Trou de l'axe.
        Box(
            modifier = Modifier
                .fillMaxSize(0.06f)
                .clip(CircleShape)
                .background(GraphiteAbyss)
                .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape)
        )
    }
}

