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
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.formatDuration
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.util.PlayerGuards
import com.credo.soundgroove.util.blendWithAlbumArt
import com.credo.soundgroove.util.rememberAlbumArtAccentColor
import kotlinx.coroutines.launch

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
    onShareCard: () -> Unit = {},
    onEditMetadata: () -> Unit = {},
    onSetRingtone: () -> Unit = {},
    playbackSpeed: Float = 1f,
    onOpenPlaybackSpeed: () -> Unit = {},
    onOpenLyrics: () -> Unit = {}
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
    val scope = rememberCoroutineScope()

    // Morph d'ouverture / fermeture inspiré de la transition "Now Playing" d'Apple Music :
    // la pochette part de la taille mini-player (scale ~0.14) et remonte vers le centre ;
    // le chrome apparaît / disparaît en fondu décalé. Symétrique à la fermeture.
    val artScale = remember { Animatable(SgMotion.PlayerArtMiniScale) }
    val artOffsetY = remember { Animatable(SgMotion.PlayerArtEnterOffsetY) }
    val chromeAlpha = remember { Animatable(0f) }
    var isExiting by remember { mutableStateOf(false) }
    val reducedMotion = rememberSgReducedMotion()

    suspend fun runEnterAnimation() {
        if (reducedMotion) {
            artScale.snapTo(1f)
            artOffsetY.snapTo(0f)
            chromeAlpha.snapTo(1f)
            return
        }
        kotlinx.coroutines.coroutineScope {
            launch { artScale.animateTo(1f, animationSpec = SgMotion.playerArtEnterSpec()) }
            launch { artOffsetY.animateTo(0f, animationSpec = SgMotion.playerArtOffsetEnterSpec()) }
            launch {
                kotlinx.coroutines.delay(SgMotion.PlayerChromeDelayMs.toLong())
                chromeAlpha.animateTo(1f, animationSpec = SgMotion.playerChromeEnterSpec())
            }
        }
    }

    suspend fun runExitAnimation() {
        if (reducedMotion) {
            artScale.snapTo(SgMotion.PlayerArtMiniScale)
            artOffsetY.snapTo(SgMotion.PlayerArtEnterOffsetY)
            chromeAlpha.snapTo(0f)
            return
        }
        kotlinx.coroutines.coroutineScope {
            launch { chromeAlpha.animateTo(0f, animationSpec = SgMotion.playerChromeExitSpec()) }
            launch { artOffsetY.animateTo(SgMotion.PlayerArtEnterOffsetY, animationSpec = SgMotion.playerArtOffsetExitSpec()) }
            launch { artScale.animateTo(SgMotion.PlayerArtMiniScale, animationSpec = SgMotion.playerArtExitSpec()) }
        }
    }

    fun dismissPlayer() {
        if (isExiting) return
        scope.launch {
            isExiting = true
            runExitAnimation()
            onClose()
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
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        when {
                            verticalDragOffset > 120f -> dismissPlayer()
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
        // Fond flouté — image sous-échantillonnée avant le flou (perf : un flou sur
        // une image déjà petite coûte beaucoup moins cher qu'un flou 100dp sur la
        // résolution native, surtout en fallback logiciel sous Android 12/API 31).
        // La couleur dominante (Palette, cf. displayAccent) porte l'essentiel de
        // l'ambiance visuelle ; le flou n'est qu'un appoint discret.
        if (song.albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUri)
                    .size(480, 960)
                    // Durée calée sur le token M3 "Medium" (SgMotion) : le fond change au
                    // même rythme que la pochette pour rester perçu comme un seul morph,
                    // pas deux animations désynchronisées (cf. UX_MOTION_GUIDELINES.md).
                    .crossfade(SgMotion.MediumMs)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
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

                SgTapTarget(onClick = { showOptionsMenu = true }) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_options),
                            contentDescription = "Options",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Hick's Law (lawsofux.com/hicks-law) : le menu ne liste que les
                    // actions absentes de la rangée de boutons du bas (Favori, File,
                    // Vitesse, Infos, Paroles) pour ne pas dupliquer les choix et
                    // garder une décision rapide.
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        modifier = Modifier.background(CardSurface)
                    ) {
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
                                    Icon(Icons.Filled.Image, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Partager la carte", color = TextPrimary)
                                }
                            },
                            onClick = {
                                showOptionsMenu = false
                                onShareCard()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Modifier métadonnées", color = TextPrimary)
                                }
                            },
                            onClick = {
                                showOptionsMenu = false
                                onEditMetadata()
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
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pochette
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            dragOffsetX.toInt(),
                            (artOffsetY.value + verticalDragOffset.coerceAtLeast(0f)).toInt()
                        )
                    }
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = artScale.value
                        scaleY = artScale.value
                    }
                    .border(1.dp, displayAccent.copy(alpha = 0.22f), RoundedCornerShape(SgRadius.xl))
                    .clip(RoundedCornerShape(SgRadius.xl))
                    .background(SurfaceElevated)
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
                            .crossfade(SgMotion.MediumMs)
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
                        tint = if (isFavorite) FavoritePink else TextSecondary,
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
                SgTapTarget(
                    onClick = {
                        isShuffled = !isShuffled
                        player.shuffleModeEnabled = isShuffled
                    }
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
                    tint = if (isFavorite) FavoritePink else TextSecondary,
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
                    label = if (playbackSpeed == playbackSpeed.toLong().toFloat()) {
                        "Vitesse ${playbackSpeed.toLong()}x"
                    } else {
                        "Vitesse ${playbackSpeed}x"
                    },
                    tint = if (playbackSpeed != 1f) displayAccent else TextSecondary,
                    onClick = onOpenPlaybackSpeed
                )
                PlayerActionButton(
                    iconRes = R.drawable.ic_songs,
                    label = "Infos",
                    tint = TextSecondary,
                    onClick = onShowInfo
                )
                PlayerActionButton(
                    icon = Icons.Filled.Lyrics,
                    label = "Paroles",
                    tint = TextSecondary,
                    onClick = onOpenLyrics
                )
            }
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
        // Cible tactile ≥48dp (Fitts's Law — lawsofux.com/fittss-law) : le glyphe
        // reste petit (22dp) mais la zone cliquable respecte le minimum M3.
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(SgRadius.pill))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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

@Composable
private fun PlayerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(SgRadius.pill))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

