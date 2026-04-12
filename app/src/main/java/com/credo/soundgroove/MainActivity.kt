package com.credo.soundgroove

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.ui.theme.*
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val albumArtUri: Uri?
)
data class Playlist(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val songs: List<Song> = emptyList()
)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        enableEdgeToEdge()
        setContent {
            SoundGrooveTheme {
                MainScreen(player)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

@Composable
fun MainScreen(player: ExoPlayer) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var recentlyPlayed by remember { mutableStateOf<List<Song>>(emptyList()) }
    var showRecentlyPlayed by remember { mutableStateOf(false) }
    var currentPlaylist by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    var favoriteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(hasPermission) {
        if (hasPermission) songs = loadSongs(context)
        else permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
    }

    LaunchedEffect(player) {
        while (true) {
            val index = player.currentMediaItemIndex
            if (index >= 0 && index < currentPlaylist.size && currentPlaylist.isNotEmpty()) {
                currentSong = currentPlaylist[index]
                isPlaying = player.isPlaying
            }
            kotlinx.coroutines.delay(300)
        }
    }

    fun playSong(song: Song, playlist: List<Song>) {
        currentPlaylist = playlist
        val mediaItems = playlist.map { s -> MediaItem.fromUri(s.uri) }
        val index = playlist.indexOf(song)
        player.setMediaItems(mediaItems, index, 0L)
        player.prepare()
        player.play()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2D1B4E),
                        Color(0xFF1A0A2E),
                        Color(0xFF0D0D1A)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> HomeTab(
                        songs = songs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        recentlyPlayed = recentlyPlayed,
                        onSeeAllRecent = { showRecentlyPlayed = true },
                        onSongClick = { song ->
                            currentSong = song
                            playSong(song, songs)
                            isPlaying = true
                            showPlayer = true
                            recentlyPlayed = (listOf(song) + recentlyPlayed)
                                .distinctBy { it.id }
                                .take(70)
                        }
                    )
                    1 -> LibraryTab(
                        songs = songs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        favoriteSongs = favoriteSongs,
                        playlists = playlists,
                        onPlaylistCreate = { name ->
                            playlists = playlists + Playlist(name = name)
                        },
                        onPlaylistAddSong = { playlist, song ->
                            playlists = playlists.map {
                                if (it.id == playlist.id)
                                    it.copy(songs = it.songs + song)
                                else it
                            }
                        },
                        onSongClick = { song ->
                            currentSong = song
                            playSong(song, songs)
                            isPlaying = true
                            showPlayer = true
                            recentlyPlayed = (listOf(song) + recentlyPlayed)
                                .distinctBy { it.id }.take(70)
                        },
                        onPlayPlaylist = { song, playlist ->
                            currentSong = song
                            playSong(song, playlist)
                            isPlaying = true
                            showPlayer = true
                            recentlyPlayed = (listOf(song) + recentlyPlayed)
                                .distinctBy { it.id }.take(70)
                        },
                        onToggleFavorite = { song ->
                            favoriteSongs = if (favoriteSongs.any { it.id == song.id })
                                favoriteSongs.filter { it.id != song.id }
                            else favoriteSongs + song
                        }
                    )
                    2 -> SearchTab(
                        songs = songs,
                        onSongClick = { song ->
                            currentSong = song
                            playSong(song, songs)
                            isPlaying = true
                            showPlayer = true
                            recentlyPlayed = (listOf(song) + recentlyPlayed)
                                .distinctBy { it.id }
                                .take(70)
                        }
                    )
                    3 -> ProfileTab(
                        songs = songs,
                        recentlyPlayed = recentlyPlayed,
                        favoriteSongs = favoriteSongs
                    )
                }
            }

            currentSong?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = isPlaying,
                    onPlayPause = {
                        if (isPlaying) player.pause() else player.play()
                        isPlaying = !isPlaying
                    },
                    onOpen = { showPlayer = true }
                )
            }

            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }

        if (showRecentlyPlayed) {
            RecentlyPlayedScreen(
                songs = recentlyPlayed,
                onClose = { showRecentlyPlayed = false },
                onSongClick = { song ->
                    currentSong = song
                    playSong(song, recentlyPlayed)
                    isPlaying = true
                    showPlayer = true
                }
            )
        }

        if (showPlayer && currentSong != null) {
            PlayerScreen(
                song = currentSong!!,
                isPlaying = isPlaying,
                isFavorite = favoriteSongs.any { it.id == currentSong!!.id },
                onPlayPause = {
                    if (isPlaying) player.pause() else player.play()
                    isPlaying = !isPlaying
                },
                onClose = { showPlayer = false },
                onToggleFavorite = {
                    val song = currentSong!!
                    favoriteSongs = if (favoriteSongs.any { it.id == song.id })
                        favoriteSongs.filter { it.id != song.id }
                    else favoriteSongs + song
                },
                player = player
            )
        }
    }
}

@Composable
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    data class NavItem(val label: String, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector, val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector)

    val tabs = listOf(
        NavItem("Accueil", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("Bibliothèque", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
        NavItem("Recherche", Icons.Filled.Search, Icons.Outlined.Search),
        NavItem("Profil", Icons.Filled.Person, Icons.Outlined.Person)
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        cornerRadius = 24.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0x33000000), Color(0x1A000000))))
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onTabSelected(index) }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (selectedTab == index) LightPurple else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        color = if (selectedTab == index) LightPurple else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                    )
                    if (selectedTab == index) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(LightPurple, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun HomeTab(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    recentlyPlayed: List<Song>,
    onSeeAllRecent: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSongs = remember(searchQuery, songs) {
        if (searchQuery.isEmpty()) songs
        else songs.filter { song ->
            song.title.contains(searchQuery, ignoreCase = true) ||
                    song.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Bonjour"
        hour < 18 -> "Bon après-midi"
        else -> "Bonsoir"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(52.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ready to groove ?",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Paramètres",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔍", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    androidx.compose.material3.TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Rechercher une chanson...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = LightPurple
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        if (currentSong != null && searchQuery.isEmpty()) {
            item {
                Text(
                    text = "EN COURS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.horizontalGradient(listOf(DarkPurple, MediumPurple)),
                            RoundedCornerShape(20.dp)
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clickable { },
                        cornerRadius = 20.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(MediumPurple.copy(0.4f), DarkPurple.copy(0.2f))
                                    )
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentSong.artist,
                                    color = PurpleAccent,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentSong.title,
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(LightPurple.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                        .border(1.dp, LightPurple.copy(0.4f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isPlaying) "▶ En lecture" else "⏸ En pause",
                                        color = LightPurple,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                    .background(CardSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentSong.albumArtUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(currentSong.albumArtUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(text = "🎵", fontSize = 40.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (recentlyPlayed.isNotEmpty() && searchQuery.isEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RÉCEMMENT ÉCOUTÉS",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Voir tout",
                        color = LightPurple,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onSeeAllRecent() }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Grille 2x2 — seulement 4 chansons
                val rows = recentlyPlayed.take(4).chunked(2)
                rows.forEach { rowSongs ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowSongs.forEach { song ->
                            GlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clickable { onSongClick(song) },
                                cornerRadius = 16.dp
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.BottomStart
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
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(0.7f))
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
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                        if (rowSongs.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        item {
            Text(
                text = if (searchQuery.isEmpty())
                    "TOUTES LES CHANSONS  •  ${songs.size}"
                else
                    "${filteredSongs.size} RÉSULTAT(S) POUR \"${searchQuery.uppercase()}\"",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        items(filteredSongs) { song ->
            SongItem(
                song = song,
                isPlaying = currentSong?.id == song.id && isPlaying,
                onClick = { onSongClick(song) }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
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
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable { onOpen() },
        cornerRadius = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(MediumPurple.copy(0.4f), DarkPurple.copy(0.6f))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardSurface),
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
                    Text(text = "🎵", fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = PurpleAccent,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
@Composable
fun SongItem(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isPlaying)
                        Brush.linearGradient(listOf(LightPurple.copy(0.15f), MediumPurple.copy(0.1f)))
                    else
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkPurple),
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
                    Text(text = if (isPlaying) "▶" else "🎵", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isPlaying) LightPurple else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isPlaying) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun RecentlyPlayedScreen(
    songs: List<Song>,
    onClose: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2D1B4E),
                        Color(0xFF1A0A2E),
                        Color(0xFF0D0D1A)
                    )
                )
            )
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
                        .background(CardSurface, CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "←", color = TextPrimary, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Récemment écoutés",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
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
                    .background(LightPurple, RoundedCornerShape(12.dp))
                    .clickable {
                        if (songs.isNotEmpty()) onSongClick(songs.first())
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "▶", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tout jouer",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(songs) { song ->
                    SongItem(
                        song = song,
                        isPlaying = false,
                        onClick = { onSongClick(song) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun SearchTab(
    songs: List<Song>,
    onSongClick: (Song) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) }
    val filters = listOf("Chansons", "Artistes", "Albums")

    val filteredSongs = remember(searchQuery, selectedFilter, songs) {
        if (searchQuery.isEmpty()) emptyList()
        else when (selectedFilter) {
            0 -> songs.filter { it.title.contains(searchQuery, ignoreCase = true) }
            1 -> songs.filter { it.artist.contains(searchQuery, ignoreCase = true) }
            2 -> songs.filter { it.artist.contains(searchQuery, ignoreCase = true) }.distinctBy { it.artist }
            else -> emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(52.dp))

        Text(
            text = "Recherche",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🔍", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(10.dp))
            androidx.compose.material3.TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(text = "Artiste, chanson...", color = TextSecondary, fontSize = 14.sp)
                },
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = LightPurple
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEachIndexed { index, filter ->
                Box(
                    modifier = Modifier
                        .background(
                            if (selectedFilter == index) LightPurple else CardSurface,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedFilter = index }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = filter,
                        color = if (selectedFilter == index) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selectedFilter == index) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔍", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Tape pour rechercher", color = TextSecondary, fontSize = 16.sp)
                }
            }
        } else if (filteredSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "😕", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Aucun résultat pour \"$searchQuery\"",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            Text(text = "${filteredSongs.size} résultat(s)", color = TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filteredSongs) { song ->
                    SongItem(song = song, isPlaying = false, onClick = { onSongClick(song) })
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    player: ExoPlayer
) {
    var progress by remember { mutableStateOf(0f) }
    var isShuffled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0L) }
    var currentPosition by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(1L)
            progress = currentPosition.toFloat() / duration.toFloat()
            kotlinx.coroutines.delay(500)
        }
    }

    fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return "%d:%02d".format(minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3D2060),
                        Color(0xFF1A0A2E),
                        Color(0xFF0D0D1A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            // Header — retour + titre + menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Fermer",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "EN LECTURE",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Pochette
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkPurple),
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
                    Text(text = "🎵", fontSize = 80.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Titre + Favori
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.artist,
                            color = LightPurple,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) Color(0xFFFF6B9D) else TextSecondary,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onToggleFavorite() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Slider
            var isSeeking by remember { mutableStateOf(false) }
            var seekPosition by remember { mutableStateOf(0f) }

            androidx.compose.material3.Slider(
                value = if (isSeeking) seekPosition else progress.coerceIn(0f, 1f),
                onValueChange = { value ->
                    isSeeking = true
                    seekPosition = value
                },
                onValueChangeFinished = {
                    player.seekTo((seekPosition * duration).toLong())
                    isSeeking = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = LightPurple,
                    activeTrackColor = LightPurple,
                    inactiveTrackColor = CardSurface
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (isSeeking) (seekPosition * duration).toLong() else currentPosition),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(text = formatTime(duration), color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Contrôles
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                cornerRadius = 24.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (isShuffled) LightPurple else GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable {
                                isShuffled = !isShuffled
                                player.shuffleModeEnabled = isShuffled
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffled) Color.White else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { player.seekToPreviousMediaItem() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Précédent",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                Brush.radialGradient(listOf(LightPurple, MediumPurple)),
                                CircleShape
                            )
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { player.seekToNextMediaItem() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Suivant",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (repeatMode > 0) LightPurple else GlassSurface, CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable {
                                repeatMode = (repeatMode + 1) % 3
                                player.repeatMode = when (repeatMode) {
                                    1 -> ExoPlayer.REPEAT_MODE_ALL
                                    2 -> ExoPlayer.REPEAT_MODE_ONE
                                    else -> ExoPlayer.REPEAT_MODE_OFF
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Répéter",
                            tint = if (repeatMode > 0) Color.White else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

fun loadSongs(context: android.content.Context): List<Song> {
    val songs = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM_ID
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, selection, null, sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol)
            val artist = cursor.getString(artistCol)
            val albumId = cursor.getLong(albumIdCol)
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
            )
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), albumId
            )
            songs.add(Song(id, title, artist, uri, albumArtUri))
        }
    }
    return songs
}
@Composable
fun ProfileTab(
    songs: List<Song>,
    recentlyPlayed: List<Song>,
    favoriteSongs: List<Song>
) {
    var userName by remember { mutableStateOf("Credson") }
    var showEditDialog by remember { mutableStateOf(false) }

    val topArtists = remember(recentlyPlayed) {
        recentlyPlayed
            .groupBy { it.artist }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { it.key }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(52.dp))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEditDialog = true },
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(MediumPurple.copy(0.3f), Color.Transparent))
                        )
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(
                                Brush.radialGradient(listOf(LightPurple, MediumPurple)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.first().uppercase(),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = userName, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Appuie pour modifier", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.weight(1f), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "🎵", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "${songs.size}", color = LightPurple, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Titres", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                GlassCard(modifier = Modifier.weight(1f), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "♡", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "${favoriteSongs.size}", color = Color(0xFFFF6B9D), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Favoris", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (topArtists.isNotEmpty()) {
            item {
                Text(
                    text = "TOP ARTISTES",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                topArtists.forEachIndexed { index, artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${index + 1}",
                            color = LightPurple,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    Brush.radialGradient(listOf(MediumPurple, DarkPurple)),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = artist.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = artist,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "RÉCEMMENT ÉCOUTÉS",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(recentlyPlayed.take(5)) { song ->
            SongItem(song = song, isPlaying = false, onClick = {})
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showEditDialog) {
        var tempName by remember { mutableStateOf(userName) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
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
                    text = "Modifier le profil",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.TextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Nom", color = TextSecondary) },
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = DarkPurple,
                        unfocusedContainerColor = DarkPurple,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = LightPurple,
                        focusedIndicatorColor = LightPurple,
                        unfocusedIndicatorColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                            .clickable { showEditDialog = false }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(LightPurple, RoundedCornerShape(12.dp))
                            .clickable {
                                userName = tempName
                                showEditDialog = false
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

@Composable
fun LibraryTab(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongs: List<Song>,
    playlists: List<Playlist>,
    onPlaylistCreate: (String) -> Unit,
    onPlaylistAddSong: (Playlist, Song) -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayPlaylist: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit
){
    var selectedTab by remember { mutableStateOf(0) }
    var selectedAlbum by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var selectedArtist by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    val tabs = listOf("Chansons", "Albums", "Artistes", "Playlists", "Favoris")
    val albums = remember(songs) {
        songs.groupBy { it.artist }
            .entries
            .sortedBy { it.key }
            .map { Pair(it.key, it.value) }
    }

    val artists = remember(songs) {
        songs.map { it.artist }.distinct().sorted()
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Contenu principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            Text(
                text = "Ma Musique",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Onglets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { selectedTab = index }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tab,
                            color = if (selectedTab == index) LightPurple else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (selectedTab == index) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(2.dp)
                                    .background(LightPurple, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(songs) { song ->
                        val isFav = favoriteSongs.any { it.id == song.id }
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayPlaylist(song, songs) },
                            cornerRadius = 14.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isFav) Brush.linearGradient(listOf(Color(0xFFFF6B9D).copy(0.1f), Color.Transparent))
                                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkPurple),
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
                                    Text(text = "🎵", fontSize = 18.sp)
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
                                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favori",
                                    tint = if (isFav) Color(0xFFFF6B9D) else TextSecondary,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable { onToggleFavorite(song) }
                                )
                        }
                    }

                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val rows = albums.chunked(2)
                    items(rows) { rowAlbums ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowAlbums.forEach { (artist, albumSongs) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(CardSurface)
                                        .clickable { selectedAlbum = Pair(artist, albumSongs) },
                                    contentAlignment = Alignment.BottomStart
                                ) {
                                    val coverSong = albumSongs.firstOrNull { it.albumArtUri != null }
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
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(0.8f))
                                                )
                                            )
                                    )
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = artist,
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
                            if (rowAlbums.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                2 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(artists) { artist ->
                        val artistSongs = songs.filter { it.artist == artist }
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedArtist = Pair(artist, artistSongs) },
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
                                        .clip(CircleShape)
                                        .background(DarkPurple),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val coverSong =
                                        artistSongs.firstOrNull { it.albumArtUri != null }
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
                                        Text(
                                            text = artist.firstOrNull()?.uppercase() ?: "?",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
                    // Playlists
                    var showCreateDialog by remember { mutableStateOf(false) }
                    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Bouton créer
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val playlist = null
                                        selectedPlaylist = playlist
                                    },
                                cornerRadius = 14.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${playlists.size} playlist(s)",
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(LightPurple, RoundedCornerShape(20.dp))
                                            .clickable { showCreateDialog = true }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "+",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Nouvelle playlist",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            if (playlists.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "🎶", fontSize = 48.sp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(text = "Aucune playlist", color = TextSecondary, fontSize = 16.sp)
                                        Text(text = "Appuie sur + pour créer", color = TextSecondary, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(playlists) { playlist ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CardSurface.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                                .clickable { selectedPlaylist = playlist }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Cover playlist
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        Brush.radialGradient(listOf(LightPurple, MediumPurple))
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
                                                    Text(text = "🎵", fontSize = 22.sp)
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playlist.name,
                                                    color = TextPrimary,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${playlist.songs.size} chanson(s)",
                                                    color = TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            Text(text = "▶", color = LightPurple, fontSize = 20.sp)
                                        }
                                    }
                                    item { Spacer(modifier = Modifier.height(16.dp)) }
                                }
                            }
                        }

                        // Dialog créer playlist
                        if (showCreateDialog) {
                            var playlistName by remember { mutableStateOf("") }
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
                                        text = "Nouvelle playlist",
                                        color = TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    androidx.compose.material3.TextField(
                                        value = playlistName,
                                        onValueChange = { playlistName = it },
                                        label = { Text("Nom de la playlist", color = TextSecondary) },
                                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                                            focusedContainerColor = DarkPurple,
                                            unfocusedContainerColor = DarkPurple,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            cursorColor = LightPurple,
                                            focusedIndicatorColor = LightPurple,
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
                                                .clickable { showCreateDialog = false }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (playlistName.isNotBlank()) LightPurple else TextSecondary,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    if (playlistName.isNotBlank()) {
                                                        onPlaylistCreate(playlistName.trim())
                                                        showCreateDialog = false
                                                        playlistName = ""
                                                    }
                                                }
                                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Créer",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Écran playlist sélectionnée
                        selectedPlaylist?.let { playlist ->
                            PlaylistDetailScreen(
                                playlist = playlist,
                                allSongs = songs,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                onClose = { selectedPlaylist = null },
                                onPlayAll = {
                                    if (playlist.songs.isNotEmpty())
                                        onPlayPlaylist(playlist.songs.first(), playlist.songs)
                                },
                                onSongClick = { song -> onPlayPlaylist(song, playlist.songs) },
                                onAddSong = { song -> onPlaylistAddSong(playlist, song) }
                            )
                        }
                    }
                }

                4 -> {
                    // Favoris (ancien cas 3)
                    if (favoriteSongs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "♡", fontSize = 48.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "Aucun favori encore", color = TextSecondary, fontSize = 16.sp)
                                Text(text = "Appuie sur ♡ pour ajouter", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(favoriteSongs) { song ->
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
                                                Brush.linearGradient(listOf(Color(0xFFFF6B9D).copy(0.1f), Color.Transparent))
                                            )
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DarkPurple),
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
                                            Text(text = "🎵", fontSize = 18.sp)
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
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = "Retirer des favoris",
                                            tint = Color(0xFFFF6B9D),
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

        // Écran Album par-dessus
        selectedAlbum?.let { (albumName, albumSongs) ->
            PlaylistScreen(
                title = albumName,
                songs = albumSongs,
                currentSong = currentSong,
                isPlaying = isPlaying,
                onClose = { selectedAlbum = null },
                onPlayAll = { onPlayPlaylist(albumSongs.first(), albumSongs) },
                onSongClick = { song -> onPlayPlaylist(song, albumSongs) }
            )
        }

        // Écran Artiste par-dessus
        selectedArtist?.let { (artistName, artistSongs) ->
            PlaylistScreen(
                title = artistName,
                songs = artistSongs,
                currentSong = currentSong,
                isPlaying = isPlaying,
                onClose = { selectedArtist = null },
                onPlayAll = { onPlayPlaylist(artistSongs.first(), artistSongs) },
                onSongClick = { song -> onPlayPlaylist(song, artistSongs) }
            )
        }
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
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2D1B4E), Color(0xFF1A0A2E), Color(0xFF0D0D1A))
                )
            )
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
                        .background(CardSurface, CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "←", color = TextPrimary, fontSize = 20.sp)
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
                    .background(LightPurple, RoundedCornerShape(12.dp))
                    .clickable { onPlayAll() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "▶", color = Color.White, fontSize = 16.sp)
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
}@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    allSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAddSong: (Song) -> Unit
) {
    var showAddSongs by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2D1B4E), Color(0xFF1A0A2E), Color(0xFF0D0D1A))
                )
            )
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
                        .background(CardSurface, CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "←", color = TextPrimary, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.songs.size} chanson(s)",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .background(LightPurple.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .clickable { showAddSongs = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "+ Ajouter", color = LightPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (playlist.songs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightPurple, RoundedCornerShape(12.dp))
                        .clickable { onPlayAll() }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "▶", color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Tout jouer", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (playlist.songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🎵", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Playlist vide", color = TextSecondary, fontSize = 16.sp)
                        Text(text = "Appuie sur + Ajouter", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(playlist.songs) { song ->
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

        // Écran ajouter chansons
        if (showAddSongs) {
            val filteredSongs = remember(searchQuery, allSongs) {
                if (searchQuery.isEmpty()) allSongs
                else allSongs.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                            it.artist.contains(searchQuery, ignoreCase = true)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2D1B4E), Color(0xFF1A0A2E), Color(0xFF0D0D1A))
                        )
                    )
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
                                .background(CardSurface, CircleShape)
                                .clickable { showAddSongs = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "←", color = TextPrimary, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Ajouter des chansons",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSurface, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔍", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Rechercher...", color = TextSecondary, fontSize = 14.sp) },
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = LightPurple
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredSongs) { song ->
                            val alreadyAdded = playlist.songs.any { it.id == song.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSurface.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (!alreadyAdded) onAddSong(song)
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkPurple),
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
                                        Text(text = "🎵", fontSize = 18.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = if (alreadyAdded) TextSecondary else TextPrimary,
                                        fontSize = 14.sp,
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
                                Text(
                                    text = if (alreadyAdded) "✓" else "+",
                                    color = if (alreadyAdded) CyanAccent else LightPurple,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}