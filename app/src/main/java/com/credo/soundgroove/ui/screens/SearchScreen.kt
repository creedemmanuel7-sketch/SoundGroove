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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
    var recentSearches by remember { mutableStateOf(listOf("Lofi hip hop", "Eminem", "Amapiano 2024", "Piano chill")) }

    val filteredSongs = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) emptyList()
        else allSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
        }.take(20)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(SurfaceOverlay, DeepPurple, Color(0xFF06030C)))
            )
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SgSpacing.md, vertical = SgSpacing.sm),
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
                            "Rechercher des chansons, artistes...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(SgRadius.pill),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor.copy(alpha = 0.6f),
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SurfaceElevated.copy(alpha = 0.8f),
                        unfocusedContainerColor = SurfaceElevated.copy(alpha = 0.5f),
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
                Text(
                    text = "RECHERCHES RÉCENTES",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(horizontal = SgSpacing.xl, vertical = SgSpacing.lg)
                )
                recentSearches.forEach { term ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { searchQuery = term }
                            .padding(horizontal = SgSpacing.xl, vertical = SgSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(SgSpacing.md))
                        Text(term, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }
            } else {
                Text(
                    text = "${filteredSongs.size} résultat(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = SgSpacing.xl, vertical = SgSpacing.sm)
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = SgSpacing.lg, vertical = SgSpacing.sm)
                ) {
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
            }
        }
    }
}
