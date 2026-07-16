package com.credo.soundgroove.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.credo.soundgroove.ui.components.SongContextMenuSheet
import com.credo.soundgroove.ui.components.SongInfoBottomSheet
import com.credo.soundgroove.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongs: List<Song>,
    accentColor: Color,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onShufflePlay: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {}
) {
    var songMenuTarget by remember { mutableStateOf<Song?>(null) }
    var infoSong by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current
    val albumCover = songs.firstOrNull { it.albumArtUri != null }?.albumArtUri
    val artistName = songs.firstOrNull()?.artist ?: "Inconnu"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SurfaceOverlay, GraphiteAbyss)))
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    if (albumCover != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(albumCover).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color(0xFF3D1D7A), Color(0xFF0D0517)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_songs),
                                contentDescription = null,
                                tint = accentColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(0.4f),
                                    0.5f to Color.Black.copy(0.2f),
                                    1f to Color(0xFF0D0517)
                                )
                            )
                    )

                    // Back Button
                    Box(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(0.4f), CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "Retour",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Title at bottom of header
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Text(
                            text = albumName,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$artistName • ${songs.size} chanson(s)",
                            color = Color.White.copy(0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // ── Actions Bar ───────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(Brush.horizontalGradient(listOf(accentColor, themeSecondaryAccent(accentColor))), RoundedCornerShape(14.dp))
                            .clickable { songs.firstOrNull()?.let { onPlaySong(it) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Lecture", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(GlassSurface, RoundedCornerShape(14.dp))
                            .clickable { onShufflePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painter = painterResource(R.drawable.ic_shuffle), contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Text("Aléatoire", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // ── Song List ────────────────────────────────────────────────────
            itemsIndexed(songs) { index, song ->
                val isFav = favoriteSongs.any { it.id == song.id }
                val isCurrent = song.id == currentSong?.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaySong(song) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                        if (isCurrent) {
                            Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        } else {
                            Text("${index + 1}", color = TextSecondary, fontSize = 13.sp)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, color = if (isCurrent) accentColor else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }

                    Icon(
                        painter = painterResource(R.drawable.ic_options),
                        contentDescription = "Menu",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { songMenuTarget = song }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(120.dp)) }
        }

        // Song context menu
        songMenuTarget?.let { song ->
            SongContextMenuSheet(
                song = song,
                isFavorite = favoriteSongs.any { it.id == song.id },
                onToggleFavorite = { onToggleFavorite(song) },
                onPlayNext = { onPlayNext(song) },
                onAddToQueue = { onAddToQueue(song) },
                onAddToPlaylist = { onAddToPlaylist(song) },
                onViewInfo = { infoSong = song },
                onDismiss = { songMenuTarget = null }
            )
        }

        infoSong?.let { song ->
            SongInfoBottomSheet(
                song = song,
                accentColor = accentColor,
                isFavorite = favoriteSongs.any { it.id == song.id },
                onToggleFavorite = { onToggleFavorite(song) },
                onShare = { com.credo.soundgroove.util.PlayerActions.shareSong(context, song) },
                onSetRingtone = { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, song) },
                onDismiss = { infoSong = null }
            )
        }
    }
}
