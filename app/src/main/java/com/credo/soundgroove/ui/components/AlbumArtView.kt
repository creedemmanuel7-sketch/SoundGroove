package com.credo.soundgroove.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.motion.SgCoverImage
import com.credo.soundgroove.ui.theme.TextSecondary
import com.credo.soundgroove.ui.theme.sgCoverFallbackBrush
import com.credo.soundgroove.ui.theme.sgHeroPlaceholderBrush
import com.credo.soundgroove.util.coverInitial

@Composable
fun AlbumArtView(
    albumArtUri: Uri?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    accentColor: Color = TextSecondary,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderLabel: String? = null,
    placeholderIconSize: Dp = 20.dp,
    placeholderLabelSize: TextUnit = 14.sp,
    /** false en listes (Coil only) ; true pour adoucir un skip URI sous shared element. */
    uriCrossfade: Boolean = false,
    /** Seed pour dégradé fallback (id / titre) — évite les carrés noirs plats. */
    fallbackSeed: String? = null,
    /** Côté cible Coil (dp) — thumbs / listes ; null = decode full (hero). */
    decodeEdgeDp: Dp? = null,
) {
    val fallbackBrush = if (fallbackSeed != null) {
        sgCoverFallbackBrush(fallbackSeed, accentColor)
    } else {
        sgHeroPlaceholderBrush()
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(fallbackBrush),
        contentAlignment = Alignment.Center
    ) {
        if (albumArtUri != null) {
            SgCoverImage(
                albumArtUri = albumArtUri,
                contentScale = contentScale,
                uriCrossfade = uriCrossfade,
                decodeEdgeDp = decodeEdgeDp,
                modifier = Modifier.fillMaxSize()
            )
        } else if (placeholderLabel != null) {
            Text(
                text = placeholderLabel,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = placeholderLabelSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_songs),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(placeholderIconSize)
            )
        }
    }
}

@Composable
fun AlbumArtThumb(
    albumArtUri: Uri?,
    size: Dp,
    cornerRadius: Dp = 10.dp,
    accentColor: Color = TextSecondary,
    modifier: Modifier = Modifier,
    fallbackSeed: String? = null,
    placeholderLabel: String? = null,
) {
    AlbumArtView(
        albumArtUri = albumArtUri,
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        accentColor = accentColor,
        placeholderLabel = placeholderLabel,
        placeholderIconSize = (size.value * 0.42f).dp.coerceAtLeast(16.dp),
        placeholderLabelSize = (size.value * 0.32f).sp,
        fallbackSeed = fallbackSeed,
        decodeEdgeDp = size,
    )
}

@Composable
fun AlbumArtThumb(
    song: Song,
    size: Dp,
    cornerRadius: Dp = 10.dp,
    accentColor: Color = TextSecondary,
    modifier: Modifier = Modifier,
) {
    AlbumArtThumb(
        albumArtUri = song.albumArtUri,
        size = size,
        cornerRadius = cornerRadius,
        accentColor = accentColor,
        modifier = modifier,
        fallbackSeed = song.id.toString(),
        placeholderLabel = song.coverInitial(),
    )
}

@Composable
fun ArtistAvatarView(
    albumArtUri: Uri?,
    artistName: String,
    size: Dp,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val initial = artistName.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    AlbumArtView(
        albumArtUri = albumArtUri,
        modifier = modifier.size(size),
        shape = CircleShape,
        accentColor = accentColor,
        placeholderLabel = initial,
        placeholderIconSize = (size.value * 0.35f).dp,
        placeholderLabelSize = (size.value * 0.28f).sp,
        fallbackSeed = artistName,
        decodeEdgeDp = size,
    )
}
