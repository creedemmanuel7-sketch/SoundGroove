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
fun ArtistDetailScreen(
    artistName: String,
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
    // Get a nice image for the artist (using first album art)
    val artistCover = songs.firstOrNull { it.albumArtUri != null }?.albumArtUri

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SurfaceOverlay, DeepPurple)))
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    // Background blur / gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(DarkPurple, Color(0xFF0D0517))))
                    )

                    // Back Button
                    Box(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(0.2f), CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_back), contentDescription = "Retour", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    // Artist Info centered
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                                .padding(if (artistCover == null) 30.dp else 0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (artistCover != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(artistCover).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(painter = painterResource(R.drawable.ic_profile), contentDescription = null, tint = accentColor, modifier = Modifier.fillMaxSize())
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = artistName,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${songs.size} chanson(s)",
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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cover
                    Box(
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(DarkPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.albumArtUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(song.albumArtUri).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        if (isCurrent) {
                            Box(modifier = Modifier.fillMaxSize().background(accentColor.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                                Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, color = if (isCurrent) accentColor else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.albumName, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
