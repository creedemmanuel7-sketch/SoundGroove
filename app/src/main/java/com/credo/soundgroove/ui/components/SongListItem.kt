package com.credo.soundgroove.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    isFavorite: Boolean,
    isCurrentSong: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Distinct de `isCurrentSong` (qui peut rester vrai en pause) : pilote seulement
    // l'anim des barres du badge "en lecture". Par défaut = isCurrentSong pour les
    // appelants qui ne suivent pas encore l'état play/pause (comportement inchangé).
    isPlaying: Boolean = isCurrentSong
) {
    val titleColor by animateColorAsState(
        targetValue = if (isCurrentSong) accentColor else TextPrimary,
        animationSpec = SgMotion.tweenFastOf(),
        label = "titleColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SgRadius.sm))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMenuClick
            )
            .padding(horizontal = SgSpacing.xs, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SgSpacing.md)
    ) {
        AlbumArtThumb(
            albumArtUri = song.albumArtUri,
            size = 44.dp,
            cornerRadius = SgRadius.sm,
            accentColor = accentColor
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Piste en cours : badge barres d'égaliseur animées, pas juste un titre
                // teinté — beaucoup plus visible en scan rapide d'une longue liste.
                if (isCurrentSong) {
                    NowPlayingBadge(isPlaying = isPlaying, accentColor = accentColor)
                }
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = titleColor,
                    fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
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
            horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm)
        ) {
            if (isFavorite) {
                Icon(
                    painter = painterResource(R.drawable.ic_favorite_filled),
                    contentDescription = null,
                    tint = TextTertiary.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary.copy(alpha = 0.7f)
            )
        }
    }
}
