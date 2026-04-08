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

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val albumArtUri: Uri?
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

    LaunchedEffect(hasPermission) {
        if (hasPermission) songs = loadSongs(context)
        else permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
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
                        onSongClick = { song ->
                            currentSong = song
                            val mediaItem = MediaItem.fromUri(song.uri)
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.play()
                            isPlaying = true
                            showPlayer = true
                        }
                    )
                    1 -> PlaceholderTab("🔍", "Recherche", "Bientôt disponible")
                    2 -> PlaceholderTab("👤", "Profil", "Bientôt disponible")
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
                    onOpen = { showPlayer = true }  // 👈 ajoute ça
                )
            }

            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }

        // Écran Player par-dessus tout
        if (showPlayer && currentSong != null) {
            PlayerScreen(
                song = currentSong!!,
                isPlaying = isPlaying,
                onPlayPause = {
                    if (isPlaying) player.pause() else player.play()
                    isPlaying = !isPlaying
                },
                onClose = { showPlayer = false }
            )
        }
    }
}

@Composable
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        Pair("🏠", "Accueil"),
        Pair("🔍", "Recherche"),
        Pair("👤", "Profil")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A0A2E))
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, (icon, label) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTabSelected(index) }
                    .padding(8.dp)
            ) {
                Text(text = icon, fontSize = 22.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
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

@Composable
fun HomeTab(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(52.dp))

        Text(
            text = "Bonsoir, Gérald 👋",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ready to groove ?",
            color = TextSecondary,
            fontSize = 14.sp
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

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (searchQuery.isEmpty())
                "Toutes les chansons  •  ${songs.size}"
            else
                "${filteredSongs.size} résultat(s) pour \"$searchQuery\"",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filteredSongs) { song ->
                SongItem(
                    song = song,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    onClick = { onSongClick(song) }
                )
            }
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
    onOpen: () -> Unit  // 👈 ajoute ça
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(
                Brush.horizontalGradient(listOf(MediumPurple, DarkPurple)),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onOpen() }  // 👈 ajoute ça
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

        Box(
            modifier = Modifier
                .size(40.dp)
                .background(LightPurple, CircleShape)
                .clickable { onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (isPlaying) "⏸" else "▶", fontSize = 16.sp)
        }
    }
}

@Composable
fun SongItem(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isPlaying) CardSurface.copy(alpha = 0.9f)
                else CardSurface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
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
                Text(
                    text = if (isPlaying) "▶" else "🎵",
                    fontSize = 18.sp
                )
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
            Text(text = "♫", color = CyanAccent, fontSize = 18.sp)
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
fun PlayerScreen(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Bouton retour
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "↓",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    modifier = Modifier.clickable { onClose() }
                )
                Text(
                    text = "En lecture",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(text = "⋮", color = TextPrimary, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Pochette grande
            Box(
                modifier = Modifier
                    .size(280.dp)
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

            Spacer(modifier = Modifier.height(40.dp))

            // Titre et artiste
            Text(
                text = song.title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = song.artist,
                color = LightPurple,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Contrôles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Précédent
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(CardSurface, CircleShape)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⏮", fontSize = 22.sp)
                }

                // Play/Pause
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(LightPurple, MediumPurple)
                            ),
                            CircleShape
                        )
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        fontSize = 28.sp
                    )
                }

                // Suivant
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(CardSurface, CircleShape)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⏭", fontSize = 22.sp)
                }
            }
        }
    }
}