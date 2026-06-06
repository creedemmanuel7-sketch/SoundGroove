package com.credo.soundgroove.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800),
        label = "progress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SgSpacing.sm, vertical = 2.dp)
            .shadow(6.dp, RoundedCornerShape(SgRadius.xl), spotColor = accentColor.copy(0.12f))
            .clip(RoundedCornerShape(SgRadius.xl))
            .background(
                Brush.verticalGradient(
                    listOf(SurfaceOverlay.copy(0.97f), DeepPurple.copy(0.99f))
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.08f), RoundedCornerShape(SgRadius.xl))
            .clickable { onOpen() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress.coerceIn(0.02f, 1f))
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(listOf(accentColor, accentColor.copy(0.4f))),
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
                    .border(1.dp, accentColor.copy(0.24f), RoundedCornerShape(SgRadius.sm))
                    .clip(RoundedCornerShape(SgRadius.sm))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri).crossfade(true).build(),
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

            Column(modifier = Modifier.weight(1f)) {
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

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        Brush.radialGradient(listOf(accentColor, accentColor.copy(0.7f))),
                        CircleShape
                    )
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (isPlaying) "Pause" else "Jouer",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = "Suivant",
                tint = TextSecondary,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onSkipNext() }
            )
        }
    }
}
