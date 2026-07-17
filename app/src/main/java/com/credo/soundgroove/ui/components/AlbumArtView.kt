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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.ui.theme.TextSecondary
import com.credo.soundgroove.ui.theme.sgHeroPlaceholderBrush

@Composable
fun AlbumArtView(
    albumArtUri: Uri?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    accentColor: Color = TextSecondary,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderLabel: String? = null,
    placeholderIconSize: Dp = 20.dp,
    placeholderLabelSize: TextUnit = 14.sp
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(sgHeroPlaceholderBrush()),
        contentAlignment = Alignment.Center
    ) {
        if (albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (placeholderLabel != null) {
            Text(
                text = placeholderLabel,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = placeholderLabelSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_songs),
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.65f),
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
    modifier: Modifier = Modifier
) {
    AlbumArtView(
        albumArtUri = albumArtUri,
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        accentColor = accentColor,
        placeholderIconSize = (size.value * 0.42f).dp.coerceAtLeast(16.dp)
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
    val initial = artistName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    AlbumArtView(
        albumArtUri = albumArtUri,
        modifier = modifier.size(size),
        shape = CircleShape,
        accentColor = accentColor,
        placeholderLabel = initial,
        placeholderIconSize = (size.value * 0.35f).dp,
        placeholderLabelSize = (size.value * 0.28f).sp
    )
}
