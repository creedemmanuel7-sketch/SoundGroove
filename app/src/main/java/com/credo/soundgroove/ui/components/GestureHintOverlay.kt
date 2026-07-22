package com.credo.soundgroove.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
import com.credo.soundgroove.util.GestureHintIds
import com.credo.soundgroove.util.GestureHintsStore
import kotlinx.coroutines.launch

class GestureHintState(
    val visible: Boolean,
    val dismiss: () -> Unit,
)

@Composable
fun rememberGestureHintState(hintId: String): GestureHintState {
    val context = LocalContext.current
    val store = remember { GestureHintsStore(context) }
    val scope = rememberCoroutineScope()
    val seenHints by store.seenHints.collectAsState(initial = null)
    val visible = seenHints?.let { hintId !in it } == true
    return remember(hintId, visible) {
        GestureHintState(
            visible = visible,
            dismiss = { scope.launch { store.markSeen(hintId) } },
        )
    }
}

@Composable
fun GestureHintBanner(
    text: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberSgReducedMotion()

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(SgMotion.tweenFastOf()) + slideInVertically(initialOffsetY = { it / 3 })
        },
        exit = if (reducedMotion) ExitTransition.None else fadeOut(SgMotion.tweenFastAccelOf()),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(SgRadius.md))
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = text }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Masquer l'indication",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(2.dp),
            )
        }
    }
}

private val playerGestureHints = listOf(
    GestureHintIds.PLAYER_DISMISS to "Glisser vers le bas pour réduire au mini-player",
    GestureHintIds.PLAYER_QUEUE to "Glisser vers le haut pour ouvrir la file d'attente",
    GestureHintIds.PLAYER_LYRICS to "Glisser horizontalement pour afficher les paroles",
)

@Composable
fun PlayerGestureHints(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { GestureHintsStore(context) }
    val seenHints by store.seenHints.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val activeHint = seenHints?.let { seen ->
        playerGestureHints.firstOrNull { (id, _) -> id !in seen }
    }

    if (activeHint != null) {
        val (hintId, text) = activeHint
        GestureHintBanner(
            text = text,
            visible = true,
            onDismiss = { scope.launch { store.markSeen(hintId) } },
            modifier = modifier,
        )
    }
}
