package com.credo.soundgroove.ui.screens

import android.widget.Toast
import com.credo.soundgroove.ui.components.GestureHintBanner
import com.credo.soundgroove.ui.components.rememberGestureHintState
import com.credo.soundgroove.ui.navigation.SgPredictiveBackHandler
import com.credo.soundgroove.util.GestureHintIds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.snap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.lyrics.LyricLine
import com.credo.soundgroove.lyrics.LyricsContent
import com.credo.soundgroove.lyrics.LyricsViewModel
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.SgTapTarget
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
import com.credo.soundgroove.ui.theme.sgCoilCrossfadeMs
import com.credo.soundgroove.util.LyricsChromePrimaryText
import com.credo.soundgroove.util.LyricsPalette
import com.credo.soundgroove.util.PlayerGuards
import com.credo.soundgroove.util.displayTitle
import com.credo.soundgroove.util.rememberLyricsPalette
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Écran Paroles — immersif plein écran, au même niveau que [PlayerScreen] : on
 * bascule entre les deux via un swipe (horizontal ou vertical) ou le bouton
 * dédié, jamais via un simple "pity popup" en bottom sheet.
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
    onOpenWebSearch: () -> Unit,
    pendingPasteText: String? = null,
    onPendingPasteConsumed: () -> Unit = {},
    isPlaying: Boolean = false,
    onPlayPause: () -> Unit = {},
    // Transition "peek" annulable, symétrique de celle pilotée depuis PlayerScreen :
    // 0 = Player plein, 1 = Paroles plein (valeur partagée, propriété d'AppNavigation).
    // Par défaut 1 (plein écran immédiat) pour les appelants qui ne branchent pas
    // encore le peek (previews, tests) — cf. Motion.kt pour la même logique côté Player.
    peekProgress: Float = 1f,
    onPeekDragStart: () -> Unit = {},
    onPeekDrag: (Float) -> Unit = {},
    onPeekDragEnd: () -> Unit = {},
    onPredictiveBackApply: suspend (Float) -> Unit = {},
    onPredictiveBackCancel: suspend () -> Unit = {},
    predictiveBackProgress: () -> Float = { 0f },
    /** Décalage sync LRC global (ms), synchronisé depuis Options ou Paramètres. */
    lyricsSyncOffsetMs: Long = LyricsViewModel.DEFAULT_SYNC_OFFSET_MS,
    viewModel: LyricsViewModel = viewModel()
) {
    val context = LocalContext.current
    var verticalDragOffset by remember { mutableStateOf(0f) }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var screenWidthPx by remember { mutableStateOf(1f) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Bouton "toggle" clair vers le Player (icône) + geste natif : back système
    // et swipe (bas) doivent produire exactement le même résultat. `enabled` sur le
    // progrès de peek : désactivé quand l'écran n'est pas au premier plan (progrès à
    // 0, potentiellement encore monté juste après un dismiss) pour ne pas voler le
    // back destiné au Player en dessous.
    val lyricsBackHint = rememberGestureHintState(GestureHintIds.LYRICS_BACK)
    val reducedMotion = rememberSgReducedMotion()
    val lyricsPredictiveEnabled = peekProgress > 0.001f

    SgPredictiveBackHandler(
        enabled = lyricsPredictiveEnabled,
        reducedMotion = reducedMotion,
        currentProgress = predictiveBackProgress,
        onApplyProgress = onPredictiveBackApply,
        onCancelProgress = onPredictiveBackCancel,
        onBackCommitted = { fromDrag ->
            if (!fromDrag || predictiveBackProgress() >= 0.38f) {
                onClose()
            }
        },
    )

    LaunchedEffect(pendingPasteText) {
        pendingPasteText?.let { text ->
            viewModel.startEditing(text)
            onPendingPasteConsumed()
        }
    }

    LaunchedEffect(song.id) {
        viewModel.loadLyricsForSong(song)
    }

    LaunchedEffect(lyricsSyncOffsetMs) {
        viewModel.setSyncOffsetMs(lyricsSyncOffsetMs)
    }

    LaunchedEffect(playbackPosition) {
        viewModel.updatePlaybackPosition(playbackPosition)
    }

    val content by viewModel.lyricsContent.collectAsState()
    val currentLineIndex by viewModel.currentLineIndex.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val editingDraft by viewModel.editingDraft.collectAsState()

    // Palette dérivée de la pochette (cf. AlbumArtPalette.rememberLyricsPalette) :
    // fond sombre premium + accent garanti lisible, cohérent avec le Player mais
    // pensé pour un long temps de lecture (contraste texte renforcé).
    val palette = rememberLyricsPalette(song.albumArtUri, accentColor)
    val effectiveAccent = palette.activeText

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription =
                    "Paroles. Glisser vers le bas ou la droite pour revenir au lecteur."
                customActions = listOf(
                    CustomAccessibilityAction("Revenir au lecteur") {
                        onClose()
                        true
                    },
                )
            }
            .onSizeChanged { screenWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            // Peek symétrique de celui du Player (cf. PlayerScreen.graphicsLayer) :
            // Paroles entre/sort par la droite, translationX dérivé du même progrès
            // partagé — swipe gauche→droite ici décroît le progrès (retour au Player).
            .graphicsLayer { translationX = (1f - peekProgress) * size.width }
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (verticalDragOffset > 120f) onClose()
                        verticalDragOffset = 0f
                    },
                    onDragCancel = { verticalDragOffset = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        verticalDragOffset += dragAmount
                    }
                )
            }
            .pointerInput(Unit) {
                // Swipe droite = retour au Player (peek annulable, cf. PlayerScreen pour
                // le sens symétrique) : ne rapporte que le geste brut, AppNavigation
                // décide seuil/cancel/spring puisqu'il possède le progrès partagé.
                detectHorizontalDragGestures(
                    onDragStart = { onPeekDragStart() },
                    onDragEnd = { onPeekDragEnd() },
                    onDragCancel = { onPeekDragEnd() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        onPeekDrag(-dragAmount / screenWidthPx)
                    }
                )
            }
    ) {
        // Fond immersif : pochette floutée + gradient. Mode perf : Palette plate, blur=0.
        val coilCrossfadeMs = sgCoilCrossfadeMs(SgMotion.FastMs)
        if (!reducedMotion && song.albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .size(480, 960)
                    .crossfade(coilCrossfadeMs)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(46.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.backgroundTop.copy(alpha = 0.92f),
                            palette.backgroundCenter.copy(alpha = 0.95f),
                            palette.backgroundBottom.copy(alpha = 0.97f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Même respiration en haut d'écran que PlayerScreen : les deux écrans
            // doivent se sentir comme deux faces d'un même objet, pas deux styles.
            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sortie secondaire : gestes (swipe bas/droite) et BackHandler = primaires ;
                // icône discrète mais cible 48dp conservée pour TalkBack / Fitts.
                SgTapTarget(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_down),
                        contentDescription = "Revenir au lecteur",
                        tint = palette.secondaryText.copy(alpha = 0.72f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PAROLES",
                        color = palette.secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = song.displayTitle(),
                        color = effectiveAccent,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(palette.glassSurface, CircleShape)
                        .border(1.dp, palette.glassBorder, CircleShape)
                        .clickable { autoScrollEnabled = !autoScrollEnabled },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (autoScrollEnabled) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                        contentDescription = if (autoScrollEnabled) {
                            "Désactiver le défilement automatique"
                        } else {
                            "Activer le défilement automatique"
                        },
                        tint = if (autoScrollEnabled) effectiveAccent else palette.secondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
                        accentColor = effectiveAccent,
                        palette = palette,
                        initialDraft = editingDraft,
                        isSaving = isSaving,
                        saveError = saveError,
                        isEditMode = content is LyricsContent.Synced || content is LyricsContent.PlainText,
                        onCancel = { viewModel.cancelEditing() },
                        onSave = { raw -> viewModel.saveLyrics(song, raw) }
                    )
                    content is LyricsContent.Loading -> LoadingLyricsState(effectiveAccent)
                    content is LyricsContent.SearchingOnline -> SearchingOnlineLyricsState(effectiveAccent, palette)
                    content is LyricsContent.Synced -> SyncedLyricsList(
                        lines = (content as LyricsContent.Synced).lines,
                        currentLineIndex = currentLineIndex,
                        accentColor = effectiveAccent,
                        inactiveColor = palette.inactiveText,
                        autoScrollEnabled = autoScrollEnabled,
                        onLineClick = { timeMs -> PlayerGuards.safeSeekToPosition(player, timeMs) }
                    )
                    content is LyricsContent.PlainText -> PlainTextLyrics(
                        text = (content as LyricsContent.PlainText).text,
                        palette = palette,
                        playbackPosition = playbackPosition,
                        durationMs = song.duration,
                        autoScrollEnabled = autoScrollEnabled
                    )
                    else -> EmptyLyricsState(
                        accentColor = effectiveAccent,
                        palette = palette,
                        onSearchOnline = onOpenWebSearch,
                        onPasteLyrics = { viewModel.startEditing() }
                    )
                }
            }

            val hasEditableLyrics = !isEditing &&
                (content is LyricsContent.Synced || content is LyricsContent.PlainText)
            if (hasEditableLyrics) {
                Spacer(modifier = Modifier.height(4.dp))
                LyricsManageActions(
                    accentColor = effectiveAccent,
                    palette = palette,
                    onEdit = { viewModel.startEditingExisting(song) },
                    onDelete = { showDeleteConfirm = true }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barre compacte façon Spotify : pochette discrète + play/pause central,
            // sans prev/next (réservés au Player et au mini-player).
            LyricsPlaybackBar(
                isPlaying = isPlaying,
                progress = (playbackPosition.toFloat() / (song.duration.takeIf { it > 0L } ?: 1L).toFloat())
                    .coerceIn(0f, 1f),
                palette = palette,
                accentColor = effectiveAccent,
                onPlayPause = onPlayPause,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 4.dp)
            )
        }

        if (peekProgress > 0.5f) {
            GestureHintBanner(
                text = "Glisser vers le bas ou la droite pour revenir au lecteur",
                visible = lyricsBackHint.visible,
                onDismiss = lyricsBackHint.dismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
            )
        }
    }

    var wasEditing by remember { mutableStateOf(false) }
    LaunchedEffect(isEditing, content, isSaving) {
        if (wasEditing && !isEditing && !isSaving && content !is LyricsContent.NotFound && content !is LyricsContent.Loading) {
            Toast.makeText(context, "Paroles enregistrées", Toast.LENGTH_SHORT).show()
        }
        wasEditing = isEditing
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text("Supprimer les paroles ?", color = palette.primaryText)
            },
            text = {
                Text(
                    "Les paroles associées à ce morceau seront effacées du cache. " +
                        "La lecture audio n'est pas affectée. Vous pourrez en rechercher ou en coller de nouvelles.",
                    color = palette.secondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteLyrics(song)
                        Toast.makeText(context, "Paroles supprimées", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Supprimer", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Annuler", color = palette.secondaryText)
                }
            },
            containerColor = palette.surfaceElevated,
            titleContentColor = palette.primaryText,
            textContentColor = palette.secondaryText
        )
    }
}

@Composable
private fun LyricsManageActions(
    accentColor: Color,
    palette: LyricsPalette,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(SgRadius.pill),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primaryText),
            border = BorderStroke(1.dp, palette.glassBorder)
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Modifier", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(SgRadius.pill),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
            border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Supprimer", fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Barre ultra-minimale en bas de l'écran Paroles (style Spotify) —
 * progress 1dp en tête + play/pause flottant centré. Prev/next restent sur
 * [PlayerScreen] et le mini-player.
 */
@Composable
private fun LyricsPlaybackBar(
    isPlaying: Boolean,
    progress: Float,
    palette: LyricsPalette,
    accentColor: Color,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = SgMotion.tweenProgress(),
        label = "lyricsBarProgress"
    )
    val progressFraction = animatedProgress.coerceIn(0f, 1f)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.glassBorder.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction.coerceAtLeast(0.005f))
                    .fillMaxSize()
                    .background(accentColor.copy(alpha = 0.92f))
            )
        }
        SgTapTarget(
            onClick = onPlayPause,
            minSize = 48.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (isPlaying) "Pause" else "Lecture",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PasteLyricsEditor(
    song: Song,
    accentColor: Color,
    palette: LyricsPalette,
    initialDraft: String,
    isSaving: Boolean,
    saveError: String?,
    isEditMode: Boolean = false,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(song.id, initialDraft) { mutableStateOf(initialDraft) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = if (isEditMode) "Modifier les paroles" else "Coller ou saisir les paroles",
            color = palette.primaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isEditMode) {
                "Modifiez le texte puis enregistrez. Les horodatages LRC ([mm:ss.xx]) sont conservés s'ils restent présents."
            } else {
                "Copiez depuis Genius, AZLyrics ou un autre site, puis collez ici. Format LRC avec horodatages accepté."
            },
            color = palette.secondaryText,
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
                    color = palette.tertiaryText,
                    fontSize = 14.sp
                )
            },
            enabled = !isSaving,
            shape = RoundedCornerShape(SgRadius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor.copy(alpha = 0.5f),
                unfocusedBorderColor = palette.borderSubtle.copy(alpha = 0.45f),
                focusedContainerColor = palette.surfaceElevated.copy(alpha = 0.45f),
                unfocusedContainerColor = palette.surfaceElevated.copy(alpha = 0.28f),
                focusedTextColor = palette.primaryText,
                unfocusedTextColor = palette.primaryText,
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.secondaryText)
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
    inactiveColor: Color,
    autoScrollEnabled: Boolean,
    onLineClick: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberSgReducedMotion()
    var lastScrolledLine by remember { mutableStateOf(-1) }

    LaunchedEffect(currentLineIndex, lines.size, autoScrollEnabled, reducedMotion) {
        if (autoScrollEnabled && currentLineIndex >= 0 && lines.isNotEmpty()) {
            val targetIndex = (currentLineIndex - 2).coerceIn(0, lines.lastIndex)
            val gap = if (lastScrolledLine < 0) 0 else kotlin.math.abs(currentLineIndex - lastScrolledLine)
            scope.launch {
                runCatching {
                    // Snap si Mode perf / reduced motion, ou saut > 3 lignes (R7).
                    if (reducedMotion || gap > 3) {
                        listState.scrollToItem(targetIndex)
                    } else {
                        val visible = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == targetIndex }
                        if (visible != null) {
                            listState.animateScrollBy(
                                visible.offset.toFloat(),
                                animationSpec = SgMotion.tweenMedium()
                            )
                        } else {
                            listState.animateScrollToItem(targetIndex)
                        }
                    }
                }
                lastScrolledLine = currentLineIndex
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            vertical = 140.dp,
            horizontal = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
            val isActive = index == currentLineIndex
            val isPast = index < currentLineIndex

            // Actif : couleur MediumMs + scale 1.06 SpringSoft + halo 0.14
            // Mode perf : color swap only (pas de scale/halo animé).
            val colorSpec = if (reducedMotion) snap() else SgMotion.tweenMediumOf<Color>()
            val color by animateColorAsState(
                targetValue = when {
                    isActive -> LyricsChromePrimaryText
                    isPast -> inactiveColor.copy(alpha = 0.42f)
                    else -> inactiveColor
                },
                animationSpec = colorSpec,
                label = "lyricLineColor"
            )
            val scale by animateFloatAsState(
                targetValue = if (isActive && !reducedMotion) SgMotion.LyricsActiveScale else 1f,
                animationSpec = if (reducedMotion) snap() else SgMotion.SpringSoft,
                label = "lyricLineScale"
            )
            val highlightAlpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0f,
                animationSpec = if (reducedMotion) snap() else SgMotion.tweenMediumOf(),
                label = "lyricLineHighlight"
            )
            val haloAlpha = if (reducedMotion) {
                if (isActive) SgMotion.LyricsHaloAlpha else 0f
            } else {
                SgMotion.LyricsHaloAlpha * highlightAlpha
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(SgRadius.md))
                    .background(
                        if (haloAlpha > 0f) accentColor.copy(alpha = haloAlpha)
                        else Color.Transparent
                    )
                    .clickable { runCatching { onLineClick(line.timeMs) } }
                    .padding(horizontal = SgSpacing.md, vertical = SgSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(SgRadius.pill))
                        .background(accentColor.copy(alpha = if (reducedMotion) {
                            if (isActive) 1f else 0f
                        } else highlightAlpha))
                )
                Spacer(modifier = Modifier.width(SgSpacing.md))
                Text(
                    text = line.text.ifBlank { "···" },
                    color = color,
                    fontSize = if (isActive) 18.sp else 16.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    lineHeight = 24.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PlainTextLyrics(
    text: String,
    palette: LyricsPalette,
    playbackPosition: Long,
    durationMs: Long,
    autoScrollEnabled: Boolean
) {
    val scrollState = rememberScrollState()
    val reducedMotion = rememberSgReducedMotion()
    var userPausedUntilMs by remember { mutableLongStateOf(0L) }

    val userScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    userPausedUntilMs = System.currentTimeMillis() + PLAIN_TEXT_AUTO_SCROLL_RESUME_MS
                }
                return Offset.Zero
            }
        }
    }

    // Défilement proportionnel au progrès de lecture (pas de sync ligne-à-ligne).
    LaunchedEffect(autoScrollEnabled, durationMs, text, reducedMotion) {
        if (!autoScrollEnabled || durationMs <= 0L) return@LaunchedEffect
        snapshotFlow { playbackPosition }
            .distinctUntilChanged { old, new -> abs(old - new) < 80L }
            .collect { positionMs ->
                if (System.currentTimeMillis() < userPausedUntilMs) return@collect
                val maxScroll = scrollState.maxValue
                if (maxScroll <= 0) return@collect
                val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val target = (maxScroll * progress).toInt()
                val gap = abs(scrollState.value - target)
                if (gap < 2) return@collect
                when {
                    reducedMotion || gap < 48 -> scrollState.scrollTo(target)
                    gap > maxScroll / 4 -> scrollState.animateScrollTo(
                        target,
                        animationSpec = SgMotion.tweenMedium()
                    )
                    else -> scrollState.animateScrollTo(
                        target,
                        animationSpec = SgMotion.tweenProgress()
                    )
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(userScrollConnection)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Marges haut/bas pour que le début et la fin du texte aient une zone de scroll utile.
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = text.ifBlank { "Aucune parole trouvée." },
            color = palette.primaryText,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(120.dp))
    }
}

private const val PLAIN_TEXT_AUTO_SCROLL_RESUME_MS = 4_000L

@Composable
private fun LoadingLyricsState(accentColor: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = accentColor)
    }
}

@Composable
private fun SearchingOnlineLyricsState(accentColor: Color, palette: LyricsPalette) {
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
            color = palette.primaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Interrogation de LRCLIB pour ce morceau",
            color = palette.secondaryText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyLyricsState(
    accentColor: Color,
    palette: LyricsPalette,
    onSearchOnline: () -> Unit,
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
                .background(palette.glassSurface, CircleShape)
                .border(1.dp, palette.glassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lyrics,
                contentDescription = null,
                tint = palette.secondaryText,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Aucune parole disponible",
            color = palette.primaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Recherchez les paroles en ligne, copiez-les, puis enregistrez-les pour ce morceau. Les paroles trouvées automatiquement ou saisies manuellement seront mémorisées pour les prochaines écoutes.",
            color = palette.secondaryText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onSearchOnline,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SgRadius.pill),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.Black
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chercher en ligne", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onPasteLyrics,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SgRadius.pill),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primaryText)
        ) {
            Text("Coller les paroles")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
