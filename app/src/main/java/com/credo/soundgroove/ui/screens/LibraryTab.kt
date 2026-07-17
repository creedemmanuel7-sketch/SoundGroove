package com.credo.soundgroove.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.SongItem
import com.credo.soundgroove.ui.components.formatDuration
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.util.PlayerGuards
import com.credo.soundgroove.util.blendWithAlbumArt
import com.credo.soundgroove.util.rememberAlbumArtAccentColor
import kotlinx.coroutines.launch

@Composable
fun LibraryTab(
    songs: List<Song>,
    sortedSongs: List<Song> = emptyList(),
    sortMode: Int = 0,
    onSortModeChange: (Int) -> Unit = {},
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongs: List<Song>,
    playlists: List<Playlist>,
    onPlaylistCreate: (String) -> Unit,
    onPlaylistAddSong: (Playlist, Song) -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayPlaylist: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlaylistDelete: (Playlist) -> Unit,
    onPlaylistRename: (Playlist, String) -> Unit,
    onRemoveSongFromPlaylist: (Playlist, Long) -> Unit = { _, _ -> },
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onShowSongInfo: (Song) -> Unit = {},
    onShareCard: (Song) -> Unit = {},
    onEditMetadata: (Song) -> Unit = {},
    onSetCoverArt: (Song, android.net.Uri) -> Unit = { _, _ -> },
    onShowPlaylistPicker: (Song) -> Unit = {},
    selectedTab: Int = 0,
    onSelectedTabChange: (Int) -> Unit = {},
    hiddenFolders: Set<String> = emptySet(),
    onHideFolder: (String) -> Unit = {},
    onUnhideFolder: (String) -> Unit = {},
    accentColor: Color
) {
    var selectedAlbum by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var selectedArtist by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var selectedFolder by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var folderMenuTarget by remember { mutableStateOf<String?>(null) }
    var showHideFolderConfirm by remember { mutableStateOf(false) }
    val launchCoverPicker = com.credo.soundgroove.ui.components.rememberSongCoverArtPicker(
        onCoverSelected = onSetCoverArt
    )
    
    BackHandler(enabled = selectedAlbum != null) { selectedAlbum = null }
    BackHandler(enabled = selectedArtist != null) { selectedArtist = null }
    BackHandler(enabled = selectedFolder != null) { selectedFolder = null }
    val tabs = listOf("Chansons", "Albums", "Artistes", "Playlists", "Dossiers", "Favoris")
    val albums = remember(songs) {
        songs.groupBy { it.albumName }
            .entries
            .sortedBy { it.key }
            .map { Pair(it.key, it.value) }
    }

    val artists = remember(songs) {
        songs.map { it.artist }.distinct().sorted()
    }

    val folders = remember(songs) {
        songs.groupBy { song ->
            song.folderPath.takeIf { it.isNotBlank() } ?: "Dossier inconnu"
        }.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Contenu principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                    .padding(horizontal = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Ma Musique",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val tabIcons = listOf(
                R.drawable.ic_songs,
                R.drawable.ic_playlists,
                R.drawable.ic_profile,
                R.drawable.ic_queue,
                R.drawable.ic_songs,
                R.drawable.ic_favorite_outline
            )
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = selectedTab.coerceIn(0, tabs.lastIndex),
                pageCount = { tabs.size }
            )
            val tabsListState = androidx.compose.foundation.lazy.rememberLazyListState()
            val scope = rememberCoroutineScope()
            val activeTab = pagerState.currentPage.coerceIn(0, tabs.lastIndex)

            LaunchedEffect(selectedTab) {
                val target = selectedTab.coerceIn(0, tabs.lastIndex)
                if (pagerState.currentPage != target) {
                    pagerState.animateScrollToPage(target)
                }
            }
            LaunchedEffect(activeTab) {
                if (selectedTab != activeTab) {
                    onSelectedTabChange(activeTab)
                }
                tabsListState.animateScrollToItem(activeTab)
            }

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = tabsListState,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tabs.size) { index ->
                    val selected = activeTab == index
                    val iconRes = if (index == 5 && selected) R.drawable.ic_favorite_filled else tabIcons.getOrElse(index) { R.drawable.ic_songs }
                    val chipBg by animateColorAsState(
                        targetValue = if (selected) accentColor else GlassSurface,
                        animationSpec = SgMotion.tweenFastOf(),
                        label = "libChipBg"
                    )
                    val chipBorder by animateColorAsState(
                        targetValue = if (selected) accentColor.copy(0.5f) else GlassBorder,
                        animationSpec = SgMotion.tweenFastOf(),
                        label = "libChipBorder"
                    )
                    val chipScale by animateFloatAsState(
                        targetValue = if (selected) 1.03f else 1f,
                        animationSpec = SgMotion.SpringSoft,
                        label = "libChipScale"
                    )
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = chipScale
                                scaleY = chipScale
                            }
                            .clip(RoundedCornerShape(SgRadius.pill))
                            .background(
                                if (selected) Brush.horizontalGradient(listOf(chipBg, themeSecondaryAccent(accentColor)))
                                else Brush.horizontalGradient(listOf(chipBg, chipBg))
                            )
                            .border(1.dp, chipBorder, RoundedCornerShape(SgRadius.pill))
                            .clickable {
                                onSelectedTabChange(index)
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                    tabsListState.animateScrollToItem(index)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(iconRes),
                            contentDescription = null,
                            tint = if (selected) Color.White else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tabs[index],
                            color = if (selected) Color.White else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        var showSortSheet by remember { mutableStateOf(false) }
                        var menuSong by remember { mutableStateOf<Song?>(null) }
                        val sortLabels = listOf("Nom (A → Z)", "Nom (Z → A)", "Artiste", "Récent")
                        val displaySongs = sortedSongs.ifEmpty { songs }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${songs.size} titre(s)",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(SgRadius.pill))
                                        .background(GlassSurface)
                                        .border(1.dp, GlassBorder, RoundedCornerShape(SgRadius.pill))
                                        .clickable { showSortSheet = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_sort),
                                        contentDescription = "Trier",
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = sortLabels[sortMode],
                                        color = accentColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(displaySongs, key = { it.id }) { song ->
                                    com.credo.soundgroove.ui.components.SongListItem(
                                        song = song,
                                        isFavorite = favoriteSongs.any { it.id == song.id },
                                        isCurrentSong = currentSong?.id == song.id,
                                        accentColor = accentColor,
                                        onClick = { onPlayPlaylist(song, displaySongs) },
                                        onMenuClick = { menuSong = song }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                        }

                        if (showSortSheet) {
                            com.credo.soundgroove.ui.components.SortBottomSheet(
                                currentMode = sortMode,
                                onModeSelected = onSortModeChange,
                                onDismiss = { showSortSheet = false }
                            )
                        }

                        menuSong?.let { song ->
                            com.credo.soundgroove.ui.components.SongContextMenuSheet(
                                song = song,
                                isFavorite = favoriteSongs.any { it.id == song.id },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onPlayNext = { onPlayNext(song); menuSong = null },
                                onAddToQueue = { onAddToQueue(song); menuSong = null },
                                onAddToPlaylist = { onShowPlaylistPicker(song); menuSong = null },
                                onViewInfo = { onShowSongInfo(song); menuSong = null },
                                onShareCard = { onShareCard(song); menuSong = null },
                                onEditMetadata = { onEditMetadata(song); menuSong = null },
                                onSetCoverArt = { launchCoverPicker(song); menuSong = null },
                                onDismiss = { menuSong = null }
                            )
                        }
                    }

                1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val rows = albums.chunked(2)
                    items(rows, key = { row -> row.joinToString("|") { it.first } }) { rowAlbums ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowAlbums.forEach { (albumName, albumSongs) ->
                                GlassCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clickable { onNavigateToAlbum(albumName) },
                                    cornerRadius = 16.dp
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.BottomStart
                                    ) {
                                        val coverSong =
                                            albumSongs.firstOrNull { it.albumArtUri != null }
                                        if (coverSong?.albumArtUri != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(coverSong.albumArtUri)
                                                    .crossfade(true)
                                                    .build(),
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
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            Color.Black.copy(0.8f)
                                                        )
                                                    )
                                                )
                                        )
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = albumName,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${albumSongs.size} titres",
                                                color = TextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowAlbums.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                2 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(artists, key = { it }) { artist ->
                        val artistSongs = songs.filter { it.artist == artist }
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToArtist(artist) },
                            cornerRadius = 14.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(46.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val coverSong =
                                        artistSongs.firstOrNull { it.albumArtUri != null }
                                    com.credo.soundgroove.ui.components.ArtistAvatarView(
                                        albumArtUri = coverSong?.albumArtUri,
                                        artistName = artist,
                                        size = 46.dp,
                                        accentColor = accentColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${artistSongs.size} chanson(s)",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }


                3 -> {
                    var showCreateSheet by remember { mutableStateOf(false) }
                    val manualPlaylists = playlists.filter { !it.isSmart }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Playlists",
                                        color = TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = buildString {
                                            append("${manualPlaylists.size} playlist(s)")
                                            val smartCount = playlists.count { it.isSmart }
                                            if (smartCount > 0) append(" · $smartCount auto")
                                        },
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(SgRadius.pill))
                                        .background(Brush.horizontalGradient(listOf(accentColor, themeSecondaryAccent(accentColor))))
                                        .clickable { showCreateSheet = true }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("Créer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (playlists.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(painter = painterResource(R.drawable.ic_queue), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Aucune playlist",
                                            color = TextSecondary,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "Appuie sur Créer pour commencer",
                                            color = TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(playlists, key = { it.id }) { playlist ->
                                        var showMenu by remember { mutableStateOf(false) }
                                        var showRenameDialog by remember { mutableStateOf(false) }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (playlist.isSmart) CardSurface.copy(alpha = 0.42f) else CardSurface.copy(alpha = 0.32f),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .clickable { onNavigateToPlaylist(playlist.id) }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        Brush.radialGradient(
                                                            listOf(
                                                                if (playlist.isSmart) accentColor.copy(0.35f) else SilverAccent,
                                                                GraphiteMid
                                                            )
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (playlist.songs.isNotEmpty() && playlist.songs.first().albumArtUri != null) {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(LocalContext.current)
                                                            .data(playlist.songs.first().albumArtUri)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        painter = painterResource(
                                                            if (playlist.isSmart) R.drawable.ic_shuffle else R.drawable.ic_songs
                                                        ),
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = playlist.name,
                                                        color = TextPrimary,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    if (playlist.isSmart) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(accentColor.copy(0.18f), RoundedCornerShape(6.dp))
                                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "Auto",
                                                                color = accentColor,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = if (playlist.songs.isEmpty()) {
                                                        if (playlist.isSmart) "Se remplit en écoutant" else "Vide"
                                                    } else {
                                                        "${playlist.songs.size} chanson(s)"
                                                    },
                                                    color = TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            if (!playlist.isSmart) {
                                                Box {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_options),
                                                        contentDescription = "Options",
                                                        tint = TextSecondary,
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clickable { showMenu = true }
                                                    )
                                                    DropdownMenu(
                                                        expanded = showMenu,
                                                        onDismissRequest = { showMenu = false },
                                                        modifier = Modifier.background(CardSurface)
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(
                                                                        painter = painterResource(R.drawable.ic_options),
                                                                        contentDescription = null,
                                                                        tint = TextPrimary,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Text("Renommer", color = TextPrimary)
                                                                }
                                                            },
                                                            onClick = {
                                                                showMenu = false
                                                                showRenameDialog = true
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(
                                                                        painter = painterResource(R.drawable.ic_trash),
                                                                        contentDescription = null,
                                                                        tint = ErrorRed,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Text("Supprimer", color = ErrorRed)
                                                                }
                                                            },
                                                            onClick = {
                                                                showMenu = false
                                                                onPlaylistDelete(playlist)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (showRenameDialog) {
                                            var newName by remember { mutableStateOf(playlist.name) }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.7f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(32.dp)
                                                        .background(CardSurface, RoundedCornerShape(20.dp))
                                                        .padding(24.dp)
                                                ) {
                                                    Text(
                                                        text = "Renommer la playlist",
                                                        color = TextPrimary,
                                                        fontSize = 20.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    androidx.compose.material3.TextField(
                                                        value = newName,
                                                        onValueChange = { newName = it },
                                                        label = { Text("Nouveau nom", color = TextSecondary) },
                                                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                                                            focusedContainerColor = GraphiteCard,
                                                            unfocusedContainerColor = GraphiteCard,
                                                            focusedTextColor = TextPrimary,
                                                            unfocusedTextColor = TextPrimary,
                                                            cursorColor = SilverAccent,
                                                            focusedIndicatorColor = SilverAccent,
                                                            unfocusedIndicatorColor = TextSecondary
                                                        ),
                                                        modifier = Modifier.fillMaxWidth(),
                                                        singleLine = true
                                                    )
                                                    Spacer(modifier = Modifier.height(24.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        Text(
                                                            text = "Annuler",
                                                            color = TextSecondary,
                                                            modifier = Modifier
                                                                .clickable { showRenameDialog = false }
                                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    if (newName.isNotBlank()) SilverAccent else TextSecondary,
                                                                    RoundedCornerShape(12.dp)
                                                                )
                                                                .clickable {
                                                                    if (newName.isNotBlank()) {
                                                                        onPlaylistRename(playlist, newName.trim())
                                                                        showRenameDialog = false
                                                                    }
                                                                }
                                                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                                        ) {
                                                            Text(
                                                                text = "Enregistrer",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    item { Spacer(modifier = Modifier.height(16.dp)) }
                                }
                            }
                        }

                        if (showCreateSheet) {
                            com.credo.soundgroove.ui.components.CreatePlaylistSheet(
                                onDismiss = { showCreateSheet = false },
                                onCreate = { name ->
                                    onPlaylistCreate(name)
                                    showCreateSheet = false
                                }
                            )
                        }
                    }
                }

                4 -> {
                    val folderDetail = selectedFolder
                    if (folderDetail != null) {
                        FolderDetailContent(
                            folderName = folderDetail.first,
                            folderSongs = folderDetail.second,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            favoriteSongs = favoriteSongs,
                            accentColor = accentColor,
                            onBack = { selectedFolder = null },
                            onSongClick = { song -> onPlayPlaylist(song, folderDetail.second) },
                            onPlayAll = {
                                folderDetail.second.firstOrNull()?.let { firstSong ->
                                    onPlayPlaylist(firstSong, folderDetail.second)
                                }
                            },
                            onToggleFavorite = onToggleFavorite,
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onShowSongInfo = onShowSongInfo,
                            onShareCard = onShareCard,
                            onEditMetadata = onEditMetadata,
                            onSetCoverArt = onSetCoverArt,
                            onShowPlaylistPicker = onShowPlaylistPicker
                        )
                    } else if (folders.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Aucun dossier trouvé", color = TextSecondary, fontSize = 16.sp)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hiddenFolders.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "DOSSIERS MASQUÉS",
                                        color = TextTertiary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                                    )
                                }
                                items(hiddenFolders.toList(), key = { "hidden_$it" }) { hiddenPath ->
                                    val hiddenLabel = hiddenPath.substringAfterLast('/').ifBlank { hiddenPath }
                                    GlassCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onUnhideFolder(hiddenPath) },
                                        cornerRadius = 14.dp
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.VisibilityOff,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(hiddenLabel, color = TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("Appuyer pour réafficher", color = accentColor, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(8.dp)) }
                            }
                            items(folders, key = { it.first }) { (folderName, folderSongs) ->
                                val folderLabel = folderName.substringAfterLast('/').ifBlank { "Dossier inconnu" }
                                val parentPath = folderName.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
                                Box {
                                    GlassCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = { selectedFolder = folderName to folderSongs },
                                                onLongClick = {
                                                    folderMenuTarget = folderName
                                                    showHideFolderConfirm = true
                                                }
                                            ),
                                        cornerRadius = 14.dp
                                    ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(GlassSurface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(folderLabel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                text = parentPath ?: "Stockage local",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text("${folderSongs.size} chanson(s)", color = TextSecondary, fontSize = 12.sp)
                                        }
                                        Icon(painter = painterResource(R.drawable.ic_back), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }

                5 -> {
                    if (favoriteSongs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painter = painterResource(R.drawable.ic_favorite_outline), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Aucun favori", color = TextSecondary, fontSize = 16.sp)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(favoriteSongs, key = { it.id }) { song ->
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlayPlaylist(song, favoriteSongs) },
                                    cornerRadius = 14.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        FavoritePink.copy(
                                                            0.1f
                                                        ), Color.Transparent
                                                    )
                                                )
                                            )
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(GraphiteCard),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (song.albumArtUri != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(song.albumArtUri)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(painter = painterResource(R.drawable.ic_songs), contentDescription = null, tint = SilverAccent, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = song.artist,
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                        Icon(
                                            painter = painterResource(R.drawable.ic_favorite_filled),
                                            contentDescription = "Retirer des favoris",
                                            tint = FavoritePink,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clickable { onToggleFavorite(song) }
                                        )
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
                }
            }
        }
    }

    if (showHideFolderConfirm && folderMenuTarget != null) {
        val target = folderMenuTarget!!
        val targetLabel = target.substringAfterLast('/').ifBlank { target }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showHideFolderConfirm = false
                folderMenuTarget = null
            },
            title = { Text("Masquer ce dossier ?", color = TextPrimary) },
            text = {
                Text(
                    "« $targetLabel » sera exclu de la bibliothèque et du scan affiché. Les fichiers restent sur l'appareil.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onHideFolder(target)
                        if (selectedFolder?.first == target) selectedFolder = null
                        showHideFolderConfirm = false
                        folderMenuTarget = null
                    }
                ) {
                    Text("Masquer", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showHideFolderConfirm = false
                        folderMenuTarget = null
                    }
                ) {
                    Text("Annuler", color = TextSecondary)
                }
            },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
fun FolderDetailContent(
    folderName: String,
    folderSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongs: List<Song>,
    accentColor: Color,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onShowSongInfo: (Song) -> Unit,
    onShareCard: (Song) -> Unit = {},
    onEditMetadata: (Song) -> Unit = {},
    onSetCoverArt: (Song, android.net.Uri) -> Unit = { _, _ -> },
    onShowPlaylistPicker: (Song) -> Unit
) {
    var menuSong by remember { mutableStateOf<Song?>(null) }
    val launchCoverPicker = com.credo.soundgroove.ui.components.rememberSongCoverArtPicker(
        onCoverSelected = onSetCoverArt
    )
    val folderLabel = folderName.substringAfterLast('/').ifBlank { "Dossier inconnu" }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Retour",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderLabel,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folderSongs.size} chanson(s)",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SgRadius.pill))
                    .background(Brush.horizontalGradient(listOf(accentColor, themeSecondaryAccent(accentColor))))
                    .clickable(enabled = folderSongs.isNotEmpty()) { onPlayAll() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Tout lire", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(folderSongs, key = { it.id }) { song ->
                com.credo.soundgroove.ui.components.SongListItem(
                    song = song,
                    isFavorite = favoriteSongs.any { it.id == song.id },
                    isCurrentSong = currentSong?.id == song.id && isPlaying,
                    accentColor = accentColor,
                    onClick = { onSongClick(song) },
                    onMenuClick = { menuSong = song }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    menuSong?.let { song ->
        com.credo.soundgroove.ui.components.SongContextMenuSheet(
            song = song,
            isFavorite = favoriteSongs.any { it.id == song.id },
            onToggleFavorite = { onToggleFavorite(song) },
            onPlayNext = { onPlayNext(song); menuSong = null },
            onAddToQueue = { onAddToQueue(song); menuSong = null },
            onAddToPlaylist = { onShowPlaylistPicker(song); menuSong = null },
            onViewInfo = { onShowSongInfo(song); menuSong = null },
            onShareCard = { onShareCard(song); menuSong = null },
            onEditMetadata = { onEditMetadata(song); menuSong = null },
            onSetCoverArt = { launchCoverPicker(song); menuSong = null },
            onDismiss = { menuSong = null }
        )
    }
}

@Composable
fun PlaylistScreen(
    title: String,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(sgFullScreenGradientBrush())
            .pointerInput(Unit) { detectTapGestures { } } // ← absorbe TOUS les taps
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onClose() },  // ← garde le même lambda qu'avant
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "Retour",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${songs.size} chansons",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SilverAccent, RoundedCornerShape(12.dp))
                    .clickable { onPlayAll() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tout jouer",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(songs) { song ->
                    SongItem(
                        song = song,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        onClick = { onSongClick(song) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

