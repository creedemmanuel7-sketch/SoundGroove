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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.SongListItem
import com.credo.soundgroove.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    allSongs: List<Song>,
    favoriteSongs: List<Song>,
    currentSong: Song?,
    accentColor: Color,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onMenuClick: (Song) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Fake recent searches for now (can be persisted in DB later)
    var recentSearches by remember { mutableStateOf(listOf("Lofi hip hop", "Eminem", "Amapiano 2024", "Piano chill")) }

    val filteredSongs = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) emptyList()
        else allSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }.take(20) // limit results for performance
    }

    LaunchedEffect(Unit) {
        // Auto focus search bar when screen opens
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0512))
            .statusBarsPadding()
    ) {
        // Search Bar Top Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
            }
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Rechercher des chansons, artistes...", color = TextSecondary, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassSurface,
                    unfocusedContainerColor = GlassSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = accentColor
                ),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = TextSecondary)
                        }
                    }
                }
            )
        }

        // Content Area
        if (searchQuery.isBlank()) {
            // Recent Searches
            if (recentSearches.isNotEmpty()) {
                Text(
                    text = "Recherches récentes",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
                LazyColumn {
                    items(recentSearches) { searchItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { searchQuery = searchItem }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(searchItem, color = TextSecondary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Clear, contentDescription = "Retirer", tint = TextSecondary.copy(0.5f), modifier = Modifier
                                .size(20.dp)
                                .clickable { recentSearches = recentSearches - searchItem })
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Recherchez vos musiques préférées", color = TextSecondary)
                }
            }
        } else {
            // Search Results
            if (filteredSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.TopCenter) {
                    Text("Aucun résultat pour \"$searchQuery\"", color = TextSecondary)
                }
            } else {
                LazyColumn {
                    items(filteredSongs) { song ->
                        SongListItem(
                            song = song,
                            isFavorite = favoriteSongs.any { it.id == song.id },
                            isCurrentSong = currentSong?.id == song.id,
                            accentColor = accentColor,
                            onClick = { 
                                focusManager.clearFocus()
                                if (!recentSearches.contains(searchQuery)) {
                                    recentSearches = listOf(searchQuery) + recentSearches.take(9)
                                }
                                onPlaySong(song) 
                            },
                            onMenuClick = { onMenuClick(song) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(100.dp)) } // padding for miniplayer
                }
            }
        }
    }
}
