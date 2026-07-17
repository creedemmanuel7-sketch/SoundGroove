package com.credo.soundgroove.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.util.SongDisplay
import com.credo.soundgroove.util.displayArtist
import com.credo.soundgroove.util.displayTitle

fun formatDuration(durationMs: Long): String {
    return SongDisplay.formatDurationOrNull(durationMs) ?: "—"
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
    isPlaying: Boolean = isCurrentSong,
    /**
     * Active les clés shared mini ↔ Player (`album_art_`, `track_meta_`, `play_control_`)
     * — requis pour Recherche → Player.
     */
    enablePlayerSharedElements: Boolean = false
) {
    val titleColor by animateColorAsState(
        targetValue = if (isCurrentSong) accentColor else TextPrimary,
        animationSpec = SgMotion.tweenFastOf(),
        label = "titleColor"
    )

    val albumArtMod = if (enablePlayerSharedElements) {
        Modifier.sgSharedAlbumArt(key = "album_art_${song.id}")
    } else {
        Modifier
    }
    val trackMetaMod = if (enablePlayerSharedElements) {
        Modifier.sgSharedBounds(key = "track_meta_${song.id}")
    } else {
        Modifier
    }
    val playControlMod = if (enablePlayerSharedElements) {
        Modifier.sgSharedBounds(key = sgPlayControlSharedKey(song.id))
    } else {
        Modifier
    }

    // Trailing slots : [favori?] [durée?] [play shared?] — height 56 cohérente partout.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SgSpacing.listRowHeight)
            .clip(RoundedCornerShape(SgRadius.sm))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMenuClick
            )
            .padding(horizontal = SgSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SgSpacing.md)
    ) {
        AlbumArtThumb(
            albumArtUri = song.albumArtUri,
            size = 44.dp,
            cornerRadius = SgRadius.sm,
            accentColor = accentColor,
            modifier = albumArtMod
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .then(trackMetaMod),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Piste en cours : badge barres d'égaliseur animées, pas juste un titre
                // teinté — beaucoup plus visible en scan rapide d'une longue liste.
                if (isCurrentSong) {
                    NowPlayingBadge(isPlaying = isPlaying, accentColor = accentColor)
                }
                Text(
                    text = song.displayTitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = titleColor,
                    fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = song.displayArtist(),
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

            SongDisplay.formatDurationOrNull(song.duration)?.let { durationLabel ->
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary.copy(alpha = 0.7f)
                )
            }

            if (enablePlayerSharedElements) {
                Box(
                    modifier = playControlMod
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
