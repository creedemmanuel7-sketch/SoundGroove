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
import com.credo.soundgroove.data.model.SmartPlaylistIds
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.AlbumArtThumb
import com.credo.soundgroove.ui.components.AddSongsToPlaylistSheet
import com.credo.soundgroove.ui.components.DualCtaBar
import com.credo.soundgroove.ui.components.SgEmptyState
import com.credo.soundgroove.ui.components.EditMetadataBottomSheet
import com.credo.soundgroove.ui.components.SongContextMenuSheet
import com.credo.soundgroove.ui.components.SongInfoBottomSheet
import com.credo.soundgroove.ui.components.rememberSongCoverArtPicker
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.util.displayArtist
import com.credo.soundgroove.util.displayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    librarySongs: List<Song>,
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
    onAddSongsToPlaylist: (List<Song>) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onSaveMetadata: (Song, String, String, String) -> Unit = { _, _, _, _ -> },
    onSetCoverArt: (Song, android.net.Uri) -> Unit = { _, _ -> }
) {
    var showRenameSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showAddSongsSheet by remember { mutableStateOf(false) }
    var songMenuTarget by remember { mutableStateOf<Song?>(null) }
    var infoSong by remember { mutableStateOf<Song?>(null) }
    var editSong by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current
    val launchCoverPicker = rememberSongCoverArtPicker(onCoverSelected = onSetCoverArt)
    val canEditContent = !playlist.isSmart

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
                                    sgHeroPlaceholderBrush()
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
                            .background(sgHeroScrimBrush())
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

                        if (canEditContent) {
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
                                    modifier = Modifier.background(SurfaceElevated)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Ajouter des titres", color = TextPrimary) },
                                        onClick = { showOptionsMenu = false; showAddSongsSheet = true },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_add),
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Renommer", color = TextPrimary) },
                                        onClick = { showOptionsMenu = false; showRenameSheet = true },
                                        leadingIcon = { Icon(painter = painterResource(R.drawable.ic_sort), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Supprimer la playlist", color = ErrorRed) },
                                        onClick = { showOptionsMenu = false; showDeleteDialog = true },
                                        leadingIcon = { Icon(painter = painterResource(R.drawable.ic_trash), contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp)) }
                                    )
                                }
                            }
                        }
                    }

                    // Title at bottom of header
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = playlist.name,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (playlist.isSmart) {
                                Box(
                                    modifier = Modifier
                                        .background(accentColor.copy(0.25f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Auto", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            text = when {
                                playlist.songs.isNotEmpty() ->
                                    com.credo.soundgroove.ui.util.songsCountLabel(playlist.songs.size)
                                playlist.id == SmartPlaylistIds.WITH_LYRICS ->
                                    "Aucun morceau avec paroles"
                                playlist.isSmart ->
                                    "Se remplit automatiquement"
                                else ->
                                    com.credo.soundgroove.ui.util.songsCountLabel(0)
                            },
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
                    enabled = playlist.songs.isNotEmpty(),
                    onPlay = { playlist.songs.firstOrNull()?.let { onPlaySong(it) } },
                    onShuffle = onShufflePlay,
                    modifier = Modifier.padding(horizontal = SgSpacing.lg, vertical = SgSpacing.md)
                )
            }

            // ── Song List ────────────────────────────────────────────────────
            if (playlist.songs.isEmpty()) {
                item {
                    SgEmptyState(
                        iconPainter = painterResource(
                            if (playlist.isSmart) R.drawable.ic_shuffle else R.drawable.ic_songs
                        ),
                        title = if (playlist.isSmart) "Playlist vide pour l'instant" else "Cette playlist est vide",
                        subtitle = when {
                            playlist.id == SmartPlaylistIds.WITH_LYRICS ->
                                "Ajoute ou télécharge des paroles pour qu'elles apparaissent ici."
                            playlist.isSmart ->
                                "Écoute de la musique pour remplir cette playlist automatiquement."
                            else ->
                                "Ajoute des titres depuis ta bibliothèque pour commencer."
                        },
                        compact = true,
                        actionLabel = if (canEditContent) "Ajouter des titres" else null,
                        accentColor = accentColor,
                        onAction = if (canEditContent) {{ showAddSongsSheet = true }} else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SgSpacing.xxl)
                    )
                }
            } else {
                itemsIndexed(playlist.songs) { index, song ->
                    val isCurrent = song.id == currentSong?.id

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaySong(song) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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

                        AlbumArtThumb(
                            albumArtUri = song.albumArtUri,
                            size = 46.dp,
                            cornerRadius = 10.dp,
                            accentColor = accentColor
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.displayTitle(),
                                color = if (isCurrent) accentColor else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.displayArtist(),
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

        // Rename sheet
        if (showRenameSheet) {
            RenamePlaylistSheet(
                currentName = playlist.name,
                accentColor = accentColor,
                onRename = { newName -> onRenamePlaylist(newName) },
                onDismiss = { showRenameSheet = false }
            )
        }

        if (showAddSongsSheet && canEditContent) {
            AddSongsToPlaylistSheet(
                playlistName = playlist.name,
                librarySongs = librarySongs,
                alreadyInPlaylistIds = playlist.songs.map { it.id }.toSet(),
                accentColor = accentColor,
                onConfirm = { selected ->
                    onAddSongsToPlaylist(selected)
                    showAddSongsSheet = false
                },
                onDismiss = { showAddSongsSheet = false },
                skipLabel = "Annuler"
            )
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = CardSurface,
                title = { Text("Supprimer la playlist ?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("\"${playlist.name}\" sera supprimée définitivement.", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        // Navigation gérée par onDeletePlaylist (un seul pop — pas de double
                        // popBackStack avec onBack, qui vidait la pile → écran blanc).
                        onDeletePlaylist()
                    }) {
                        Text("Supprimer", color = ErrorRed, fontWeight = FontWeight.Bold)
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
                            Brush.horizontalGradient(listOf(SilverAccent, GraphiteMid)),
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
