package com.credo.soundgroove.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurLinear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.util.blendWithAlbumArt
import com.credo.soundgroove.util.rememberAlbumArtAccentColor

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
    // Shared element réel (SharedTransitionLayout) par défaut : la pochette morphe
    // vers celle du Player plein écran si le contexte est disponible (fourni par
    // AppNavigation/MainScreen via CompositionLocal), sinon ce modifier est un
    // no-op — cf. docs/FEATURES_C_SHARED_ELEMENT.md et ui/theme/Motion.kt.
    albumArtModifier: Modifier = Modifier.sgSharedAlbumArt(key = "album_art_${song.id}"),
    // Bornes partagées titre+artiste (sharedBounds, pas sharedElement — contenu différent).
    trackMetaModifier: Modifier = Modifier.sgSharedBounds(key = "track_meta_${song.id}"),
    // Feedback UI req. 4 : un badge discret sur la pochette signale un mode de
    // lecture non "silencieux par défaut" (crossfade actif ou gapless désactivé).
    gaplessEnabled: Boolean = true,
    crossfadeDurationMs: Int = 0
) {
    val albumAccent = rememberAlbumArtAccentColor(song.albumArtUri, accentColor)
    val displayAccent = blendWithAlbumArt(accentColor, albumAccent, weight = 0.3f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = SgMotion.tweenProgress(),
        label = "progress"
    )
    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (playPressed) 0.9f else 1f,
        animationSpec = SgMotion.SpringSnappy,
        label = "playScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SgSpacing.sm, vertical = 2.dp)
            .shadow(6.dp, RoundedCornerShape(SgRadius.xl), spotColor = displayAccent.copy(0.12f))
            .clip(RoundedCornerShape(SgRadius.xl))
            .background(
                Brush.verticalGradient(
                    listOf(SurfaceOverlay.copy(0.97f), GraphiteAbyss.copy(0.99f))
                )
            )
            .border(1.dp, displayAccent.copy(alpha = 0.08f), RoundedCornerShape(SgRadius.xl))
            .clickable { onOpen() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress.coerceIn(0.02f, 1f))
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(listOf(displayAccent, displayAccent.copy(0.4f))),
                    RoundedCornerShape(topStart = SgRadius.xl, topEnd = SgRadius.xl)
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SgSpacing.md, vertical = SgSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(albumArtModifier)
                    .border(1.dp, displayAccent.copy(0.24f), RoundedCornerShape(SgRadius.sm))
                    .clip(RoundedCornerShape(SgRadius.sm))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            // Token M3 "Fast" (SgMotion) : micro-surface, transition rapide.
                            .data(song.albumArtUri).crossfade(SgMotion.FastMs).build(),
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
                if (crossfadeDurationMs > 0 || !gaplessEnabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(GraphiteAbyss)
                            .border(1.dp, displayAccent.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (crossfadeDurationMs > 0) Icons.Filled.BlurLinear else Icons.Filled.Pause,
                            contentDescription = if (crossfadeDurationMs > 0) "Crossfade actif" else "Gapless désactivé",
                            tint = displayAccent,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(trackMetaModifier)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SgTapTarget(onClick = onSkipPrevious) {
                    Icon(
                        painter = painterResource(R.drawable.ic_previous),
                        contentDescription = "Précédent",
                        tint = TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Cible tactile ≥48dp (Fitts's Law) autour du bouton visuel de 38dp.
                SgTapTarget(
                    onClick = onPlayPause,
                    interactionSource = playInteraction,
                    indication = null
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            }
                            .background(
                                Brush.radialGradient(listOf(displayAccent, displayAccent.copy(0.7f))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = if (isPlaying) "Pause" else "Jouer",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                SgTapTarget(onClick = onSkipNext) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Suivant",
                        tint = TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
