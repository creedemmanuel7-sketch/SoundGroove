package com.credo.soundgroove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.graphicsLayer
import com.credo.soundgroove.util.EqualizerBandInfo
import com.credo.soundgroove.util.EqualizerPreset
import com.credo.soundgroove.util.PlaybackPreferences
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
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "Nouvelle Playlist",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Donne un nom à ta playlist", fontSize = 14.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(16.dp))

            // Text field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Nom de la playlist", color = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = MaterialTheme.colorScheme.primary,
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
                                    if (name == suggestion) MaterialTheme.colorScheme.primary.copy(0.2f) else GlassSurface,
                                    RoundedCornerShape(20.dp)
                                )
                                .border(
                                    1.dp,
                                    if (name == suggestion) MaterialTheme.colorScheme.primary else GlassBorder,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { name = suggestion }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                suggestion,
                                color = if (name == suggestion) MaterialTheme.colorScheme.primary else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Create button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        if (name.isNotBlank()) Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary, GraphiteMid)
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
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 24.dp)
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
                            color = if (isSelected) MaterialTheme.colorScheme.primary else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    supportingContent = { Text(desc, color = TextSecondary, fontSize = 12.sp) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
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
    val manualPlaylists = playlists.filter { !it.isSmart }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp)
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
                    .background(MaterialTheme.colorScheme.primary.copy(0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(0.18f), RoundedCornerShape(12.dp))
                    .clickable { onCreateAndAdd(); onDismiss() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text("Créer une nouvelle playlist", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            if (manualPlaylists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(modifier = Modifier.height(12.dp))

                manualPlaylists.forEach { playlist ->
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
                                    Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary, GraphiteMid)),
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
                            tint = MaterialTheme.colorScheme.primary,
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
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit,
    onViewInfo: () -> Unit,
    onShareCard: () -> Unit = {},
    onEditMetadata: () -> Unit = {},
    onSetCoverArt: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 26.dp)
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
                        .background(GraphiteCard),
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
                Triple("Ajouter à la file", R.drawable.ic_queue, onAddToQueue),
                Triple("Ajouter à une playlist", R.drawable.ic_add, onAddToPlaylist),
                Triple("Infos sur la chanson", R.drawable.ic_songs, onViewInfo),
                Triple("Partager la carte", R.drawable.ic_options, onShareCard),
                Triple("Modifier métadonnées", R.drawable.ic_settings, onEditMetadata),
                Triple("Choisir une pochette", R.drawable.ic_add, onSetCoverArt)
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
                        tint = if (label.contains("favoris")) FavoritePink else TextSecondary,
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

// ─── Sleep Timer Sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    accentColor: Color,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onCancel: () -> Unit
) {
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
            Text(
                "Minuterie de sommeil",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "La lecture s'arrêtera automatiquement",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(20.dp))

            val options = listOf(
                15 to "15 minutes",
                30 to "30 minutes",
                60 to "60 minutes"
            )
            options.forEach { (minutes, label) ->
                SleepTimerOption(
                    label = label,
                    accentColor = accentColor,
                    onClick = { onSelectMinutes(minutes); onDismiss() }
                )
            }
            SleepTimerOption(
                label = "Fin de la piste en cours",
                accentColor = accentColor,
                onClick = { onSelectEndOfTrack(); onDismiss() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SleepTimerOption(
                label = "Annuler la minuterie",
                accentColor = TextSecondary,
                onClick = { onCancel(); onDismiss() }
            )
        }
    }
}

@Composable
private fun SleepTimerOption(
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.75f else 0.55f,
        animationSpec = SgMotion.tweenFast(),
        label = "sleepOptionBg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sgPressScale(interactionSource, pressedScale = 0.98f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .background(GlassSurface.copy(alpha = bgAlpha))
            .border(1.dp, GlassBorder.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play),
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
    Spacer(modifier = Modifier.height(8.dp))
}

// ─── Song Info Sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoBottomSheet(
    song: Song,
    accentColor: Color,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onSetRingtone: () -> Unit,
    onShareCard: () -> Unit = onShare,
    onEditMetadata: () -> Unit = {},
    onSetCoverArt: () -> Unit = {},
    onDismiss: () -> Unit
) {
    // Le `ModalBottomSheet` M3 gère déjà nativement tenir/glisser/annuler (son
    // `SheetState` interne règle seul le spring-back si le drag est insuffisant,
    // cf. SgMotion.sheetContentEnterSpec) : on ne touche pas à cette mécanique.
    // Ce qu'on ajoute ici, c'est une apparition plus douce du CONTENU (scale+fade
    // court) pour éviter l'effet "pop" instantané dès que la sheet devient visible.
    val reducedMotion = rememberSgReducedMotion()
    val contentScale = remember { Animatable(if (reducedMotion) 1f else SgMotion.SheetContentInitialScale) }
    val contentAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            launch { contentScale.animateTo(1f, animationSpec = SgMotion.sheetContentEnterSpec()) }
            launch { contentAlpha.animateTo(1f, animationSpec = SgMotion.sheetContentEnterSpec()) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = contentScale.value
                    scaleY = contentScale.value
                    alpha = contentAlpha.value
                }
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            Text(
                "Informations",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GraphiteCard),
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
                        Icon(Icons.Filled.MusicNote, null, tint = accentColor, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(song.artist, color = accentColor, fontSize = 14.sp, maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))
            SongInfoRow(Icons.Filled.Person, "Artiste", song.artist, accentColor)
            SongInfoRow(Icons.Filled.MusicNote, "Titre", song.title, accentColor)
            SongInfoRow(Icons.Filled.Album, "Album", song.albumName, accentColor)
            SongInfoRow(Icons.Filled.Timer, "Durée", formatDuration(song.duration), accentColor)
            if (song.folderPath.isNotBlank()) {
                SongInfoRow(Icons.Filled.Folder, "Dossier", song.folderPath, accentColor)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoActionChip(
                    label = if (isFavorite) "Favori" else "Favoris",
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    tint = FavoritePink,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleFavorite
                )
                InfoActionChip(
                    label = "Partager",
                    icon = Icons.Filled.Share,
                    tint = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = onShare
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoActionChip(
                    label = "Carte",
                    icon = Icons.Filled.Image,
                    tint = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = onShareCard
                )
                InfoActionChip(
                    label = "Éditer",
                    icon = Icons.Filled.Edit,
                    tint = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = onEditMetadata
                )
                InfoActionChip(
                    label = "Pochette",
                    icon = Icons.Filled.Image,
                    tint = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = onSetCoverArt
                )
                InfoActionChip(
                    label = "Sonnerie",
                    icon = Icons.Filled.Notifications,
                    tint = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = onSetRingtone
                )
            }
        }
    }
}

@Composable
private fun SongInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accentColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp, maxLines = 2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface.copy(alpha = 0.5f))
            .border(1.dp, GlassBorder.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextPrimary, fontSize = 11.sp)
    }
}

// ─── Playback Speed + Pitch Sheet ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedBottomSheet(
    currentSpeed: Float,
    currentPitch: Float = 1f,
    accentColor: Color,
    onSpeedSelected: (Float) -> Unit,
    onPitchSelected: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val pitches = listOf(0.75f, 0.875f, 1.0f, 1.125f, 1.25f, 1.5f)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            Text(
                "Vitesse et tonalité",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Vitesse : ${formatSpeedLabel(currentSpeed)} · Tonalité : ${formatPitchLabel(currentPitch)}",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Vitesse", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            speeds.forEach { speed ->
                val isSelected = kotlin.math.abs(speed - currentSpeed) < 0.01f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSpeedSelected(speed) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatSpeedLabel(speed),
                        color = if (isSelected) accentColor else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(Icons.Filled.Check, null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Tonalité (pitch)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Indépendante de la vitesse (Media3 setPitch)",
                color = TextTertiary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            pitches.forEach { pitch ->
                val isSelected = kotlin.math.abs(pitch - currentPitch) < 0.01f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onPitchSelected(pitch) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatPitchLabel(pitch),
                        color = if (isSelected) accentColor else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(Icons.Filled.Check, null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossfadeBottomSheet(
    currentMs: Int,
    gaplessEnabled: Boolean,
    accentColor: Color,
    onDurationSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = PlaybackPreferences.CROSSFADE_OPTIONS_MS
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            Text(
                "Crossfade",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Fondu enchaîné via le volume — courbe ease-in-out",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            PlaybackModeIndicator(
                gaplessEnabled = gaplessEnabled,
                crossfadeMs = currentMs,
                accentColor = accentColor
            )
            Spacer(modifier = Modifier.height(16.dp))
            options.forEach { ms ->
                val isSelected = ms == currentMs
                val label = PlaybackPreferences.crossfadeLabel(ms)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onDurationSelected(ms); onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        label,
                        color = if (isSelected) accentColor else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(Icons.Filled.Check, null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PlaybackModeIndicator(
    gaplessEnabled: Boolean,
    crossfadeMs: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    // Badge cliquable depuis PlayerScreen (ouvre CrossfadeBottomSheet) ; laissé à
    // `null` là où l'indicateur reste purement informatif (ex. dans CrossfadeBottomSheet
    // lui-même, pas de sens à se ré-ouvrir soi-même).
    onClick: (() -> Unit)? = null
) {
    val label = PlaybackPreferences.playbackModeLabel(gaplessEnabled, crossfadeMs)
    val icon = when {
        crossfadeMs > 0 -> Icons.Filled.BlurLinear
        gaplessEnabled -> Icons.Filled.SkipNext
        else -> Icons.Filled.Pause
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(accentColor.copy(alpha = 0.1f))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerBottomSheet(
    enabled: Boolean,
    preset: EqualizerPreset,
    bands: List<EqualizerBandInfo>,
    accentColor: Color,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (EqualizerPreset) -> Unit,
    onBandLevelChange: (Int, Short) -> Unit,
    onDismiss: () -> Unit
) {
    val presetOptions = EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Égaliseur",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (bands.isEmpty()) {
                            "En attente de lecture audio…"
                        } else {
                            "${bands.size} bandes · ${preset.label}"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = GlassSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Presets", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetOptions.forEach { option ->
                    val selected = preset == option
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) accentColor.copy(alpha = 0.18f)
                                else GlassSurface.copy(alpha = 0.35f)
                            )
                            .border(
                                1.dp,
                                if (selected) accentColor.copy(alpha = 0.5f) else GlassBorder.copy(alpha = 0.3f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { onPresetSelected(option) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            option.label,
                            color = if (selected) accentColor else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            if (bands.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text("Bandes", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    bands.forEach { band ->
                        EqualizerBandSlider(
                            band = band,
                            accentColor = accentColor,
                            enabled = enabled,
                            onLevelChange = { onBandLevelChange(band.index.toInt(), it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerBandSlider(
    band: EqualizerBandInfo,
    accentColor: Color,
    enabled: Boolean,
    onLevelChange: (Short) -> Unit
) {
    val range = band.maxLevelMillibels - band.minLevelMillibels
    val normalized = if (range == 0) {
        0.5f
    } else {
        (band.levelMillibels - band.minLevelMillibels).toFloat() / range.toFloat()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(44.dp)
    ) {
        Box(
            modifier = Modifier
                .height(150.dp)
                .width(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = normalized.coerceIn(0f, 1f),
                onValueChange = { value ->
                    val level = (band.minLevelMillibels + value * range).toInt()
                        .coerceIn(band.minLevelMillibels.toInt(), band.maxLevelMillibels.toInt())
                        .toShort()
                    onLevelChange(level)
                },
                enabled = enabled,
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = -90f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                    .height(36.dp)
                    .width(150.dp)
                    .offset(x = 57.dp),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = GlassBorder.copy(alpha = 0.4f)
                )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            formatFrequencyLabel(band.centerFrequencyHz),
            color = TextTertiary,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

private fun formatFrequencyLabel(hz: Int): String = when {
    hz >= 1000 -> "${hz / 1000}k"
    else -> "$hz"
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataBottomSheet(
    song: Song,
    accentColor: Color,
    onSave: (title: String, artist: String, album: String) -> Unit,
    onSetCoverArt: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.albumName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SgSpacing.lg)
                .padding(bottom = SgSpacing.xxl)
        ) {
            Text(
                "Tags & pochette",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(SgSpacing.xs))
            Text(
                "Modifie les métadonnées et la pochette en un seul endroit",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(SgSpacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SgRadius.md))
                    .background(GlassSurface.copy(alpha = 0.4f))
                    .border(1.dp, GlassBorder.copy(alpha = 0.35f), RoundedCornerShape(SgRadius.md))
                    .clickable(onClick = onSetCoverArt)
                    .padding(SgSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(SgRadius.sm))
                        .background(GraphiteCard),
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
                        Icon(Icons.Filled.MusicNote, null, tint = accentColor, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.width(SgSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pochette", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Appuyer pour choisir une image", color = TextSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Filled.Image, null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(SgSpacing.md))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artiste") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = album,
                onValueChange = { album = it },
                label = { Text("Album") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor)
                    .clickable {
                        onSave(title.trim(), artist.trim(), album.trim())
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Enregistrer", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatSpeedLabel(speed: Float): String {
    return if (speed == speed.toLong().toFloat()) "${speed.toLong()}x" else "${speed}x"
}

private fun formatPitchLabel(pitch: Float): String {
    return if (pitch == pitch.toLong().toFloat()) "${pitch.toLong()}x" else "${pitch}x"
}

// ─── Player Options Sheet (declutter Player plein écran) ────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerOptionsBottomSheet(
    accentColor: Color,
    gaplessEnabled: Boolean,
    crossfadeDurationMs: Int,
    sleepTimerRemainingSeconds: Int?,
    playbackSpeed: Float,
    playbackPitch: Float,
    equalizerEnabled: Boolean,
    equalizerPresetLabel: String,
    vinylModeEnabled: Boolean,
    onOpenCrossfade: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenPlaybackSpeed: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onToggleVinylMode: () -> Unit,
    onShowInfo: () -> Unit,
    onShare: () -> Unit,
    onShareCard: () -> Unit,
    onEditMetadata: () -> Unit,
    onSetRingtone: () -> Unit,
    onDismiss: () -> Unit
) {
    val reducedMotion = rememberSgReducedMotion()
    val contentScale = remember { Animatable(if (reducedMotion) 1f else SgMotion.SheetContentInitialScale) }
    val contentAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            launch { contentScale.animateTo(1f, animationSpec = SgMotion.sheetContentEnterSpec()) }
            launch { contentAlpha.animateTo(1f, animationSpec = SgMotion.sheetContentEnterSpec()) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceOverlay.copy(alpha = 0.96f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = contentScale.value
                    scaleY = contentScale.value
                    alpha = contentAlpha.value
                }
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "Options de lecture",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Transition, audio, affichage et actions",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Transition", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val crossfadeActive = crossfadeDurationMs > 0
                val playbackModeActive = crossfadeActive || !gaplessEnabled
                PlayerOptionCompactChip(
                    icon = if (crossfadeActive) Icons.Filled.BlurLinear else Icons.Filled.SkipNext,
                    label = PlaybackPreferences.playbackModeLabel(gaplessEnabled, crossfadeDurationMs),
                    active = playbackModeActive,
                    accentColor = accentColor,
                    onClick = { onDismiss(); onOpenCrossfade() },
                    modifier = Modifier.weight(1f)
                )
                val sleepActive = sleepTimerRemainingSeconds != null
                PlayerOptionCompactChip(
                    icon = Icons.Filled.Bedtime,
                    label = com.credo.soundgroove.ui.screens.formatSleepTimerDisplay(sleepTimerRemainingSeconds)
                        ?: "Minuterie",
                    active = sleepActive,
                    accentColor = accentColor,
                    onClick = { onDismiss(); onOpenSleepTimer() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Audio", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            val speedPitchActive = playbackSpeed != 1f || playbackPitch != 1f
            PlayerOptionsListRow(
                icon = Icons.Filled.Speed,
                label = "Vitesse et tonalité",
                detail = "${formatSpeedLabel(playbackSpeed)} · ${formatPitchLabel(playbackPitch)}",
                active = speedPitchActive,
                accentColor = accentColor,
                onClick = { onDismiss(); onOpenPlaybackSpeed() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            PlayerOptionsListRow(
                icon = Icons.Filled.GraphicEq,
                label = "Égaliseur",
                detail = if (equalizerEnabled) equalizerPresetLabel else "Désactivé",
                active = equalizerEnabled,
                accentColor = accentColor,
                onClick = { onDismiss(); onOpenEqualizer() }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Affichage", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (vinylModeEnabled) accentColor.copy(alpha = 0.1f) else GlassSurface.copy(alpha = 0.45f))
                    .border(
                        1.dp,
                        if (vinylModeEnabled) accentColor.copy(alpha = 0.35f) else GlassBorder.copy(alpha = 0.35f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Album, null, tint = if (vinylModeEnabled) accentColor else TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Mode vinyle", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Pochette en disque tournant", color = TextSecondary, fontSize = 11.sp)
                    }
                }
                Switch(
                    checked = vinylModeEnabled,
                    onCheckedChange = { onToggleVinylMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = GlassSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Actions", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            val actionRows = listOf(
                Triple(Icons.Filled.Share, "Partager", onShare),
                Triple(Icons.Filled.Image, "Partager la carte", onShareCard),
                Triple(Icons.Filled.Info, "Informations", onShowInfo),
                Triple(Icons.Filled.Edit, "Modifier métadonnées", onEditMetadata),
                Triple(Icons.Filled.Notifications, "Définir comme sonnerie", onSetRingtone)
            )
            actionRows.forEach { (icon, label, action) ->
                PlayerOptionsListRow(
                    icon = icon,
                    label = label,
                    detail = null,
                    active = false,
                    accentColor = accentColor,
                    onClick = { onDismiss(); action() }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun PlayerOptionCompactChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accentColor.copy(alpha = 0.14f) else GlassSurface.copy(alpha = 0.45f))
            .border(
                1.dp,
                if (active) accentColor.copy(alpha = 0.4f) else GlassBorder.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) accentColor else TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (active) accentColor else TextPrimary,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerOptionsListRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String?,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) accentColor.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (active) accentColor else TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (detail != null) {
                    Text(detail, color = if (active) accentColor else TextSecondary, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
    }
}
