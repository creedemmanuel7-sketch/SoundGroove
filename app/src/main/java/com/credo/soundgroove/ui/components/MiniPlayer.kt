package com.credo.soundgroove.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.motion.SgRiveAccent
import com.credo.soundgroove.ui.theme.GraphiteAbyss
import com.credo.soundgroove.ui.theme.SgAdaptive
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.SgTapTarget
import com.credo.soundgroove.ui.theme.SurfaceOverlay
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.TextSecondary
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
import com.credo.soundgroove.ui.theme.sgConstrainWidth
import com.credo.soundgroove.ui.theme.sgSharedAlbumArt
import com.credo.soundgroove.util.SongDisplay
import com.credo.soundgroove.util.blendWithAlbumArt
import com.credo.soundgroove.util.coverInitial
import com.credo.soundgroove.util.rememberAlbumArtAccentColor

/**
 * Mini-player unique — coque d'écoute Phase 1 :
 * ambiance accent légère, art net, progress inset, contrôles snappy.
 * Pas de badge gapless/crossfade (réglages secondaires dans Options).
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    // Shared element = pochette seule (évite titres / play orphelins pendant le morph).
    albumArtModifier: Modifier = Modifier.sgSharedAlbumArt(key = "album_art_${song.id}"),
    trackMetaModifier: Modifier = Modifier,
    playControlModifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") gaplessEnabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") crossfadeDurationMs: Int = 0,
    albumCoverAccentEnabled: Boolean = false,
    isBuffering: Boolean = false,
    /** Bind MediaController en cours (cold start) — distinct du buffering piste. */
    isControllerConnecting: Boolean = false,
) {
    val albumAccent = rememberAlbumArtAccentColor(song.albumArtUri, accentColor)
    val displayAccent = if (albumCoverAccentEnabled) {
        accentColor
    } else {
        blendWithAlbumArt(accentColor, albumAccent, weight = 0.3f)
    }
    val reducedMotion = rememberSgReducedMotion()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (reducedMotion) snap() else SgMotion.tweenProgress(),
        label = "progress"
    )
    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (!reducedMotion && playPressed) 0.9f else 1f,
        animationSpec = if (reducedMotion) snap() else SgMotion.SpringSnappy,
        label = "playScale"
    )
    val shape = RoundedCornerShape(SgRadius.xl)
    val showBusySpinner = !isPlaying && (isBuffering || isControllerConnecting)
    val openDescription = buildString {
        append("Ouvrir le lecteur : ")
        append(SongDisplay.title(song.title, song.folderPath))
        append(", ")
        append(SongDisplay.artist(song.artist, song.title, song.folderPath))
        if (isPlaying) append(", en lecture")
        else if (isControllerConnecting) append(", connexion")
        else if (isBuffering) append(", chargement")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .sgConstrainWidth(SgAdaptive.MiniPlayerMax)
            .padding(horizontal = SgSpacing.sm, vertical = 2.dp)
            .height(SgSpacing.miniPlayerHeight)
            .shadow(10.dp, shape, spotColor = displayAccent.copy(0.22f), ambientColor = displayAccent.copy(0.08f))
            .clip(shape)
            // Fond opaque : pas de bleed du texte Settings / listes sous le mini.
            .background(SurfaceOverlay)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        displayAccent.copy(alpha = 0.16f),
                        SurfaceOverlay.copy(alpha = 0.0f),
                        GraphiteAbyss.copy(alpha = 0.35f),
                    )
                )
            )
            .border(1.dp, displayAccent.copy(alpha = 0.14f), shape)
            .semantics { contentDescription = openDescription }
            .clickable { onOpen() }
    ) {
        // Progress 3px inset bas — lecture = fil d'écoute visible en permanence
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = SgSpacing.sm)
                .padding(bottom = 3.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(SgRadius.pill))
                .background(TextPrimary.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress.coerceIn(0.02f, 1f))
                    .background(
                        Brush.horizontalGradient(
                            listOf(displayAccent, displayAccent.copy(0.65f))
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SgSpacing.md)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm)
        ) {
            val skipCrossfadeSpec = if (reducedMotion) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                fadeIn(tween(180)) togetherWith fadeOut(tween(180))
            }
            AnimatedContent(
                targetState = song.id,
                transitionSpec = { skipCrossfadeSpec },
                label = "miniSkipArt"
            ) { _ ->
                Box(
                    modifier = Modifier
                        .size(SgSpacing.miniPlayerArt)
                        .border(1.dp, displayAccent.copy(0.32f), RoundedCornerShape(SgRadius.sm))
                        .clip(RoundedCornerShape(SgRadius.sm)),
                    contentAlignment = Alignment.Center
                ) {
                    // Shared element = pochette seule (pas le pulse Rive → évite artefact morph).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(albumArtModifier)
                    ) {
                        AlbumArtView(
                            albumArtUri = song.albumArtUri,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(SgRadius.sm),
                            accentColor = displayAccent,
                            placeholderLabel = song.coverInitial(),
                            placeholderIconSize = 18.dp,
                            placeholderLabelSize = 14.sp,
                            fallbackSeed = song.id.toString(),
                            decodeEdgeDp = SgSpacing.miniPlayerArt,
                        )
                    }
                    if (isPlaying) {
                        SgRiveAccent(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 0.22f },
                            autoplay = true,
                            fallback = null,
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = song.id,
                transitionSpec = { skipCrossfadeSpec },
                label = "miniSkipMeta",
                modifier = Modifier
                    .weight(1f)
                    .then(trackMetaModifier)
            ) { _ ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = SongDisplay.title(song.title, song.folderPath),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPlaying) {
                            NowPlayingBars(
                                isPlaying = true,
                                accentColor = displayAccent,
                                modifier = Modifier.height(12.dp),
                                barHeight = 12.dp
                            )
                        }
                    }
                    Text(
                        text = SongDisplay.artist(song.artist, song.title, song.folderPath),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                SgTapTarget(onClick = onSkipPrevious, minSize = SgSpacing.hitTarget) {
                    Icon(
                        painter = painterResource(R.drawable.ic_previous),
                        contentDescription = "Précédent",
                        tint = TextSecondary,
                        modifier = Modifier.size(SgSpacing.iconSize)
                    )
                }

                SgTapTarget(
                    onClick = onPlayPause,
                    minSize = SgSpacing.hitTarget,
                    interactionSource = playInteraction,
                    indication = null
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .then(playControlModifier)
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            }
                            .background(
                                Brush.radialGradient(
                                    listOf(displayAccent, displayAccent.copy(0.7f))
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showBusySpinner) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(
                                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Jouer",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                SgTapTarget(onClick = onSkipNext, minSize = SgSpacing.hitTarget) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Suivant",
                        tint = TextSecondary,
                        modifier = Modifier.size(SgSpacing.iconSize)
                    )
                }
            }
        }
    }
}
