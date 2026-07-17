package com.credo.soundgroove.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.lyrics.LyricLine
import com.credo.soundgroove.lyrics.LyricsContent
import com.credo.soundgroove.lyrics.LyricsSearchHelper
import com.credo.soundgroove.lyrics.LyricsViewModel
import com.credo.soundgroove.ui.theme.BorderSubtle
import com.credo.soundgroove.ui.theme.GlassBorder
import com.credo.soundgroove.ui.theme.GlassSurface
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SurfaceElevated
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.TextSecondary
import com.credo.soundgroove.ui.theme.TextTertiary
import com.credo.soundgroove.ui.theme.sgSheetGradientBrush
import kotlinx.coroutines.launch

/**
 * Écran/overlay Paroles — bottom sheet 3/4+ ouvert depuis le PlayerScreen.
 *
 * États : synchronisé (.lrc), texte simple (.txt), saisie manuelle, et vide.
 * Cache local prioritaire pour un affichage instantané au retour sur un morceau.
 */
@Composable
fun LyricsScreen(
    song: Song,
    player: Player,
    playbackPosition: Long,
    accentColor: Color,
    onClose: () -> Unit,
    viewModel: LyricsViewModel = viewModel()
) {
    val context = LocalContext.current
    var verticalDragOffset by remember { mutableStateOf(0f) }

    LaunchedEffect(song.id) {
        viewModel.loadLyricsForSong(song)
    }

    LaunchedEffect(playbackPosition) {
        viewModel.updatePlaybackPosition(playbackPosition)
    }

    val content by viewModel.lyricsContent.collectAsState()
    val currentLineIndex by viewModel.currentLineIndex.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(sgSheetGradientBrush())
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (verticalDragOffset > 150f) onClose()
                        verticalDragOffset = 0f
                    },
                    onDragCancel = { verticalDragOffset = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        verticalDragOffset += dragAmount
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(GlassBorder, RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))

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
                        painter = painterResource(R.drawable.ic_close_down),
                        contentDescription = "Fermer",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PAROLES",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = song.title,
                        color = accentColor,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isEditing -> PasteLyricsEditor(
                        song = song,
                        accentColor = accentColor,
                        isSaving = isSaving,
                        saveError = saveError,
                        onCancel = { viewModel.cancelEditing() },
                        onSave = { raw -> viewModel.saveLyrics(song, raw) }
                    )
                    content is LyricsContent.Loading -> LoadingLyricsState(accentColor)
                    content is LyricsContent.SearchingOnline -> SearchingOnlineLyricsState(accentColor)
                    content is LyricsContent.Synced -> SyncedLyricsList(
                        lines = (content as LyricsContent.Synced).lines,
                        currentLineIndex = currentLineIndex,
                        accentColor = accentColor,
                        onLineClick = { timeMs -> player.seekTo(timeMs) }
                    )
                    content is LyricsContent.PlainText -> PlainTextLyrics(
                        text = (content as LyricsContent.PlainText).text
                    )
                    else -> EmptyLyricsState(
                        accentColor = accentColor,
                        onOpenSearch = {
                            LyricsSearchHelper.openGoogleLyricsSearch(context, song)
                        },
                        onPasteLyrics = { viewModel.startEditing() }
                    )
                }
            }
        }
    }

    var wasEditing by remember { mutableStateOf(false) }
    LaunchedEffect(isEditing, content, isSaving) {
        if (wasEditing && !isEditing && !isSaving && content !is LyricsContent.NotFound && content !is LyricsContent.Loading) {
            Toast.makeText(context, "Paroles enregistrées", Toast.LENGTH_SHORT).show()
        }
        wasEditing = isEditing
    }
}

@Composable
private fun PasteLyricsEditor(
    song: Song,
    accentColor: Color,
    isSaving: Boolean,
    saveError: String?,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(song.id) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = "Coller ou saisir les paroles",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Copiez depuis Genius, AZLyrics ou un autre site, puis collez ici. Format LRC avec horodatages accepté.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            placeholder = {
                Text(
                    "Collez les paroles ici…",
                    color = TextTertiary,
                    fontSize = 14.sp
                )
            },
            enabled = !isSaving,
            shape = RoundedCornerShape(SgRadius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor.copy(alpha = 0.5f),
                unfocusedBorderColor = BorderSubtle.copy(alpha = 0.45f),
                focusedContainerColor = SurfaceElevated.copy(alpha = 0.45f),
                unfocusedContainerColor = SurfaceElevated.copy(alpha = 0.28f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = accentColor
            )
        )

        if (saveError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = saveError,
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(SgRadius.pill),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text("Annuler")
            }
            Button(
                onClick = { onSave(draft) },
                enabled = !isSaving && draft.trim().isNotBlank(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(SgRadius.pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Enregistrer", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SyncedLyricsList(
    lines: List<LyricLine>,
    currentLineIndex: Int,
    accentColor: Color,
    onLineClick: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && lines.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem((currentLineIndex - 2).coerceAtLeast(0))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            vertical = 140.dp,
            horizontal = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
            val isActive = index == currentLineIndex
            val isPast = index < currentLineIndex

            val color by animateColorAsState(
                targetValue = when {
                    isActive -> accentColor
                    isPast -> TextTertiary
                    else -> TextSecondary
                },
                animationSpec = SgMotion.tweenMediumOf(),
                label = "lyricLineColor"
            )
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.06f else 1f,
                animationSpec = SgMotion.SpringSoft,
                label = "lyricLineScale"
            )

            Text(
                text = line.text.ifBlank { "···" },
                color = color,
                fontSize = if (isActive) 19.sp else 16.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable { onLineClick(line.timeMs) }
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PlainTextLyrics(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = text.ifBlank { "Aucune parole trouvée." },
            color = TextPrimary,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun LoadingLyricsState(accentColor: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = accentColor)
    }
}

@Composable
private fun SearchingOnlineLyricsState(accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = accentColor)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Recherche en ligne…",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Interrogation de LRCLIB pour ce morceau",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyLyricsState(
    accentColor: Color,
    onOpenSearch: () -> Unit,
    onPasteLyrics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(GlassSurface, CircleShape)
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lyrics,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Aucune parole disponible",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Recherchez les paroles en ligne, copiez-les, puis enregistrez-les pour ce morceau. Les paroles trouvées automatiquement ou saisies manuellement seront mémorisées pour les prochaines écoutes.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onOpenSearch,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SgRadius.pill),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.Black
            )
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ouvrir un site de paroles", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onPasteLyrics,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SgRadius.pill),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
        ) {
            Text("Coller les paroles")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onOpenSearch) {
            Text(
                text = "Recherche Google : titre + artiste + lyrics",
                color = TextTertiary,
                fontSize = 12.sp
            )
        }
    }
}
