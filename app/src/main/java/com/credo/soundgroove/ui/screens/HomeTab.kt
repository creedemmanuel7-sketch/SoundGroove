package com.credo.soundgroove.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.SongItem
import com.credo.soundgroove.ui.components.SgEmptyState
import com.credo.soundgroove.ui.theme.CardSurface
import com.credo.soundgroove.ui.theme.GlassBorder
import com.credo.soundgroove.ui.theme.GlassCard
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.TextSecondary
import com.credo.soundgroove.ui.theme.TextTertiary
import com.credo.soundgroove.ui.theme.themeSecondaryAccent
import com.credo.soundgroove.viewmodel.ContinueListening
import com.credo.soundgroove.viewmodel.HomeViewModel
import java.util.Calendar

private object LibrarySection {
    const val PLAYLISTS = 3
    const val FOLDERS = 4
    const val FAVORITES = 5
}

@Composable
fun HomeTab(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    recentlyPlayed: List<Song>,
    favoriteSongs: List<Song>,
    playlists: List<Playlist>,
    playbackPosition: Long = 0L,
    playbackQueue: List<Song> = emptyList(),
    onSeeAllRecent: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onShowSongInfo: (Song) -> Unit,
    onShowPlaylistPicker: (Song) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onOpenPlayer: () -> Unit,
    onResumeListening: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNavigateToLibrarySection: (Int) -> Unit = {},
    accentColor: Color,
    secondaryAccent: Color = themeSecondaryAccent(accentColor),
    homeViewModel: HomeViewModel = viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSongs = remember(searchQuery, songs) {
        if (searchQuery.isEmpty()) songs
        else songs.filter { song ->
            song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val foldersCount = remember(songs) {
        songs
            .map { song -> song.folderPath.takeIf { it.isNotBlank() } ?: "Dossier inconnu" }
            .distinct()
            .size
    }

    val homeState = remember(
        songs,
        recentlyPlayed,
        favoriteSongs,
        currentSong,
        isPlaying,
        playbackPosition,
        playbackQueue
    ) {
        homeViewModel.buildUiState(
            songs = songs,
            recentlyPlayed = recentlyPlayed,
            favoriteSongs = favoriteSongs,
            currentSong = currentSong,
            isPlaying = isPlaying,
            playbackPosition = playbackPosition,
            playbackQueue = playbackQueue
        )
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Bonjour"
        hour < 18 -> "Bon après-midi"
        else -> "Bonsoir"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SgSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(SgSpacing.sectionGap)
    ) {
        item {
            Spacer(modifier = Modifier.height(SgSpacing.screenTop))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Prêt à écouter ?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Paramètres",
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onOpenSettings() }
                )
            }
        }

        if (songs.isEmpty()) {
            item {
                SgEmptyState(
                    iconPainter = painterResource(R.drawable.ic_songs),
                    title = "Bibliothèque vide",
                    subtitle = "Importez de la musique sur votre appareil ou vérifiez l'accès aux fichiers audio.",
                    compact = true,
                    accentColor = accentColor,
                    actionLabel = "Ouvrir la bibliothèque",
                    onAction = { onNavigateToLibrarySection(0) }
                )
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = SgRadius.md,
                accentColor = accentColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SgSpacing.md, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onNavigateToSearch() }
                    )
                    Spacer(modifier = Modifier.width(SgSpacing.sm + 2.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Rechercher une chanson...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = accentColor
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        if (searchQuery.isEmpty()) {
            homeState.continueListening?.let { session ->
                item {
                    ContinueListeningSection(
                        session = session,
                        accentColor = accentColor,
                        secondaryAccent = secondaryAccent,
                        onOpenPlayer = onOpenPlayer,
                        onResumeListening = onResumeListening
                    )
                }
            }

            item {
                QuickAccessSection(
                    accentColor = accentColor,
                    favoritesCount = favoriteSongs.size,
                    playlistsCount = playlists.size,
                    foldersCount = foldersCount,
                    onNavigateToLibrarySection = onNavigateToLibrarySection
                )
            }

            if (homeState.mixSuggestions.isNotEmpty()) {
                item {
                    DailyMixHero(
                        songs = homeState.mixSuggestions,
                        accentColor = accentColor,
                        secondaryAccent = secondaryAccent,
                        onPlayAll = {
                            onPlaySong(homeState.mixSuggestions.first(), homeState.mixSuggestions)
                        }
                    )
                }
                item {
                    MixSuggestionsRow(
                        songs = homeState.mixSuggestions,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        onPlaySong = onPlaySong
                    )
                }
            }

            if (homeState.similarSongs.isNotEmpty()) {
                item {
                    DiscoverySectionHeader(
                        title = "SIMILAIRES",
                        subtitle = "Même artiste ou album",
                        actionLabel = "Tout lire",
                        accentColor = accentColor,
                        onAction = {
                            onPlaySong(homeState.similarSongs.first(), homeState.similarSongs)
                        }
                    )
                }
                item {
                    MixSuggestionsRow(
                        songs = homeState.similarSongs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        onPlaySong = onPlaySong
                    )
                }
            }

            if (homeState.localRadio.isNotEmpty()) {
                item {
                    DiscoverySectionHeader(
                        title = "RADIO LOCALE",
                        subtitle = "File aléatoire · ${homeState.localRadio.size} titres",
                        actionLabel = "Lancer",
                        accentColor = accentColor,
                        onAction = {
                            onPlaySong(homeState.localRadio.first(), homeState.localRadio)
                        }
                    )
                }
                item {
                    LocalRadioRow(
                        songs = homeState.localRadio,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        onPlaySong = onPlaySong
                    )
                }
            }

            if (homeState.newAdditions.isNotEmpty()) {
                item {
                    SectionHeader(title = "NOUVEAUX AJOUTS")
                }
                items(homeState.newAdditions, key = { "new-${it.id}" }) { song ->
                    SongItem(
                        song = song,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        onClick = { onPlaySong(song, homeState.newAdditions) },
                        showMenu = true,
                        isFavorite = favoriteSongs.any { it.id == song.id },
                        accentColor = accentColor,
                        onToggleFavorite = { onToggleFavorite(song) },
                        onShowInfo = { onShowSongInfo(song) },
                        onShowPlaylistPicker = { onShowPlaylistPicker(song) },
                        onPlayNow = { onPlaySong(song, homeState.newAdditions) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) }
                    )
                }
            }

            if (recentlyPlayed.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "RÉCEMMENT ÉCOUTÉS", modifier = Modifier.weight(1f))
                        Text(
                            text = "Voir tout",
                            style = MaterialTheme.typography.labelLarge,
                            color = accentColor,
                            modifier = Modifier.clickable { onSeeAllRecent() }
                        )
                    }
                    Spacer(modifier = Modifier.height(SgSpacing.sm + 2.dp))
                    val rows = recentlyPlayed.take(4).chunked(2)
                    rows.forEach { rowSongs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm + 2.dp)
                        ) {
                            rowSongs.forEach { song ->
                                RecentSongTile(
                                    song = song,
                                    accentColor = accentColor,
                                    onClick = { onPlaySong(song, recentlyPlayed) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowSongs.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(SgSpacing.sm + 2.dp))
                    }
                }
            }
        } else {
            item {
                SectionHeader(title = "${filteredSongs.size} RÉSULTAT(S)")
            }
            items(filteredSongs, key = { it.id }) { song ->
                SongItem(
                    song = song,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    onClick = { onPlaySong(song, filteredSongs) },
                    showMenu = true,
                    isFavorite = favoriteSongs.any { it.id == song.id },
                    accentColor = accentColor,
                    onToggleFavorite = { onToggleFavorite(song) },
                    onShowInfo = { onShowSongInfo(song) },
                    onShowPlaylistPicker = { onShowPlaylistPicker(song) },
                    onPlayNow = { onPlaySong(song, filteredSongs) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(SgSpacing.lg)) }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = modifier
    )
}

@Composable
private fun ContinueListeningSection(
    session: ContinueListening,
    accentColor: Color,
    secondaryAccent: Color,
    onOpenPlayer: () -> Unit,
    onResumeListening: () -> Unit
) {
    Column {
        SectionHeader(title = "CONTINUER L'ÉCOUTE")
        Spacer(modifier = Modifier.height(SgSpacing.sm))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = SgRadius.lg,
            accentColor = accentColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(accentColor.copy(alpha = 0.24f), secondaryAccent.copy(alpha = 0.10f))
                        )
                    )
                    .padding(SgSpacing.md + 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtThumb(
                        song = session.song,
                        size = 72.dp,
                        cornerRadius = SgRadius.sm
                    )
                    Spacer(modifier = Modifier.width(SgSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.song.title,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(SgSpacing.xs))
                        Text(
                            text = session.song.artist,
                            color = accentColor,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(SgSpacing.sm))
                        Text(
                            text = session.sessionLabel,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                if (session.isActiveSession && session.progress > 0f) {
                    Spacer(modifier = Modifier.height(SgSpacing.md))
                    LinearProgressIndicator(
                        progress = { session.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(SgRadius.pill)),
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.18f)
                    )
                    if (session.positionLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(SgSpacing.xs))
                        Text(
                            text = session.positionLabel,
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SgSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm)) {
                    ResumeButton(
                        label = when {
                            session.isActiveSession && session.isPlaying -> "Ouvrir le lecteur"
                            session.isActiveSession -> "Reprendre"
                            else -> "Rejouer"
                        },
                        accentColor = accentColor,
                        filled = true,
                        onClick = {
                            if (session.isActiveSession && session.isPlaying) onOpenPlayer()
                            else onResumeListening()
                        }
                    )
                    if (session.isActiveSession) {
                        ResumeButton(
                            label = "Lecteur",
                            accentColor = accentColor,
                            filled = false,
                            onClick = onOpenPlayer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeButton(
    label: String,
    accentColor: Color,
    filled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (filled) accentColor.copy(alpha = 0.28f) else Color.Transparent
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(SgRadius.pill))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = SgSpacing.md + 2.dp, vertical = SgSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SgSpacing.xs)
    ) {
        if (filled) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(SgRadius.pill))
        )
    }
}

@Composable
private fun QuickAccessSection(
    accentColor: Color,
    favoritesCount: Int,
    playlistsCount: Int,
    foldersCount: Int,
    onNavigateToLibrarySection: (Int) -> Unit
) {
    Column {
        SectionHeader(title = "ACCÈS RAPIDE")
        Spacer(modifier = Modifier.height(SgSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm)
        ) {
            QuickAccessChip(
                label = "Favoris",
                subtitle = if (favoritesCount > 0) "$favoritesCount" else null,
                iconRes = R.drawable.ic_favorite_outline,
                accentColor = accentColor,
                onClick = { onNavigateToLibrarySection(LibrarySection.FAVORITES) },
                modifier = Modifier.weight(1f)
            )
            QuickAccessChip(
                label = "Playlists",
                subtitle = if (playlistsCount > 0) "$playlistsCount" else null,
                iconRes = R.drawable.ic_playlists,
                accentColor = accentColor,
                onClick = { onNavigateToLibrarySection(LibrarySection.PLAYLISTS) },
                modifier = Modifier.weight(1f)
            )
            QuickAccessChip(
                label = "Dossiers",
                subtitle = if (foldersCount > 0) "$foldersCount" else null,
                iconRes = R.drawable.ic_folder,
                accentColor = accentColor,
                onClick = { onNavigateToLibrarySection(LibrarySection.FOLDERS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickAccessChip(
    label: String,
    subtitle: String?,
    iconRes: Int,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.clickable(onClick = onClick),
        cornerRadius = SgRadius.md,
        accentColor = accentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SgSpacing.md, horizontal = SgSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(SgSpacing.xs))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun DiscoverySectionHeader(
    title: String,
    subtitle: String,
    actionLabel: String,
    accentColor: Color,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionHeader(title = title)
            Text(
                text = subtitle,
                color = TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = accentColor,
            modifier = Modifier.clickable(onClick = onAction)
        )
    }
}

@Composable
private fun DailyMixHero(
    songs: List<Song>,
    accentColor: Color,
    secondaryAccent: Color,
    onPlayAll: () -> Unit
) {
    val featured = songs.first()
    val preview = songs.take(3)
    Column {
        SectionHeader(title = "MIX DU JOUR")
        Spacer(modifier = Modifier.height(SgSpacing.sm))
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlayAll),
            cornerRadius = SgRadius.lg,
            accentColor = accentColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accentColor.copy(alpha = 0.34f),
                                secondaryAccent.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(SgSpacing.md + 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-18).dp),
                        modifier = Modifier.padding(end = SgSpacing.md)
                    ) {
                        preview.forEachIndexed { index, song ->
                            AlbumArtThumb(
                                song = song,
                                size = if (index == 0) 72.dp else 56.dp,
                                cornerRadius = SgRadius.sm,
                                modifier = Modifier
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = featured.title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(SgSpacing.xs))
                        Text(
                            text = featured.artist,
                            color = accentColor,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(SgSpacing.sm))
                        Text(
                            text = "${songs.size} titres · votre sélection du jour",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(SgSpacing.md))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(SgRadius.pill))
                        .background(accentColor.copy(alpha = 0.24f))
                        .clickable(onClick = onPlayAll)
                        .padding(horizontal = SgSpacing.md + 4.dp, vertical = SgSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SgSpacing.xs)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Lancer le mix",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalRadioRow(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    accentColor: Color,
    onPlaySong: (Song, List<Song>) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm + 2.dp),
        contentPadding = PaddingValues(end = SgSpacing.xs)
    ) {
        items(songs, key = { "radio-${it.id}" }) { song ->
            val isCurrent = currentSong?.id == song.id && isPlaying
            GlassCard(
                modifier = Modifier
                    .width(120.dp)
                    .clickable { onPlaySong(song, songs) },
                cornerRadius = SgRadius.md,
                accentColor = accentColor
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        AlbumArtThumb(
                            song = song,
                            size = null,
                            cornerRadius = 0.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(SgSpacing.xs)
                                .clip(RoundedCornerShape(SgRadius.pill))
                                .background(accentColor.copy(alpha = 0.85f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "RADIO",
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(accentColor.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_play),
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.padding(SgSpacing.sm)) {
                        Text(
                            text = song.title,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MixSuggestionsRow(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    accentColor: Color,
    onPlaySong: (Song, List<Song>) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm + 2.dp),
        contentPadding = PaddingValues(end = SgSpacing.xs)
    ) {
        items(songs, key = { "mix-${it.id}" }) { song ->
            val isCurrent = currentSong?.id == song.id && isPlaying
            GlassCard(
                modifier = Modifier
                    .width(132.dp)
                    .clickable { onPlaySong(song, songs) },
                cornerRadius = SgRadius.md,
                accentColor = accentColor
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        AlbumArtThumb(
                            song = song,
                            size = null,
                            cornerRadius = 0.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(accentColor.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_play),
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.padding(SgSpacing.sm)) {
                        Text(
                            text = song.title,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSongTile(
    song: Song,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        cornerRadius = SgRadius.md,
        accentColor = accentColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomStart
        ) {
            AlbumArtThumb(
                song = song,
                size = null,
                cornerRadius = 0.dp,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                        )
                    )
            )
            Text(
                text = song.title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(SgSpacing.sm)
            )
        }
    }
}

@Composable
private fun AlbumArtThumb(
    song: Song,
    size: androidx.compose.ui.unit.Dp?,
    cornerRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val boxModifier = if (size != null) {
        modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(CardSurface)
    } else {
        modifier.background(CardSurface)
    }
    Box(
        modifier = boxModifier,
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
            Icon(
                painter = painterResource(R.drawable.ic_songs),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(if (size != null) 32.dp else 40.dp)
            )
        }
    }
}

@Composable
fun PlaceholderTab(icon: String, title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, fontSize = 48.sp)
            Spacer(modifier = Modifier.height(SgSpacing.md))
            Text(text = title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(SgSpacing.xs))
            Text(text = subtitle, color = TextSecondary, fontSize = 14.sp)
        }
    }
}
