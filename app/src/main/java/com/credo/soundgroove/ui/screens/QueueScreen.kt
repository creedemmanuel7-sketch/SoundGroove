package com.credo.soundgroove.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.mutableStateOf
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
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.playback.QueuePresentation
import com.credo.soundgroove.ui.components.NowPlayingBars
import com.credo.soundgroove.ui.components.SgEmptyState
import com.credo.soundgroove.ui.motion.SgCoverImage
import com.credo.soundgroove.ui.theme.GlassBorder
import com.credo.soundgroove.ui.theme.GraphiteCard
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.SilverAccent
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.glassEffect
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
import com.credo.soundgroove.ui.theme.sgNavigationBarsBottom
import com.credo.soundgroove.ui.theme.sgPressScale
import com.credo.soundgroove.ui.theme.sgSheetGradientBrush
import com.credo.soundgroove.playback.QueueSheetMotion
import com.credo.soundgroove.util.displayArtist
import com.credo.soundgroove.util.displayTitle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class QueueRow(
    val index: Int,
    val song: Song,
    val key: String,
)

/**
 * File d'attente sectionnée : Historique repliable, carte En cours, À suivre
 * avec poignée de drag et swipe-retirer. Fallback Compose si Flutter indisponible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    playlist: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean = true,
    accentColor: Color = SilverAccent,
    morphProgress: Float = 1f,
    playbackPositionMs: Long = 0L,
    onClose: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onClearUpcoming: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberSgReducedMotion()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 64.dp.toPx() }
    val collapseThreshold = with(density) { QueueSheetMotion.DEFAULT_THRESHOLD_DP.dp.toPx() }
    val collapseDrag = remember { Animatable(0f) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var historyExpanded by remember { mutableStateOf(false) }
    var didInitialScroll by remember { mutableStateOf(false) }
    val safeCurrentIndex = currentIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))

    val keys = remember(playlist) {
        QueuePresentation.stableKeys(playlist.map { it.id })
    }
    val sections = remember(playlist.size, safeCurrentIndex, playbackPositionMs, playlist) {
        val durations = LongArray(playlist.size) { i -> playlist[i].duration }
        QueuePresentation.split(playlist.size, safeCurrentIndex, durations, playbackPositionMs)
    }
    val remainingLabel = remember(sections.remainingMs, sections.upcomingCount, sections.totalCount) {
        QueuePresentation.remainingLabel(sections.remainingMs, sections.upcomingCount, sections.totalCount)
    }
    val historyRows = remember(playlist, keys, sections.historyCount, historyExpanded) {
        if (!historyExpanded) emptyList()
        else (sections.historyStart until sections.historyEndExclusive).map { i ->
            QueueRow(i, playlist[i], keys[i])
        }
    }
    val upcomingRows = remember(playlist, keys, sections.upcomingStart, sections.upcomingEndExclusive) {
        (sections.upcomingStart until sections.upcomingEndExclusive).map { i ->
            QueueRow(i, playlist[i], keys[i])
        }
    }
    val nowSong = playlist.getOrNull(sections.nowPlaying)

    val nowPlayingVisible by remember {
        derivedStateOf { isNowPlayingVisible(listState) }
    }

    LaunchedEffect(didInitialScroll, playlist.isNotEmpty(), reducedMotion) {
        if (didInitialScroll || playlist.isEmpty()) return@LaunchedEffect
        val target = if (sections.hasHistory()) 1 else 0
        if (reducedMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        didInitialScroll = true
    }

    LaunchedEffect(safeCurrentIndex, nowPlayingVisible) {
        if (!didInitialScroll || playlist.isEmpty()) return@LaunchedEffect
        if (!nowPlayingVisible) {
            val target = if (sections.hasHistory()) 1 else 0
            if (reducedMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = SgRadius.xl, topEnd = SgRadius.xl))
            .background(sgSheetGradientBrush())
            .graphicsLayer {
                alpha = morphProgress.coerceIn(0f, 1f)
                translationY = QueueSheetMotion.dragTranslationY(collapseDrag.value)
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            QueueCollapseHandle(
                accentColor = accentColor,
                onClose = onClose,
                onVerticalDrag = { amount ->
                    if (amount > 0f) {
                        scope.launch {
                            collapseDrag.snapTo(collapseDrag.value + amount)
                        }
                    }
                },
                onDragEnd = {
                    val dismiss = QueueSheetMotion.shouldDismiss(
                        collapseDrag.value,
                        collapseThreshold,
                        0f,
                    )
                    if (dismiss) {
                        onClose()
                    } else {
                        scope.launch {
                            collapseDrag.animateTo(0f, SgMotion.queueSheetSpec())
                        }
                    }
                },
                onDragCancel = {
                    scope.launch { collapseDrag.animateTo(0f, SgMotion.queueSheetSpec()) }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SgSpacing.xl, vertical = SgSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "File d'attente",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = remainingLabel,
                        color = TextPrimary.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.heightIn(min = SgSpacing.hitTarget),
                ) {
                    Text("Fermer", color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            }

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
                        start = SgSpacing.xl,
                        end = SgSpacing.xl,
                        top = SgSpacing.sm,
                        bottom = 80.dp + sgNavigationBarsBottom(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(SgSpacing.md),
                ) {
                    if (sections.hasHistory()) {
                        item(key = "history_header", contentType = "section") {
                            QueueSectionHeader(
                                title = "Historique",
                                meta = "${sections.historyCount}",
                                accentColor = accentColor,
                                expanded = historyExpanded,
                                onClick = { historyExpanded = !historyExpanded },
                            )
                        }
                        items(
                            items = historyRows,
                            key = { it.key },
                            contentType = { "history_row" },
                        ) { row ->
                            QueueHistoryRow(
                                song = row.song,
                                accentColor = accentColor,
                                onPlay = { onPlaySong(row.index) },
                            )
                        }
                    }

                    item(key = "now_playing", contentType = "now") {
                        if (nowSong != null) {
                            QueueNowPlayingCard(
                                song = nowSong,
                                isPlaying = isPlaying,
                                accentColor = accentColor,
                            )
                        }
                    }

                    item(key = "upcoming_header", contentType = "section") {
                        QueueUpcomingHeader(
                            count = sections.upcomingCount,
                            accentColor = accentColor,
                            canClear = sections.hasUpcoming(),
                            onClear = onClearUpcoming,
                        )
                    }

                    items(
                        items = upcomingRows,
                        key = { it.key },
                        contentType = { "upcoming_row" },
                    ) { row ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    scope.launch { onRemoveSong(row.index) }
                                    true
                                } else false
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val progress = dismissState.progress.coerceIn(0f, 1f)
                                if (progress > 0.02f) {
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
                            QueueUpcomingRow(
                                song = row.song,
                                isDragging = draggingIndex == row.index,
                                dragOffsetY = if (draggingIndex == row.index) dragOffsetY else 0f,
                                accentColor = accentColor,
                                onPlay = { onPlaySong(row.index) },
                                onDragStart = {
                                    draggingIndex = row.index
                                    dragOffsetY = 0f
                                },
                                onDrag = { dragOffsetY += it },
                                onDragEnd = {
                                    val delta = (dragOffsetY / rowHeightPx).roundToInt()
                                    val target = (row.index + delta).coerceIn(0, playlist.lastIndex)
                                    if (target != row.index) onMoveSong(row.index, target)
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

        if (!nowPlayingVisible && playlist.isNotEmpty() && didInitialScroll) {
            AssistChip(
                onClick = {
                    scope.launch {
                        val target = if (sections.hasHistory()) 1 else 0
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

private fun isNowPlayingVisible(listState: LazyListState): Boolean {
    val visible = listState.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return true
    return visible.any { it.key == "now_playing" }
}

@Composable
fun QueueCollapseHandle(
    accentColor: Color,
    onClose: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handleInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SgSpacing.hitTarget)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onVerticalDrag = { _, amount -> onVerticalDrag(amount) },
                )
            }
            .clickable(
                interactionSource = handleInteraction,
                indication = null,
                onClick = onClose,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(SgSpacing.sm))
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(SgRadius.pill))
                .background(accentColor.copy(alpha = 0.38f))
                .border(1.dp, GlassBorder.copy(alpha = 0.45f), RoundedCornerShape(SgRadius.pill)),
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = "Fermer la file",
            tint = TextPrimary.copy(alpha = 0.55f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun QueueSectionHeader(
    title: String,
    meta: String,
    accentColor: Color,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SgRadius.sm))
            .clickable(onClick = onClick)
            .padding(vertical = SgSpacing.sm, horizontal = SgSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = TextPrimary.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = meta,
            color = accentColor.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Replier l'historique" else "Déplier l'historique",
            tint = TextPrimary.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(start = SgSpacing.sm)
                .size(18.dp),
        )
    }
}

@Composable
private fun QueueUpcomingHeader(
    count: Int,
    accentColor: Color,
    canClear: Boolean,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SgSpacing.sm, bottom = SgSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "À suivre",
            color = TextPrimary.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            color = accentColor.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
        )
        if (canClear) {
            TextButton(onClick = onClear) {
                Text("Tout effacer", color = accentColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun QueueNowPlayingCard(
    song: Song,
    isPlaying: Boolean,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(cornerRadius = SgRadius.lg, accentColor = accentColor)
            .padding(horizontal = SgSpacing.lg, vertical = SgSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QueueArt(song = song, accentColor = accentColor, size = 56)
        Spacer(modifier = Modifier.width(SgSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "En cours",
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
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
                color = TextPrimary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NowPlayingBars(isPlaying = isPlaying, accentColor = accentColor, barHeight = 16.dp)
    }
}

@Composable
private fun QueueHistoryRow(
    song: Song,
    accentColor: Color,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SgRadius.md))
            .glassEffect(cornerRadius = SgRadius.md, accentColor = accentColor)
            .clickable(onClick = onPlay)
            .padding(horizontal = SgSpacing.md, vertical = SgSpacing.md)
            .alpha(0.78f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QueueArt(song = song, accentColor = accentColor, size = 40)
        Spacer(modifier = Modifier.width(SgSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.displayTitle(),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.displayArtist(),
                color = TextPrimary.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QueueUpcomingRow(
    song: Song,
    isDragging: Boolean,
    dragOffsetY: Float,
    accentColor: Color,
    onPlay: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = SgMotion.SpringSoft,
        label = "queue_drag_scale",
    )
    val duration = QueuePresentation.formatRemaining(song.duration)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
            }
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .glassEffect(cornerRadius = SgRadius.md, accentColor = accentColor)
            .clickable(onClick = onPlay)
            .padding(horizontal = SgSpacing.md, vertical = SgSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_drag),
            contentDescription = "Réordonner",
            tint = TextPrimary.copy(alpha = 0.45f),
            modifier = Modifier
                .size(28.dp)
                .pointerInput(song.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        },
                    )
                },
        )
        Spacer(modifier = Modifier.width(SgSpacing.xs))
        QueueArt(song = song, accentColor = accentColor, size = 44)
        Spacer(modifier = Modifier.width(SgSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.displayTitle(),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.displayArtist(),
                color = TextPrimary.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (duration != "0:00") {
            Text(
                text = duration,
                color = TextPrimary.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun QueueArt(song: Song, accentColor: Color, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(SgRadius.sm))
            .background(GraphiteCard),
        contentAlignment = Alignment.Center,
    ) {
        if (song.albumArtUri != null) {
            SgCoverImage(
                albumArtUri = song.albumArtUri,
                uriCrossfade = false,
                decodeEdgeDp = size.dp,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_songs),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size((size * 0.4f).dp),
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
    val metaInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassEffect(cornerRadius = 0.dp, accentColor = accentColor),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = SgSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = metaInteraction,
                        indication = null,
                        onClick = onExpand,
                    ),
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
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(SgSpacing.hitTarget)
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
                        .size(48.dp)
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
                        .size(SgSpacing.hitTarget)
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
                .size(SgSpacing.hitTarget)
                .clickable(onClick = onExpand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = "Fermer la file",
                tint = TextPrimary.copy(alpha = 0.62f),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
