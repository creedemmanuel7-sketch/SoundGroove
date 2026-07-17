package com.credo.soundgroove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.theme.GlassBorder
import com.credo.soundgroove.ui.theme.GlassSurface
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.SurfaceOverlay
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.TextSecondary
import com.credo.soundgroove.ui.theme.themeSecondaryAccent
import com.credo.soundgroove.util.displayArtist
import com.credo.soundgroove.util.displayTitle

/**
 * Picker multi-select pour ajouter des titres à une playlist utilisateur
 * (pas les smart playlists). Recherche + liste bibliothèque + confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToPlaylistSheet(
    playlistName: String,
    librarySongs: List<Song>,
    alreadyInPlaylistIds: Set<Long> = emptySet(),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onConfirm: (List<Song>) -> Unit,
    onDismiss: () -> Unit,
    skipLabel: String = "Plus tard"
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    val selectableSongs = remember(librarySongs, alreadyInPlaylistIds) {
        librarySongs.filter { it.id !in alreadyInPlaylistIds }
    }
    val filteredSongs = remember(selectableSongs, query) {
        val q = query.trim()
        if (q.isEmpty()) selectableSongs
        else {
            val lower = q.lowercase()
            selectableSongs.filter { song ->
                song.displayTitle().lowercase().contains(lower) ||
                    song.displayArtist().lowercase().contains(lower) ||
                    song.albumName.lowercase().contains(lower)
            }
        }
    }
    val selectedCount = selectedIds.size
    val confirmEnabled = selectedCount > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = SgSpacing.lg)
                .padding(bottom = SgSpacing.xxl)
        ) {
            Text(
                "Ajouter des titres",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (playlistName.isBlank()) {
                    "Choisis des morceaux dans ta bibliothèque"
                } else {
                    "Pour « $playlistName »"
                },
                fontSize = 14.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(SgSpacing.md))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Rechercher un titre, artiste…", color = TextSecondary) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_songs),
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                shape = RoundedCornerShape(SgRadius.md),
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

            Spacer(modifier = Modifier.height(SgSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        selectableSongs.isEmpty() && alreadyInPlaylistIds.isNotEmpty() ->
                            "Tous les titres sont déjà dans la playlist"
                        filteredSongs.isEmpty() -> "Aucun résultat"
                        else -> "${filteredSongs.size} titre(s) · $selectedCount sélectionné(s)"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                if (filteredSongs.isNotEmpty()) {
                    val allFilteredSelected = filteredSongs.all { it.id in selectedIds }
                    Text(
                        text = if (allFilteredSelected) "Tout désélectionner" else "Tout sélectionner",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(SgRadius.sm))
                            .clickable {
                                selectedIds = if (allFilteredSelected) {
                                    selectedIds - filteredSongs.map { it.id }.toSet()
                                } else {
                                    selectedIds + filteredSongs.map { it.id }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(SgSpacing.sm))

            if (selectableSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (librarySongs.isEmpty()) {
                            "Ta bibliothèque est vide"
                        } else {
                            "Rien à ajouter pour le moment"
                        },
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = SgSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredSongs, key = { it.id }) { song ->
                        val selected = song.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(SgRadius.md))
                                .background(
                                    if (selected) accentColor.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    selectedIds = if (selected) {
                                        selectedIds - song.id
                                    } else {
                                        selectedIds + song.id
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + song.id
                                    } else {
                                        selectedIds - song.id
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = accentColor,
                                    uncheckedColor = TextSecondary,
                                    checkmarkColor = Color.White
                                )
                            )
                            AlbumArtThumb(
                                albumArtUri = song.albumArtUri,
                                size = 44.dp,
                                cornerRadius = 10.dp,
                                accentColor = accentColor
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.displayTitle(),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.displayArtist(),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SgSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(SgRadius.md))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(SgRadius.md))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(skipLabel, color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(SgRadius.md))
                        .background(
                            if (confirmEnabled) {
                                Brush.horizontalGradient(
                                    listOf(accentColor, themeSecondaryAccent(accentColor))
                                )
                            } else {
                                Brush.horizontalGradient(listOf(GlassSurface, GlassSurface))
                            }
                        )
                        .clickable(enabled = confirmEnabled) {
                            val selected = selectableSongs.filter { it.id in selectedIds }
                            onConfirm(selected)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (confirmEnabled) {
                            if (selectedCount == 1) "Ajouter 1 titre" else "Ajouter $selectedCount titres"
                        } else {
                            "Ajouter"
                        },
                        color = if (confirmEnabled) Color.White else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
