package com.credo.soundgroove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.theme.*

// ─── Create Playlist Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistSheet(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val suggestions = listOf("Mix du jour", "Détente", "Workout", "Road Trip", "Soirée")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF150B2B),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                "Nouvelle Playlist",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Donne un nom à ta playlist", fontSize = 14.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(20.dp))

            // Text field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Nom de la playlist", color = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LightPurple,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = LightPurple,
                    focusedContainerColor = GlassSurface,
                    unfocusedContainerColor = GlassSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Suggestions chips
            Text("Suggestions", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            // Suggestions chips (grouped in rows of 3)
            suggestions.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (name == suggestion) LightPurple.copy(0.2f) else GlassSurface,
                                    RoundedCornerShape(20.dp)
                                )
                                .border(
                                    1.dp,
                                    if (name == suggestion) LightPurple else GlassBorder,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { name = suggestion }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                suggestion,
                                color = if (name == suggestion) LightPurple else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Create button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        if (name.isNotBlank()) Brush.horizontalGradient(
                            listOf(LightPurple, MediumPurple)
                        ) else Brush.horizontalGradient(
                            listOf(GlassSurface, GlassSurface)
                        ),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(enabled = name.isNotBlank()) {
                        onCreate(name.trim())
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Créer",
                    color = if (name.isNotBlank()) Color.White else TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── Sort Sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        Triple("Nom (A → Z)", R.drawable.ic_sort, "Alphabétique croissant"),
        Triple("Nom (Z → A)", R.drawable.ic_sort, "Alphabétique décroissant"),
        Triple("Artiste", R.drawable.ic_profile, "Par nom d'artiste"),
        Triple("Récemment ajouté", R.drawable.ic_songs, "Les plus récents en premier")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Trier par",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            options.forEachIndexed { idx, (label, iconRes, desc) ->
                val isSelected = currentMode == idx
                ListItem(
                    headlineContent = {
                        Text(
                            label,
                            color = if (isSelected) LightPurple else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    supportingContent = { Text(desc, color = TextSecondary, fontSize = 12.sp) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = if (isSelected) LightPurple else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(LightPurple, CircleShape)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) LightPurple.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onModeSelected(idx); onDismiss() }
                )
            }
        }
    }
}

// ─── Add To Playlist Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: Song,
    playlists: List<Playlist>,
    onAddToPlaylist: (Playlist) -> Unit,
    onCreateAndAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF150B2B),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                "Ajouter à une playlist",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(song.title, fontSize = 13.sp, color = TextSecondary, maxLines = 1)
            Spacer(modifier = Modifier.height(20.dp))

            // Create new button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightPurple.copy(0.12f))
                    .border(1.dp, LightPurple.copy(0.3f), RoundedCornerShape(12.dp))
                    .clickable { onCreateAndAdd(); onDismiss() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(LightPurple, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text("Créer une nouvelle playlist", color = LightPurple, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            if (playlists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(modifier = Modifier.height(12.dp))

                playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddToPlaylist(playlist); onDismiss() }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cover art grid or placeholder
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    Brush.radialGradient(listOf(LightPurple, MediumPurple)),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val firstSong = playlist.songs.firstOrNull()
                            if (firstSong?.albumArtUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(firstSong.albumArtUri).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_songs),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${playlist.songs.size} chanson(s)", color = TextSecondary, fontSize = 12.sp)
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            tint = LightPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Aucune playlist existante",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─── Song Context Menu Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongContextMenuSheet(
    song: Song,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onViewInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF150B2B),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
        ) {
            // Song header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(song.artist, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
                }
            }

            HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

            val actions = listOf(
                Triple(
                    if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                    if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline,
                    onToggleFavorite
                ),
                Triple("Jouer ensuite", R.drawable.ic_play, onPlayNext),
                Triple("Ajouter à une playlist", R.drawable.ic_add, onAddToPlaylist),
                Triple("Infos sur la chanson", R.drawable.ic_songs, onViewInfo)
            )

            actions.forEach { (label, iconRes, action) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { action(); onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (label.contains("favoris")) Color(0xFFFF6B9D) else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        label,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
