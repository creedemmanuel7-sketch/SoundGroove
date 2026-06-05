package com.credo.soundgroove.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.theme.*

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float,          // 0f..1f
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
            .clickable { onOpen() }
    ) {
        // Background glass card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1E0A3C).copy(alpha = 0.97f),
                            Color(0xFF0D0517).copy(alpha = 0.99f)
                        )
                    ),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
        )

        // Progress bar as thin accent line at top
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(2.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(accentColor, accentColor.copy(alpha = 0.5f))
                    ),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album Art
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkPurple),
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
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Title & artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play / Pause button
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = if (isPlaying) "Pause" else "Jouer",
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onPlayPause() }
            )

            // Skip next button
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
