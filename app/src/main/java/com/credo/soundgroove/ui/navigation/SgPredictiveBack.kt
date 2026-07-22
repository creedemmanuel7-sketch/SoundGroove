package com.credo.soundgroove.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.ui.components.GestureHintBanner
import com.credo.soundgroove.ui.components.rememberGestureHintState
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
import com.credo.soundgroove.util.GestureHintIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * Branche le back prédictif Android 13+ sur un progrès 0→1 — même contrat que
 * [com.credo.soundgroove.ui.screens.PlayerScreen] (POC S3).
 */
@Composable
fun SgPredictiveBackHandler(
    enabled: Boolean,
    reducedMotion: Boolean = rememberSgReducedMotion(),
    commitFraction: Float = 0.38f,
    currentProgress: () -> Float,
    onApplyProgress: suspend (Float) -> Unit,
    onCancelProgress: suspend () -> Unit,
    onBackCommitted: (fromInteractiveDrag: Boolean) -> Unit,
) {
    val predictiveEnabled = enabled && !reducedMotion

    PredictiveBackHandler(enabled = predictiveEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                onApplyProgress(backEvent.progress)
            }
        } catch (e: CancellationException) {
            onCancelProgress()
            throw e
        }
    }

    BackHandler(enabled = enabled) {
        onBackCommitted(currentProgress() >= commitFraction)
    }
}

/**
 * Enveloppe pour routes NavHost secondaires : slide + fade synchronisés au back
 * prédictif (miroir de [SgMotion.navPopExit]).
 */
@Composable
fun SgPredictivePopHost(
    enabled: Boolean = true,
    onBack: () -> Unit,
    showDetailBackHint: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reducedMotion = rememberSgReducedMotion()
    val popProgressAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val commitFraction = 0.38f
    val detailHint = rememberGestureHintState(GestureHintIds.DETAIL_BACK)

    suspend fun applyPopDrag(raw: Float) {
        val p = raw.coerceIn(0f, 1f)
        popProgressAnim.snapTo(p)
    }

    suspend fun cancelPopDrag() {
        if (reducedMotion) {
            popProgressAnim.snapTo(0f)
        } else {
            popProgressAnim.animateTo(0f, animationSpec = SgMotion.SpringSnappy)
        }
    }

    SgPredictiveBackHandler(
        enabled = enabled,
        reducedMotion = reducedMotion,
        commitFraction = commitFraction,
        currentProgress = { popProgressAnim.value },
        onApplyProgress = { applyPopDrag(it) },
        onCancelProgress = { cancelPopDrag() },
        onBackCommitted = { fromDrag ->
            scope.launch {
                if (fromDrag) {
                    popProgressAnim.snapTo(1f)
                }
                onBack()
            }
        },
    )

    Box(
        modifier = modifier.graphicsLayer {
            val p = popProgressAnim.value
            if (p > 0f) {
                translationX = p * size.width * 0.5f
                alpha = lerpFloat(1f, 0.7f, p)
            }
        }
    ) {
        content()
        if (showDetailBackHint) {
            GestureHintBanner(
                text = "Glisser depuis le bord gauche ou utiliser Retour pour revenir",
                visible = detailHint.visible,
                onDismiss = detailHint.dismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }
    }
}
