package com.credo.soundgroove.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.lyrics.LyricsContent
import com.credo.soundgroove.lyrics.LyricsViewModel
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.TextSecondary
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion

/**
 * Aperçu paroles style Spotify sur le lecteur : 2–3 lignes sync (précédente /
 * active / suivante), texte léger sans carte. Tap → écran Paroles plein.
 * Sans LRC sync : rien (swipe horizontal ou action d'accessibilité).
 */
@Composable
fun PlayerInlineLyricsPreview(
    song: Song,
    playbackPositionMs: Long,
    accentColor: Color,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    lyricsSyncOffsetMs: Long = LyricsViewModel.DEFAULT_SYNC_OFFSET_MS,
    lyricsViewModel: LyricsViewModel = viewModel()
) {
    val reducedMotion = rememberSgReducedMotion()
    val content by lyricsViewModel.lyricsContent.collectAsState()
    val currentLineIndex by lyricsViewModel.currentLineIndex.collectAsState()

    LaunchedEffect(song.id) {
        lyricsViewModel.loadLyricsForSong(song)
    }
    LaunchedEffect(lyricsSyncOffsetMs) {
        lyricsViewModel.setSyncOffsetMs(lyricsSyncOffsetMs)
    }
    LaunchedEffect(playbackPositionMs, content) {
        lyricsViewModel.updatePlaybackPosition(playbackPositionMs)
    }

    when (val state = content) {
        is LyricsContent.Synced -> {
            if (state.lines.isEmpty()) return
            val index = currentLineIndex
            val prev = state.lines.getOrNull(index - 1)?.text?.takeIf { index > 0 }
            val active = when {
                index >= 0 -> state.lines.getOrNull(index)?.text
                else -> state.lines.firstOrNull()?.text
            }.orEmpty()
            val next = when {
                index < 0 -> state.lines.getOrNull(1)?.text
                else -> state.lines.getOrNull(index + 1)?.text
            }

            Box(
                modifier = modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 76.dp)
                    .semantics {
                        contentDescription =
                            "Aperçu des paroles. Appuyer pour ouvrir l'écran Paroles."
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenLyrics
                    )
                    .padding(horizontal = SgSpacing.sm, vertical = SgSpacing.xs)
            ) {
                AnimatedContent(
                    targetState = Triple(prev, active, next),
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        } else {
                            fadeIn(SgMotion.tweenFast()) togetherWith fadeOut(SgMotion.tweenFast())
                        }
                    },
                    label = "inlineLyricsLines",
                    modifier = Modifier.fillMaxWidth()
                ) { (p, a, n) ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        InlineLyricLine(
                            text = p.orEmpty(),
                            accentColor = accentColor,
                            active = false,
                            placeholder = p == null
                        )
                        InlineLyricLine(
                            text = a,
                            accentColor = accentColor,
                            active = true,
                            placeholder = a.isBlank()
                        )
                        InlineLyricLine(
                            text = n.orEmpty(),
                            accentColor = accentColor,
                            active = false,
                            placeholder = n == null
                        )
                    }
                }
            }
        }

        is LyricsContent.PlainText,
        LyricsContent.NotFound,
        LyricsContent.Loading,
        LyricsContent.SearchingOnline -> Unit
    }
}

@Composable
private fun InlineLyricLine(
    text: String,
    accentColor: Color,
    active: Boolean,
    placeholder: Boolean
) {
    val reducedMotion = rememberSgReducedMotion()
    val color by animateColorAsState(
        targetValue = when {
            placeholder -> Color.Transparent
            active -> accentColor
            else -> TextSecondary.copy(alpha = 0.5f)
        },
        animationSpec = if (reducedMotion) snap() else SgMotion.tweenMediumOf(),
        label = "inlineLyricColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (active && !reducedMotion && !placeholder) 1.03f else 1f,
        animationSpec = if (reducedMotion) snap() else SgMotion.SpringSoft,
        label = "inlineLyricScale"
    )
    Text(
        text = if (placeholder) " " else text,
        color = color,
        fontSize = if (active) 15.sp else 12.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    )
}
