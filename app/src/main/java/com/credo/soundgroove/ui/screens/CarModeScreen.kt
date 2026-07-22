package com.credo.soundgroove.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.credo.soundgroove.data.model.Song

private val CarBg = Color(0xFF050506)
private val CarSurface = Color(0xFF141518)
private val CarText = Color(0xFFF2F2F4)
private val CarMuted = Color(0xFF9A9CA3)
private val CarAccent = Color(0xFFE8E8EA)

/**
 * Mode voiture : plein écran, thème sombre forcé, gros contrôles, listes courtes.
 * Écran dédié (hors PlayerScreen) pour éviter les conflits de sprint.
 */
@Composable
fun CarModeScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongs: List<Song>,
    recentlyPlayed: List<Song>,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousKeepScreenOn = window?.attributes?.flags
            ?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            if (!previousKeepScreenOn) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val shortFavorites = remember(favoriteSongs) { favoriteSongs.take(6) }
    val shortRecent = remember(recentlyPlayed) { recentlyPlayed.take(6) }
    val queueForSong = remember(shortFavorites, shortRecent) {
        (shortFavorites + shortRecent).distinctBy { it.id }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101114), CarBg, Color(0xFF000000)),
                ),
            )
            .testTag("car_mode_screen")
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SoundGroove",
                    color = CarText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(CarSurface)
                        .clickable(onClick = onExit)
                        .semantics { contentDescription = "Quitter le mode voiture" }
                        .testTag("car_mode_exit"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = CarText,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Mode voiture",
                color = CarMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentSong?.title?.ifBlank { "Aucun titre" } ?: "Aucun titre",
                color = CarText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = currentSong?.artist?.ifBlank { "—" } ?: "Choisissez un morceau ci-dessous",
                color = CarMuted,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CarControlButton(
                    icon = Icons.Filled.SkipPrevious,
                    label = "Précédent",
                    testTag = "car_skip_previous",
                    onClick = onSkipPrevious,
                )
                CarControlButton(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = if (isPlaying) "Pause" else "Lecture",
                    testTag = "car_play_pause",
                    large = true,
                    onClick = onPlayPause,
                )
                CarControlButton(
                    icon = Icons.Filled.SkipNext,
                    label = "Suivant",
                    testTag = "car_skip_next",
                    onClick = onSkipNext,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("car_mode_list"),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shortFavorites.isNotEmpty()) {
                    item {
                        Text(
                            text = "Favoris",
                            color = CarMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(shortFavorites, key = { "fav-${it.id}" }) { song ->
                        CarSongRow(
                            song = song,
                            onClick = { onPlaySong(song, queueForSong.ifEmpty { listOf(song) }) },
                        )
                    }
                }
                if (shortRecent.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Récents",
                            color = CarMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(shortRecent, key = { "recent-${it.id}" }) { song ->
                        CarSongRow(
                            song = song,
                            onClick = { onPlaySong(song, queueForSong.ifEmpty { listOf(song) }) },
                        )
                    }
                }
                if (shortFavorites.isEmpty() && shortRecent.isEmpty()) {
                    item {
                        Text(
                            text = "Ajoutez des favoris ou écoutez quelques titres pour les retrouver ici.",
                            color = CarMuted,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    large: Boolean = false,
    onClick: () -> Unit,
) {
    val size = if (large) 88.dp else 72.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (large) CarAccent else CarSurface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (large) CarBg else CarText,
            modifier = Modifier.size(if (large) 44.dp else 34.dp),
        )
    }
}

@Composable
private fun CarSongRow(
    song: Song,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CarSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = CarText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                color = CarMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = CarAccent,
            modifier = Modifier.size(28.dp),
        )
    }
}
