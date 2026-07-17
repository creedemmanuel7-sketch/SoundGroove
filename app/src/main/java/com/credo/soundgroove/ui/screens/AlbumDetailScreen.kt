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
import com.credo.soundgroove.ui.components.AlbumArtThumb
import com.credo.soundgroove.ui.components.DualCtaBar
import com.credo.soundgroove.ui.components.SgEmptyState
import com.credo.soundgroove.util.displayArtist
import com.credo.soundgroove.util.displayTitle
import com.credo.soundgroove.ui.components.EditMetadataBottomSheet
import com.credo.soundgroove.ui.components.SongContextMenuSheet
import com.credo.soundgroove.ui.components.SongInfoBottomSheet
import com.credo.soundgroove.ui.components.rememberSongCoverArtPicker
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
    onAddToPlaylist: (Song) -> Unit = {},
    onSaveMetadata: (Song, String, String, String) -> Unit = { _, _, _, _ -> },
    onSetCoverArt: (Song, android.net.Uri) -> Unit = { _, _ -> }
) {
    var songMenuTarget by remember { mutableStateOf<Song?>(null) }
    var infoSong by remember { mutableStateOf<Song?>(null) }
    var editSong by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current
    val launchCoverPicker = rememberSongCoverArtPicker(onCoverSelected = onSetCoverArt)
    val albumCover = songs.firstOrNull { it.albumArtUri != null }?.albumArtUri
    val artistName = com.credo.soundgroove.util.SongDisplay.artist(songs.firstOrNull()?.artist)

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
                    // SharedBounds avec la tile Bibliothèque (SgRadius.xl ≈ 24dp).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .sgSharedBounds(
                                key = sgAlbumCoverSharedKey(albumName),
                                clipShape = SgAlbumCoverSharedClip,
                            )
                            .clip(RoundedCornerShape(SgRadius.xl))
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
                                    .background(sgHeroPlaceholderBrush()),
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
                    }

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(sgHeroScrimBrushWithCover())
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
                            text = "$artistName • ${com.credo.soundgroove.ui.util.songsCountLabel(songs.size)}",
                            color = Color.White.copy(0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // ── Actions Bar ───────────────────────────────────────────────────
            item {
                DualCtaBar(
                    accentColor = accentColor,
                    enabled = songs.isNotEmpty(),
                    onPlay = { songs.firstOrNull()?.let { onPlaySong(it) } },
                    onShuffle = onShufflePlay,
                    modifier = Modifier.padding(horizontal = SgSpacing.lg, vertical = SgSpacing.md)
                )
            }

            // ── Song List ────────────────────────────────────────────────────
            if (songs.isEmpty()) {
                item {
                    SgEmptyState(
                        iconPainter = painterResource(R.drawable.ic_songs),
                        title = "Aucun titre dans cet album",
                        subtitle = "Les fichiers audio de cet album n'ont pas été trouvés.",
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SgSpacing.xxl)
                    )
                }
            } else {
            itemsIndexed(songs) { index, song ->
                val isCurrent = song.id == currentSong?.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaySong(song) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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

                    AlbumArtThumb(
                        albumArtUri = song.albumArtUri,
                        size = 46.dp,
                        cornerRadius = 10.dp,
                        accentColor = accentColor
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.displayTitle(), color = if (isCurrent) accentColor else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.displayArtist(), color = TextSecondary, fontSize = 12.sp, maxLines = 1)
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

                if (index < songs.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                        color = GlassBorder.copy(alpha = 0.20f)
                    )
                }
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
                onShareCard = {
                    com.credo.soundgroove.util.PlayerActions.shareSongCard(
                        context,
                        song,
                        accentColor.hashCode()
                    )
                },
                onEditMetadata = { editSong = song },
                onSetCoverArt = { launchCoverPicker(song) },
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
                onShareCard = {
                    com.credo.soundgroove.util.PlayerActions.shareSongCard(
                        context,
                        song,
                        accentColor.hashCode()
                    )
                },
                onEditMetadata = { editSong = song; infoSong = null },
                onSetCoverArt = { launchCoverPicker(song); infoSong = null },
                onSetRingtone = { com.credo.soundgroove.util.PlayerActions.setAsRingtone(context, song) },
                onDismiss = { infoSong = null }
            )
        }

        editSong?.let { song ->
            EditMetadataBottomSheet(
                song = song,
                accentColor = accentColor,
                onSave = { title, artist, album -> onSaveMetadata(song, title, artist, album) },
                onSetCoverArt = { launchCoverPicker(song) },
                onDismiss = { editSong = null }
            )
        }
    }
}
