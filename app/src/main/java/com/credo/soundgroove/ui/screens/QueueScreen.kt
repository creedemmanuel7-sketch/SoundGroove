package com.credo.soundgroove.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.SgEmptyState
import com.credo.soundgroove.ui.motion.SgCoverImage
import com.credo.soundgroove.ui.theme.CardSurface
import com.credo.soundgroove.ui.theme.ErrorRed
import com.credo.soundgroove.ui.theme.GlassBorder
import com.credo.soundgroove.ui.theme.GraphiteCard
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.SilverAccent
import com.credo.soundgroove.ui.theme.SurfaceElevated
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
import com.credo.soundgroove.ui.theme.sgNavigationBarsBottom
import com.credo.soundgroove.ui.theme.sgPressScale
import com.credo.soundgroove.ui.theme.sgSheetGradientBrush
import com.credo.soundgroove.util.displayArtist
import com.credo.soundgroove.util.displayTitle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Panneau file d'attente — architecture unifiée (inspirée Rivage QueuePanel) :
 * une seule liste avec le titre en cours surligné inline, long-press pour réordonner,
 * swipe gauche pour retirer. Le morph [morphProgress] pilote l'apparition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    playlist: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean = true,
    accentColor: Color = SilverAccent,
    morphProgress: Float = 1f,
    onClose: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberSgReducedMotion()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 64.dp.toPx() }
    val collapseThreshold = with(density) { 56.dp.toPx() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var collapseDrag by remember { mutableFloatStateOf(0f) }
    val safeCurrentIndex = currentIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))

    val showReturnChip by remember {
        derivedStateOf {
            if (safeCurrentIndex !in playlist.indices) return@derivedStateOf false
            val first = listState.firstVisibleItemIndex
            val last = first + listState.layoutInfo.visibleItemsInfo.size
            safeCurrentIndex < first - 1 || safeCurrentIndex > last
        }
    }

    LaunchedEffect(playlist.size, safeCurrentIndex, reducedMotion) {
        if (playlist.isNotEmpty() && safeCurrentIndex in playlist.indices) {
            if (reducedMotion) listState.scrollToItem(safeCurrentIndex)
            else listState.animateScrollToItem(safeCurrentIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = SgRadius.xl, topEnd = SgRadius.xl))
            .background(sgSheetGradientBrush())
            .graphicsLayer { alpha = morphProgress.coerceIn(0f, 1f) }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(SgSpacing.sm))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(GlassBorder, RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(SgSpacing.md))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (collapseDrag > collapseThreshold) onClose()
                                collapseDrag = 0f
                            },
                            onDragCancel = { collapseDrag = 0f },
                            onVerticalDrag = { _, amount ->
                                if (amount > 0f) collapseDrag += amount
                            },
                        )
                    }
                    .padding(horizontal = SgSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "File d'attente · ${playlist.size}",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "↓ Glisser pour refermer",
                        color = TextPrimary.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(onClick = onClose) {
                    Text("Fermer", color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                text = "Maintien pour réordonner · glisser à gauche pour retirer",
                color = TextPrimary.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = SgSpacing.xl, vertical = SgSpacing.xs),
            )

            if (playlist.isEmpty()) {
                SgEmptyState(
                    iconPainter = painterResource(R.drawable.ic_queue),
                    title = "File d'attente vide",
                    subtitle = "Lance une lecture depuis la bibliothèque ou une playlist.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = SgSpacing.xl),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 280.dp),
                    contentPadding = PaddingValues(
                        start = SgSpacing.lg,
                        end = SgSpacing.lg,
                        bottom = 72.dp + sgNavigationBarsBottom(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(SgSpacing.xs),
                ) {
                    itemsIndexed(
                        items = playlist,
                        key = { index, song -> "${song.id}-$index" },
                        contentType = { _, _ -> "queue_row" },
                    ) { index, song ->
                        val isCurrent = index == safeCurrentIndex
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    scope.launch { onRemoveSong(index) }
                                    true
                                } else false
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val progress = dismissState.progress.coerceIn(0f, 1f)
                                if (progress > 0.02f && !isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .alpha(progress)
                                            .clip(RoundedCornerShape(SgRadius.md))
                                            .background(ErrorRed.copy(alpha = 0.85f))
                                            .padding(horizontal = SgSpacing.xl),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Text("Retirer", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            },
                        ) {
                            QueueUnifiedRow(
                                song = song,
                                index = index,
                                isCurrent = isCurrent,
                                isCurrentPlaying = isCurrent && isPlaying,
                                isDragging = draggingIndex == index,
                                dragOffsetY = if (draggingIndex == index) dragOffsetY else 0f,
                                accentColor = accentColor,
                                rowHeightPx = rowHeightPx,
                                onPlay = { onPlaySong(index) },
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffsetY = 0f
                                },
                                onDrag = { dragOffsetY += it },
                                onDragEnd = {
                                    val delta = (dragOffsetY / rowHeightPx).roundToInt()
                                    val target = (index + delta).coerceIn(0, playlist.lastIndex)
                                    if (target != index) onMoveSong(index, target)
                                    draggingIndex = -1
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffsetY = 0f
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showReturnChip && playlist.isNotEmpty()) {
            AssistChip(
                onClick = {
                    scope.launch {
                        val target = (safeCurrentIndex - 1).coerceAtLeast(0)
                        listState.animateScrollToItem(target)
                    }
                },
                label = { Text("Revenir au titre en cours") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun QueueUnifiedRow(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    isCurrentPlaying: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    accentColor: Color,
    rowHeightPx: Float,
    onPlay: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val metaColor = TextPrimary.copy(alpha = 0.72f)
    val borderColor by animateColorAsState(
        targetValue = when {
            isDragging -> accentColor.copy(alpha = 0.55f)
            isCurrent -> accentColor.copy(alpha = 0.4f)
            else -> GlassBorder.copy(alpha = 0.35f)
        },
        animationSpec = SgMotion.tweenFastOf(),
        label = "queue_row_border",
    )
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = SgMotion.SpringSoft,
        label = "queue_drag_scale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
            }
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .clip(RoundedCornerShape(SgRadius.md))
            .background(
                if (isCurrent) {
                    Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.16f), CardSurface.copy(alpha = 0.97f)))
                } else {
                    Brush.linearGradient(listOf(CardSurface.copy(alpha = 0.97f), CardSurface.copy(alpha = 0.97f)))
                },
            )
            .border(1.dp, borderColor, RoundedCornerShape(SgRadius.md))
            .pointerInput(index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                )
            }
            .clickable(onClick = onPlay)
            .padding(horizontal = SgSpacing.md, vertical = SgSpacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            Box(modifier = Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                com.credo.soundgroove.ui.components.NowPlayingBars(
                    isPlaying = isCurrentPlaying,
                    accentColor = accentColor,
                    barHeight = 14.dp,
                )
            }
        } else {
            Text(
                text = "${index + 1}",
                color = metaColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(SgSpacing.sm))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(SgRadius.sm))
                .background(GraphiteCard),
            contentAlignment = Alignment.Center,
        ) {
            if (song.albumArtUri != null) {
                SgCoverImage(
                    albumArtUri = song.albumArtUri,
                    uriCrossfade = false,
                    decodeEdgeDp = 44.dp,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_songs),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(SgSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.displayTitle(),
                color = if (isCurrent) accentColor else TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.displayArtist(),
                color = metaColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Bandeau compact Player quand la file est ouverte (~1/4 écran). */
@Composable
fun PlayerQueueBanner(
    song: Song,
    isPlaying: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prevInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(com.credo.soundgroove.ui.theme.sgFullScreenGradientBrush())
            .clickable { onExpand() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SgSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(SgRadius.sm))
                    .background(GraphiteCard),
                contentAlignment = Alignment.Center,
            ) {
                if (song.albumArtUri != null) {
                    SgCoverImage(
                        albumArtUri = song.albumArtUri,
                        uriCrossfade = true,
                        decodeEdgeDp = 52.dp,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_songs),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(SgSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.displayTitle(),
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.displayArtist(),
                    color = TextPrimary.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .sgPressScale(prevInteraction, pressedScale = 0.9f)
                        .clickable(interactionSource = prevInteraction, indication = null, onClick = onSkipPrevious),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_previous),
                        contentDescription = "Précédent",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .sgPressScale(playInteraction, pressedScale = 0.9f)
                        .clip(CircleShape)
                        .background(accentColor)
                        .clickable(interactionSource = playInteraction, indication = null, onClick = onPlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (isPlaying) "Pause" else "Lecture",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .sgPressScale(nextInteraction, pressedScale = 0.9f)
                        .clickable(interactionSource = nextInteraction, indication = null, onClick = onSkipNext),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Suivant",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(GlassBorder.copy(alpha = 0.4f)),
        )
    }
}
