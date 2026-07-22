package com.credo.soundgroove.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.GestureHintBanner
import com.credo.soundgroove.ui.components.SgEmptyState
import com.credo.soundgroove.ui.components.rememberGestureHintState
import com.credo.soundgroove.ui.theme.CardSurface
import com.credo.soundgroove.ui.theme.ErrorRed
import com.credo.soundgroove.ui.theme.GlassBorder
import com.credo.soundgroove.ui.theme.GlassCard
import com.credo.soundgroove.ui.theme.GraphiteCard
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.SilverAccent
import com.credo.soundgroove.ui.theme.SurfaceElevated
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.sgPressScale
import com.credo.soundgroove.ui.theme.sgCoilCrossfadeMs
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
import com.credo.soundgroove.ui.theme.sgSheetGradientBrush
import com.credo.soundgroove.util.GestureHintIds
import com.credo.soundgroove.util.displayArtist
import com.credo.soundgroove.util.displayTitle
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    playlist: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean = true,
    accentColor: Color = SilverAccent,
    onClose: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var verticalDragOffset by remember { mutableStateOf(0f) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var itemDragOffset by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberSgReducedMotion()
    val queueCloseHint = rememberGestureHintState(GestureHintIds.QUEUE_CLOSE)
    val safeCurrentIndex = currentIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
    val currentSong = playlist.getOrNull(safeCurrentIndex)

    LaunchedEffect(playlist.size, safeCurrentIndex, reducedMotion) {
        if (playlist.isNotEmpty() && safeCurrentIndex in playlist.indices) {
            if (reducedMotion) listState.scrollToItem(safeCurrentIndex)
            else listState.animateScrollToItem(safeCurrentIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // ~3/4 d'écran (cf. correction utilisateur : bandeau Player 1/4, Queue 3/4).
            .fillMaxHeight(0.75f)
            .clip(RoundedCornerShape(topStart = SgRadius.xl, topEnd = SgRadius.xl))
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
                .padding(horizontal = SgSpacing.xl)
        ) {
            Spacer(modifier = Modifier.height(SgSpacing.md))

            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(GlassBorder, RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(SgSpacing.lg))

            QueueHeader(
                trackCount = playlist.size,
                accentColor = accentColor,
                onClose = onClose
            )

            AnimatedVisibility(
                visible = currentSong != null,
                enter = if (reducedMotion) EnterTransition.None else fadeIn(SgMotion.tweenFastOf()) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = SgMotion.tweenFastOf()
                ),
                exit = if (reducedMotion) ExitTransition.None else fadeOut(SgMotion.tweenFastAccelOf()) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = SgMotion.tweenFastAccelOf()
                )
            ) {
                currentSong?.let { song ->
                    Spacer(modifier = Modifier.height(SgSpacing.lg))
                    QueueNowPlayingBanner(
                        song = song,
                        position = safeCurrentIndex + 1,
                        total = playlist.size,
                        isPlaying = isPlaying,
                        accentColor = accentColor
                    )
                }
            }

            AnimatedVisibility(
                visible = playlist.isNotEmpty(),
                enter = if (reducedMotion) EnterTransition.None else fadeIn(SgMotion.tweenFastOf()),
                exit = if (reducedMotion) ExitTransition.None else fadeOut(SgMotion.tweenFastAccelOf())
            ) {
                Column {
                    Spacer(modifier = Modifier.height(SgSpacing.md))
                    QueueHintRow()
                }
            }

            Spacer(modifier = Modifier.height(SgSpacing.md))

            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = playlist.isEmpty(),
                    enter = if (reducedMotion) EnterTransition.None else fadeIn(SgMotion.tweenFastOf()) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = SgMotion.tweenFastOf()
                    ),
                    exit = if (reducedMotion) ExitTransition.None else fadeOut(SgMotion.tweenFastAccelOf()) + scaleOut(
                        targetScale = 0.94f,
                        animationSpec = SgMotion.tweenFastAccelOf()
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    SgEmptyState(
                        iconPainter = painterResource(R.drawable.ic_queue),
                        title = "File d'attente vide",
                        subtitle = "Lance une lecture depuis la bibliothèque ou une playlist pour remplir la file.",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = playlist.isNotEmpty(),
                    enter = if (reducedMotion) EnterTransition.None else fadeIn(SgMotion.tweenFastOf()),
                    exit = if (reducedMotion) ExitTransition.None else fadeOut(SgMotion.tweenFastAccelOf()),
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(SgSpacing.sm)
                    ) {
                        itemsIndexed(
                            playlist,
                            key = { _, song -> song.id }
                        ) { index, song ->
                            val isCurrent = index == safeCurrentIndex
                            val isDragging = draggingIndex == index
                            val isCurrentPlaying = isCurrent && isPlaying
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value != SwipeToDismissBoxValue.Settled && !isCurrent) {
                                        scope.launch { onRemoveSong(index) }
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    QueueSwipeBackground(
                                        dismissState = dismissState,
                                        isCurrent = isCurrent
                                    )
                                }
                            ) {
                                QueueItemRow(
                                    song = song,
                                    index = index,
                                    isCurrent = isCurrent,
                                    isCurrentPlaying = isCurrentPlaying,
                                    isDragging = isDragging,
                                    itemDragOffset = if (isDragging) itemDragOffset else 0f,
                                    accentColor = accentColor,
                                    onPlay = { onPlaySong(index) },
                                    onDragStart = {
                                        draggingIndex = index
                                        itemDragOffset = 0f
                                    },
                                    onDrag = { amount ->
                                        itemDragOffset += amount
                                    },
                                    onDragEnd = {
                                        val rowHeightPx = 72f
                                        val steps = (itemDragOffset / rowHeightPx).toInt()
                                        val from = index
                                        val to = (index + steps).coerceIn(0, playlist.lastIndex)
                                        if (from != to) onMoveSong(from, to)
                                        draggingIndex = null
                                        itemDragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        itemDragOffset = 0f
                                    }
                                )
                            }
                        }
                    }
                }

                // "Revenir à la piste en cours" : utile dès que la file est longue et que
                // l'utilisateur a scrollé loin de l'item en lecture (cf. demande §A).
                val showScrollToCurrent by remember(listState, safeCurrentIndex) {
                    derivedStateOf {
                        playlist.isNotEmpty() &&
                            listState.layoutInfo.visibleItemsInfo.none { it.index == safeCurrentIndex }
                    }
                }
                com.credo.soundgroove.ui.components.ScrollToCurrentFab(
                    visible = showScrollToCurrent,
                    accentColor = accentColor,
                    onClick = { scope.launch { listState.animateScrollToItem(safeCurrentIndex) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = SgSpacing.sm, bottom = 96.dp)
                )
            }
        }

        GestureHintBanner(
            text = "Glisser vers le bas pour fermer la file",
            visible = queueCloseHint.visible,
            onDismiss = queueCloseHint.dismiss,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun QueueHeader(
    trackCount: Int,
    accentColor: Color,
    onClose: () -> Unit
) {
    val closeInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .sgPressScale(closeInteraction, pressedScale = 0.94f)
                .background(SurfaceElevated, CircleShape)
                .border(1.dp, GlassBorder.copy(alpha = 0.55f), CircleShape)
                .clickable(
                    interactionSource = closeInteraction,
                    indication = null,
                    onClick = onClose
                ),
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
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "File d'attente",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(SgSpacing.xs))
            AnimatedContent(
                targetState = trackCount,
                transitionSpec = {
                    fadeIn(SgMotion.tweenFastOf()) togetherWith fadeOut(SgMotion.tweenFastAccelOf())
                },
                label = "queue_count"
            ) { count ->
                Text(
                    text = queueCountLabel(count),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun QueueNowPlayingBanner(
    song: Song,
    position: Int,
    total: Int,
    isPlaying: Boolean,
    accentColor: Color
) {
    val coilCrossfadeMs = sgCoilCrossfadeMs(SgMotion.FastMs)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = SgRadius.md,
        accentColor = accentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
                .padding(horizontal = SgSpacing.md, vertical = SgSpacing.sm + 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(SgRadius.sm))
                    .background(GraphiteCard),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(coilCrossfadeMs)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_songs),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    com.credo.soundgroove.ui.components.NowPlayingBars(
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        barHeight = 14.dp
                    )
                }
            }

            Spacer(modifier = Modifier.width(SgSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EN COURS · $position / $total",
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.displayTitle(),
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist(),
                    color = TextPrimary.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QueueHintRow() {
    // Fond opaque SurfaceElevated + texte ~AA (évite le wash GlassSurface clair).
    val hintColor = TextPrimary.copy(alpha = 0.78f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SgRadius.sm))
            .background(SurfaceElevated.copy(alpha = 0.92f))
            .border(1.dp, GlassBorder.copy(alpha = 0.35f), RoundedCornerShape(SgRadius.sm))
            .padding(horizontal = SgSpacing.md, vertical = SgSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_trash),
            contentDescription = null,
            tint = hintColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(SgSpacing.xs))
        Text(
            text = "Glisser vers la gauche pour retirer",
            color = hintColor,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = " · ",
            color = hintColor.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall
        )
        Icon(
            painter = painterResource(R.drawable.ic_drag),
            contentDescription = null,
            tint = hintColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(SgSpacing.xs))
        Text(
            text = "Poignée pour réordonner",
            color = hintColor,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QueueSwipeBackground(
    dismissState: androidx.compose.material3.SwipeToDismissBoxState,
    isCurrent: Boolean
) {
    // Affiche « Retirer » seulement quand le swipe a une cible (évite le wash-out
    // du label blanc à travers la row au repos — row désormais opaque aussi).
    val revealed = !isCurrent && dismissState.targetValue != SwipeToDismissBoxValue.Settled
    val reducedMotion = rememberSgReducedMotion()
    val colorSpec = if (reducedMotion) snap<Color>() else SgMotion.tweenFastOf<Color>()
    val floatSpec = if (reducedMotion) snap<Float>() else SgMotion.tweenFastOf()
    val scaleSpec = if (reducedMotion) snap<Float>() else SgMotion.SpringSnappy

    val color by animateColorAsState(
        targetValue = if (revealed) ErrorRed.copy(alpha = 0.88f) else Color.Transparent,
        animationSpec = colorSpec,
        label = "swipe_color"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.7f,
        animationSpec = scaleSpec,
        label = "swipe_icon_scale"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = floatSpec,
        label = "swipe_label_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(SgRadius.md))
            .background(color)
            .padding(horizontal = SgSpacing.xl),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (!isCurrent) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                    alpha = labelAlpha
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_trash),
                    contentDescription = "Retirer",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(SgSpacing.sm))
                Text(
                    text = "Retirer",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    isCurrentPlaying: Boolean,
    isDragging: Boolean,
    itemDragOffset: Float,
    accentColor: Color,
    onPlay: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val handleInteraction = remember { MutableInteractionSource() }
    val coilCrossfadeMs = sgCoilCrossfadeMs(SgMotion.FastMs)
    val metaColor = TextPrimary.copy(alpha = 0.72f)
    val borderColor by animateColorAsState(
        targetValue = when {
            isDragging -> accentColor.copy(alpha = 0.55f)
            isCurrent -> accentColor.copy(alpha = 0.35f)
            else -> GlassBorder.copy(alpha = 0.45f)
        },
        animationSpec = SgMotion.tweenFastOf(),
        label = "item_border"
    )
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = SgMotion.SpringSoft,
        label = "drag_scale"
    )
    val dragElevation by animateFloatAsState(
        targetValue = if (isDragging) 6f else 0f,
        animationSpec = SgMotion.tweenFastOf(),
        label = "drag_elevation"
    )

    // Fond opaque (CardSurface) : évite le wash-out du label « Retirer » du swipe
    // à travers le GlassCard translucide.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = dragElevation
            }
            .offset { IntOffset(x = 0, y = itemDragOffset.toInt()) }
            .clip(RoundedCornerShape(SgRadius.md))
            .background(CardSurface.copy(alpha = 0.97f))
            .border(
                width = if (isCurrent || isDragging) 1.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(SgRadius.md)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isCurrent) {
                        Brush.linearGradient(
                            listOf(accentColor.copy(alpha = 0.14f), Color.Transparent)
                        )
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .clickable { onPlay() }
                .padding(
                    start = SgSpacing.md,
                    top = SgSpacing.sm + 2.dp,
                    bottom = SgSpacing.sm + 2.dp,
                    end = SgSpacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                color = if (isCurrent) accentColor else metaColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(24.dp)
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(SgRadius.sm))
                    .background(GraphiteCard),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(coilCrossfadeMs)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_songs),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        com.credo.soundgroove.ui.components.NowPlayingBars(
                            isPlaying = isCurrentPlaying,
                            accentColor = accentColor,
                            barHeight = 16.dp
                        )
                    }
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
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist(),
                    color = metaColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(SgSpacing.md))

            // Poignée sobre (SurfaceElevated) — séparée du contenu, sans carré gris clair.
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 40.dp)
                    .sgPressScale(handleInteraction, pressedScale = 0.94f, pressedAlpha = 0.78f)
                    .clip(RoundedCornerShape(SgRadius.sm))
                    .background(
                        if (isDragging) accentColor.copy(alpha = 0.16f)
                        else SurfaceElevated
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDragging) accentColor.copy(alpha = 0.4f)
                        else GlassBorder.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(SgRadius.sm)
                    )
                    .pointerInput(index) {
                        detectVerticalDragGestures(
                            onDragStart = { scope.launch { onDragStart() } },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                            onDragEnd = { scope.launch { onDragEnd() } },
                            onDragCancel = { scope.launch { onDragCancel() } }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.5.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(1.5.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    if (isDragging) accentColor
                                    else TextPrimary.copy(alpha = 0.55f)
                                )
                        )
                    }
                }
            }
        }
    }
}

private fun queueCountLabel(count: Int): String = when (count) {
    0 -> "Aucun titre"
    1 -> "1 titre"
    else -> "$count titres"
}

/**
 * Bandeau compact du Player quand la File d'attente est ouverte (cf. demande §B) :
 * le Player plein écran se réduit à ceci (~1/4 écran, cf. AppNavigation) — pochette,
 * titre/artiste, précédent/play-pause/suivant. Un tap ailleurs que sur les contrôles
 * referme la file et fait ré-expanser le Player (`onExpand`).
 */
@Composable
fun PlayerQueueBanner(
    song: Song,
    isPlaying: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prevInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val coilCrossfadeMs = sgCoilCrossfadeMs(SgMotion.FastMs)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(com.credo.soundgroove.ui.theme.sgFullScreenGradientBrush())
            .statusBarsPadding()
            .clickable { onExpand() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = SgSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 36.dp)
                        .background(GlassBorder, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(SgSpacing.md))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(SgRadius.sm))
                        .background(GraphiteCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(song.albumArtUri)
                                .crossfade(coilCrossfadeMs)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_songs),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
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
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.displayArtist(),
                        color = TextPrimary.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(SgSpacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .sgPressScale(prevInteraction, pressedScale = 0.9f)
                            .clickable(
                                interactionSource = prevInteraction,
                                indication = null,
                                onClick = onSkipPrevious
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_previous),
                            contentDescription = "Précédent",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .sgPressScale(playInteraction, pressedScale = 0.9f)
                            .clip(CircleShape)
                            .background(accentColor)
                            .clickable(
                                interactionSource = playInteraction,
                                indication = null,
                                onClick = onPlayPause
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .sgPressScale(nextInteraction, pressedScale = 0.9f)
                            .clickable(
                                interactionSource = nextInteraction,
                                indication = null,
                                onClick = onSkipNext
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_next),
                            contentDescription = "Suivant",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(GlassBorder.copy(alpha = 0.4f))
            )
        }
    }
}
