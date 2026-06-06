package com.credo.soundgroove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.theme.*

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun SongListItem(
    song: Song,
    isFavorite: Boolean,
    isCurrentSong: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowBg = if (isCurrentSong) {
        Brush.horizontalGradient(
            listOf(accentColor.copy(alpha = 0.10f), Color.Transparent)
        )
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SgRadius.sm))
            .background(rowBg)
            .clickable { onClick() }
            .padding(horizontal = SgSpacing.xs, vertical = SgSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (isCurrentSong) Modifier.border(1.dp, accentColor.copy(alpha = 0.46f), RoundedCornerShape(SgRadius.sm))
                    else Modifier
                )
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
                    tint = TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (isCurrentSong) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accentColor.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrentSong) accentColor else TextPrimary,
                fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Medium,
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

        Text(
            text = formatDuration(song.duration),
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary
        )

        if (isFavorite) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(FavoritePink.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_favorite_filled),
                    contentDescription = null,
                    tint = FavoritePink,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { onMenuClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_options),
                contentDescription = "Menu",
                tint = TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
