package com.credo.soundgroove.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.SgEmptyState
import com.credo.soundgroove.ui.components.SongListItem
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.util.MediaPermissions
import com.credo.soundgroove.viewmodel.SearchFilter
import com.credo.soundgroove.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    allSongs: List<Song>,
    playlists: List<Playlist>,
    favoriteSongs: List<Song>,
    currentSong: Song?,
    accentColor: Color,
    recentSearches: List<String> = emptyList(),
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onFolderClick: (String) -> Unit,
    onMenuClick: (Song) -> Unit,
    onSearchSubmitted: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    searchViewModel: SearchViewModel = viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<SearchFilter?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(MediaPermissions.hasAudioReadPermission(context))
    }
    val audioPermission = remember { MediaPermissions.audioReadPermission() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    fun submitSearch() {
        val trimmed = searchQuery.trim()
        if (trimmed.isNotBlank()) {
            onSearchSubmitted(trimmed)
        }
        focusManager.clearFocus()
    }

    fun folderLabel(folderPath: String): String = searchViewModel.folderLabel(folderPath)

    val searchResults = remember(searchQuery, allSongs, playlists, selectedFilter) {
        val raw = searchViewModel.buildResults(searchQuery.trim(), allSongs, playlists)
        searchViewModel.filterResults(raw, selectedFilter)
    }

    val filteredSongs = searchResults.songs
    val filteredAlbums = searchResults.albums
    val filteredArtists = searchResults.artists
    val filteredPlaylists = searchResults.playlists
    val filteredFolders = searchResults.folders

    val showSongs = selectedFilter == null || selectedFilter == SearchFilter.Songs
    val showAlbums = selectedFilter == null || selectedFilter == SearchFilter.Albums
    val showArtists = selectedFilter == null || selectedFilter == SearchFilter.Artists
    val showPlaylists = selectedFilter == null || selectedFilter == SearchFilter.Playlists
    val showFolders = selectedFilter == null || selectedFilter == SearchFilter.Folders

    val hasResults = (showSongs && filteredSongs.isNotEmpty()) ||
        (showAlbums && filteredAlbums.isNotEmpty()) ||
        (showArtists && filteredArtists.isNotEmpty()) ||
        (showPlaylists && filteredPlaylists.isNotEmpty()) ||
        (showFolders && filteredFolders.isNotEmpty())

    LaunchedEffect(Unit) {
        if (hasPermission) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        SurfaceOverlay,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SgSpacing.sm, vertical = SgSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SgIconButton(onClick = onBack, accentColor = accentColor) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(SgSpacing.sm))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            "Titres, albums, artistes, playlists, dossiers…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    },
                    singleLine = true,
                    enabled = hasPermission,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    shape = RoundedCornerShape(SgRadius.pill),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor.copy(alpha = 0.42f),
                        unfocusedBorderColor = BorderSubtle.copy(alpha = 0.45f),
                        focusedContainerColor = SurfaceElevated.copy(alpha = 0.52f),
                        unfocusedContainerColor = SurfaceElevated.copy(alpha = 0.32f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = accentColor,
                        disabledTextColor = TextTertiary,
                        disabledBorderColor = BorderSubtle.copy(alpha = 0.25f),
                        disabledContainerColor = SurfaceElevated.copy(alpha = 0.18f)
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Effacer", tint = TextSecondary)
                            }
                        }
                    }
                )
            }

            if (!hasPermission) {
                SgEmptyState(
                    icon = Icons.Default.Lock,
                    title = "Accès à la musique requis",
                    subtitle = "Autorisez SoundGroove à lire vos fichiers audio pour rechercher dans votre bibliothèque.",
                    actionLabel = "Accorder la permission",
                    accentColor = accentColor,
                    onAction = { permissionLauncher.launch(audioPermission) }
                )
            } else {
                SearchFilterRow(
                    selectedFilter = selectedFilter,
                    accentColor = accentColor,
                    onFilterSelected = { selectedFilter = it }
                )

                if (searchQuery.isBlank()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = SgSpacing.lg, vertical = SgSpacing.md)
                    ) {
                        item {
                            SgEmptyState(
                                icon = Icons.Default.Search,
                                title = "Rechercher dans votre bibliothèque",
                                subtitle = if (selectedFilter != null) {
                                    "Filtre « ${selectedFilter!!.label} » actif — saisissez un mot-clé."
                                } else {
                                    "Saisissez un titre, un album, un artiste, une playlist ou un dossier."
                                },
                                compact = true,
                                accentColor = accentColor
                            )
                            Spacer(modifier = Modifier.height(SgSpacing.lg))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RECHERCHES RÉCENTES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                    modifier = Modifier.padding(horizontal = SgSpacing.sm)
                                )
                                if (recentSearches.isNotEmpty()) {
                                    TextButton(onClick = onClearSearchHistory) {
                                        Text("Effacer", color = accentColor, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                        if (recentSearches.isEmpty()) {
                            item {
                                Text(
                                    text = "Vos recherches récentes apparaîtront ici.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = SgSpacing.md)
                                )
                            }
                        }
                        items(recentSearches) { term ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = term
                                        submitSearch()
                                    }
                                    .padding(vertical = SgSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.History, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(SgSpacing.md))
                                Text(term, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = SgSpacing.sm, vertical = SgSpacing.xs)
                    ) {
                    if (!hasResults) {
                        item {
                            SgEmptyState(
                                icon = Icons.Default.SearchOff,
                                title = "Aucun résultat pour « $searchQuery »",
                                subtitle = when (selectedFilter) {
                                    SearchFilter.Songs -> "Essayez un autre titre ou artiste."
                                    SearchFilter.Albums -> "Aucun album ne correspond à cette recherche."
                                    SearchFilter.Artists -> "Aucun artiste ne correspond à cette recherche."
                                    SearchFilter.Playlists -> "Aucune playlist ne correspond à cette recherche."
                                    SearchFilter.Folders -> "Aucun dossier ne correspond à cette recherche."
                                    null -> "Essayez un autre mot-clé ou changez de filtre."
                                },
                                compact = true
                            )
                        }
                    }

                    if (showSongs && filteredSongs.isNotEmpty()) {
                        item { SectionTitle("${filteredSongs.size} chanson(s)") }
                        items(filteredSongs, key = { it.id }) { song ->
                            SongListItem(
                                song = song,
                                isFavorite = favoriteSongs.any { it.id == song.id },
                                isCurrentSong = currentSong?.id == song.id,
                                accentColor = accentColor,
                                onClick = { onPlaySong(song, filteredSongs) },
                                onMenuClick = { onMenuClick(song) }
                            )
                        }
                    }

                    if (showAlbums && filteredAlbums.isNotEmpty()) {
                        item { SectionTitle("Albums") }
                        items(filteredAlbums, key = { it.first }) { (albumName, albumSongs) ->
                            SearchEntityRow(
                                title = albumName,
                                subtitle = "${albumSongs.size} titre(s)",
                                iconRes = R.drawable.ic_playlists,
                                accentColor = accentColor,
                                onClick = { onAlbumClick(albumName) }
                            )
                        }
                    }

                    if (showArtists && filteredArtists.isNotEmpty()) {
                        item { SectionTitle("Artistes") }
                        items(filteredArtists, key = { it.first }) { (artistName, songCount) ->
                            SearchEntityRow(
                                title = artistName,
                                subtitle = "$songCount titre(s)",
                                iconRes = R.drawable.ic_profile,
                                accentColor = accentColor,
                                onClick = { onArtistClick(artistName) }
                            )
                        }
                    }

                    if (showPlaylists && filteredPlaylists.isNotEmpty()) {
                        item { SectionTitle("Playlists") }
                        items(filteredPlaylists, key = { it.id }) { playlist ->
                            SearchEntityRow(
                                title = playlist.name,
                                subtitle = "${playlist.songs.size} chanson(s)",
                                iconRes = R.drawable.ic_queue,
                                accentColor = accentColor,
                                onClick = { onPlaylistClick(playlist.id) }
                            )
                        }
                    }

                    if (showFolders && filteredFolders.isNotEmpty()) {
                        item { SectionTitle("Dossiers") }
                        items(filteredFolders, key = { it.first }) { (folderPath, folderSongs) ->
                            SearchEntityRow(
                                title = folderLabel(folderPath),
                                subtitle = buildString {
                                    append("${folderSongs.size} chanson(s)")
                                    if (folderPath.contains('/')) {
                                        append(" · ")
                                        append(folderPath.substringBeforeLast('/'))
                                    }
                                },
                                icon = Icons.Default.Folder,
                                accentColor = accentColor,
                                onClick = { onFolderClick(folderPath) }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }
        }
        }
    }
}

@Composable
private fun SearchFilterRow(
    selectedFilter: SearchFilter?,
    accentColor: Color,
    onFilterSelected: (SearchFilter?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SgSpacing.md, vertical = SgSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm)
    ) {
        item {
            SgChip(
                text = "Tout",
                selected = selectedFilter == null,
                accentColor = accentColor,
                onClick = { onFilterSelected(null) }
            )
        }
        items(SearchFilter.entries) { filter ->
            SgChip(
                text = filter.label,
                selected = selectedFilter == filter,
                accentColor = accentColor,
                onClick = {
                    onFilterSelected(if (selectedFilter == filter) null else filter)
                }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = Modifier.padding(horizontal = SgSpacing.sm, vertical = SgSpacing.md)
    )
}

@Composable
private fun SearchEntityRow(
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    iconRes: Int? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = SgSpacing.sm, vertical = SgSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(SgRadius.md)),
            contentAlignment = Alignment.Center
        ) {
            when {
                icon != null -> Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                iconRes != null -> Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(SgSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
