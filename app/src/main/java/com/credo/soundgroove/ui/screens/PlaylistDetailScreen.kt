package com.credo.soundgroove.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.SongContextMenuSheet
import com.credo.soundgroove.ui.components.SongInfoBottomSheet
import com.credo.soundgroove.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongs: List<Song>,
    accentColor: Color,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onShufflePlay: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRemoveSongFromPlaylist: (Long) -> Unit,
    onDeletePlaylist: () -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {}
) {
    var showRenameSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var songMenuTarget by remember { mutableStateOf<Song?>(null) }
    var infoSong by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current

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
                        .height(280.dp)
                ) {
                    // Mosaic of 4 cover arts or gradient
                    val covers = playlist.songs.mapNotNull { it.albumArtUri }.take(4)
                    if (covers.size >= 4) {
                        // 2x2 grid
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.weight(1f)) {
                                covers.take(2).forEach { uri ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                            Row(modifier = Modifier.weight(1f)) {
                                covers.drop(2).forEach { uri ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                        }
                    } else if (covers.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(covers.first()).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(listOf(Color(0xFF3D1D7A), Color(0xFF0D0517)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_playlists),
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
                                    0f to Color.Transparent,
                                    0.5f to Color.Black.copy(0.3f),
                                    1f to Color(0xFF0D0517)
                                )
                            )
                    )

                    // Back + Options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
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

                        Box {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(0.4f), CircleShape)
                                    .clickable { showOptionsMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_options),
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false },
                                modifier = Modifier.background(Color(0xFF2D1B4E))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Renommer", color = TextPrimary) },
                                    onClick = { showOptionsMenu = false; showRenameSheet = true },
                                    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_sort), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer la playlist", color = Color(0xFFFF6B6B)) },
                                    onClick = { showOptionsMenu = false; showDeleteDialog = true },
                                    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_favorite_outline), contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }

                    // Title at bottom of header
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Text(
                            text = playlist.name,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${playlist.songs.size} chanson(s)",
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
                    // Play button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(
                                Brush.horizontalGradient(listOf(accentColor, themeSecondaryAccent(accentColor))),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { playlist.songs.firstOrNull()?.let { onPlaySong(it) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Lecture", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    // Shuffle button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(GlassSurface, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.clickable { onShufflePlay() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shuffle),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Aléatoire", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // ── Song List ────────────────────────────────────────────────────
            if (playlist.songs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_songs),
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Cette playlist est vide",
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Appuyez sur ⋮ sur une chanson pour l'ajouter",
                                color = TextSecondary.copy(0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(playlist.songs) { index, song ->
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
                        // Track number or current indicator
                        Box(
                            modifier = Modifier.width(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrent) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_play),
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Cover
                        Box(
                            modifier = Modifier
                                .size(46.dp)
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

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = if (isCurrent) accentColor else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
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

                    if (index < playlist.songs.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                            color = GlassBorder.copy(alpha = 0.20f)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(120.dp)) }
            }
        }

        // ── Sheets & Dialogs ─────────────────────────────────────────────────

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

        // Rename sheet
        if (showRenameSheet) {
            RenamePlaylistSheet(
                currentName = playlist.name,
                accentColor = accentColor,
                onRename = { newName -> onRenamePlaylist(newName) },
                onDismiss = { showRenameSheet = false }
            )
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = Color(0xFF2D1B4E),
                title = { Text("Supprimer la playlist ?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("\"${playlist.name}\" sera supprimée définitivement.", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = { showDeleteDialog = false; onDeletePlaylist(); onBack() }) {
                        Text("Supprimer", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Annuler", color = TextSecondary)
                    }
                }
            )
        }
    }
}

// ─── Rename Sheet ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenamePlaylistSheet(
    currentName: String,
    accentColor: Color,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Renommer la playlist", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = accentColor,
                    focusedContainerColor = GlassSurface,
                    unfocusedContainerColor = GlassSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(GlassSurface, RoundedCornerShape(14.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Text("Annuler", color = TextSecondary, fontWeight = FontWeight.Bold) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(
                            Brush.horizontalGradient(listOf(LightPurple, MediumPurple)),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = name.isNotBlank()) {
                            onRename(name.trim())
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Renommer", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
