package com.credo.soundgroove.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.SongListItem
import com.credo.soundgroove.ui.theme.*

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
    onPlaySong: (Song) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onMenuClick: (Song) -> Unit,
    onSearchSubmitted: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun submitSearch() {
        val trimmed = searchQuery.trim()
        if (trimmed.isNotBlank()) {
            onSearchSubmitted(trimmed)
        }
        focusManager.clearFocus()
    }

    val filteredSongs = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) emptyList()
        else allSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.albumName.contains(searchQuery, ignoreCase = true)
        }.take(20)
    }

    val filteredAlbums = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) emptyList()
        else allSongs.groupBy { it.albumName }
            .filterKeys { it.contains(searchQuery, ignoreCase = true) }
            .toList()
            .sortedBy { it.first }
            .take(8)
    }

    val filteredArtists = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) emptyList()
        else allSongs.groupBy { it.artist }
            .filterKeys { it.contains(searchQuery, ignoreCase = true) }
            .map { it.key to it.value.size }
            .sortedBy { it.first }
            .take(8)
    }

    val filteredPlaylists = remember(searchQuery, playlists) {
        if (searchQuery.isBlank()) emptyList()
        else playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(8)
    }

    val hasResults = filteredSongs.isNotEmpty() ||
        filteredAlbums.isNotEmpty() ||
        filteredArtists.isNotEmpty() ||
        filteredPlaylists.isNotEmpty()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(SurfaceOverlay, GraphiteAbyss, Color(0xFF060606)))
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
                            "Titres, albums, artistes, playlists...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    },
                    singleLine = true,
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
                        cursorColor = accentColor
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

            if (searchQuery.isBlank()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = SgSpacing.lg, vertical = SgSpacing.md)
                ) {
                    item {
                        Text(
                            text = "Recherche",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(SgSpacing.xs))
                        Text(
                            text = "Trouve rapidement une chanson, un album, un artiste ou une playlist.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
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
                                text = "Vos recherches apparaîtront ici.",
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
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(horizontal = SgSpacing.xl),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(44.dp))
                                    Spacer(modifier = Modifier.height(SgSpacing.md))
                                    Text("Aucun résultat", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Essaie avec un autre titre, artiste ou album.", color = TextSecondary)
                                }
                            }
                        }
                    }

                    if (filteredSongs.isNotEmpty()) {
                        item { SectionTitle("${filteredSongs.size} chanson(s)") }
                        items(filteredSongs, key = { it.id }) { song ->
                            SongListItem(
                                song = song,
                                isFavorite = favoriteSongs.any { it.id == song.id },
                                isCurrentSong = currentSong?.id == song.id,
                                accentColor = accentColor,
                                onClick = { onPlaySong(song) },
                                onMenuClick = { onMenuClick(song) }
                            )
                        }
                    }

                    if (filteredAlbums.isNotEmpty()) {
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

                    if (filteredArtists.isNotEmpty()) {
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

                    if (filteredPlaylists.isNotEmpty()) {
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

                    item { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }
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
    iconRes: Int,
    accentColor: Color,
    onClick: () -> Unit
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
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
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
