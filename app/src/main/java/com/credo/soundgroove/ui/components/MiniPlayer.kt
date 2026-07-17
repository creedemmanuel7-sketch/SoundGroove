package com.credo.soundgroove.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.snap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.theme.GraphiteAbyss
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.SgTapTarget
import com.credo.soundgroove.ui.theme.SurfaceElevated
import com.credo.soundgroove.ui.theme.SurfaceOverlay
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.TextSecondary
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
import com.credo.soundgroove.ui.theme.sgCoilCrossfadeMs
import com.credo.soundgroove.ui.theme.sgPlayControlSharedKey
import com.credo.soundgroove.ui.theme.sgSharedAlbumArt
import com.credo.soundgroove.ui.theme.sgSharedBounds
import com.credo.soundgroove.util.SongDisplay
import com.credo.soundgroove.util.blendWithAlbumArt
import com.credo.soundgroove.util.rememberAlbumArtAccentColor

/**
 * Mini-player unique — h64, art 40² radius-sm, progress 3px inset bas, skip 48dp.
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
    albumArtModifier: Modifier = Modifier.sgSharedAlbumArt(key = "album_art_${song.id}"),
    trackMetaModifier: Modifier = Modifier.sgSharedBounds(key = "track_meta_${song.id}"),
    playControlModifier: Modifier = Modifier.sgSharedBounds(
        key = sgPlayControlSharedKey(song.id),
        clipShape = CircleShape,
    ),
    @Suppress("UNUSED_PARAMETER") gaplessEnabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") crossfadeDurationMs: Int = 0,
    albumCoverAccentEnabled: Boolean = false,
) {
    val albumAccent = rememberAlbumArtAccentColor(song.albumArtUri, accentColor)
    val displayAccent = if (albumCoverAccentEnabled) {
        accentColor
    } else {
        blendWithAlbumArt(accentColor, albumAccent, weight = 0.3f)
    }
    val reducedMotion = rememberSgReducedMotion()
    val coilCrossfadeMs = sgCoilCrossfadeMs(SgMotion.FastMs)
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SgSpacing.sm, vertical = 2.dp)
            .height(SgSpacing.miniPlayerHeight)
            .shadow(6.dp, shape, spotColor = displayAccent.copy(0.12f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(SurfaceOverlay.copy(0.97f), GraphiteAbyss.copy(0.99f))
                )
            )
            .border(1.dp, displayAccent.copy(alpha = 0.08f), shape)
            .clickable { onOpen() }
    ) {
        // Progress 3px inset bas
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = SgSpacing.sm)
                .padding(bottom = 3.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(SgRadius.pill))
                .background(TextPrimary.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress.coerceIn(0.02f, 1f))
                    .background(
                        Brush.horizontalGradient(
                            listOf(displayAccent, displayAccent.copy(0.55f))
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
            Box(
                modifier = Modifier
                    .size(SgSpacing.miniPlayerArt)
                    .then(albumArtModifier)
                    .border(1.dp, displayAccent.copy(0.24f), RoundedCornerShape(SgRadius.sm))
                    .clip(RoundedCornerShape(SgRadius.sm))
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
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(trackMetaModifier)
            ) {
                Text(
                    text = SongDisplay.title(song.title, song.folderPath),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = SongDisplay.artist(song.artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
