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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.credo.soundgroove.ui.theme.SoundGrooveTheme

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri
)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        enableEdgeToEdge()
        setContent {
            SoundGrooveTheme {
                HomeScreen(player)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

@Composable
fun HomeScreen(player: ExoPlayer) {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Bonsoir, Gérald 👋",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${songs.size} chansons trouvées",
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🔍", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Rechercher une chanson...",
                color = Color(0xFF555555),
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mini lecteur
        currentSong?.let { song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1DB954), shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .clickable {
                        if (isPlaying) player.pause()
                        else player.play()
                        isPlaying = !isPlaying
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (isPlaying) "⏸" else "▶", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(songs) { song ->
                SongItem(
                    song = song,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    onClick = {
                        currentSong = song
                        val mediaItem = MediaItem.fromUri(song.uri)
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                        isPlaying = true
                    }
                )
            }
        }
    }
}

@Composable
fun SongItem(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isPlaying) Color(0xFF1A2A1A) else Color(0xFF1A1A1A),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (isPlaying) Color(0xFF1DB954) else Color(0xFF2A2A2A),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (isPlaying) "▶" else "🎵", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = song.title,
                color = if (isPlaying) Color(0xFF1DB954) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun loadSongs(context: android.content.Context): List<Song> {
    val songs = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
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

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol)
            val artist = cursor.getString(artistCol)
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
            )
            songs.add(Song(id, title, artist, uri))
        }
    }
    return songs
}