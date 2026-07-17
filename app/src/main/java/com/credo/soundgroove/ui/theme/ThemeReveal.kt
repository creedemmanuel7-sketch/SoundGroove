package com.credo.soundgroove.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Révélation circulaire / vague en demi-cercle lors d'un changement de [AppTheme].
 * Le nouveau thème se déploie depuis le point d'origine (carte ou ligne paramètres).
 */
@Stable
class SgThemeRevealState internal constructor(
    internal val progress: Animatable<Float, AnimationVector1D>,
    internal val reducedMotion: Boolean
) {
    internal var overlayTheme by mutableStateOf<AppTheme?>(null)
    internal var origin by mutableStateOf(Offset.Zero)
    internal var animating by mutableStateOf(false)

    val isAnimating: Boolean get() = animating

    suspend fun reveal(
        targetTheme: AppTheme,
        originInWindow: Offset,
        onCommit: suspend (AppTheme) -> Unit
    ) {
        if (animating) return
        overlayTheme = targetTheme
        origin = originInWindow
        if (reducedMotion) {
            onCommit(targetTheme)
            overlayTheme = null
            progress.snapTo(0f)
            return
        }
        animating = true
        progress.snapTo(0f)
        progress.animateTo(1f, SgMotion.themeRevealSpec())
        onCommit(targetTheme)
        overlayTheme = null
        progress.snapTo(0f)
        animating = false
    }
}

@Composable
fun rememberSgThemeRevealState(): SgThemeRevealState {
    val reducedMotion = rememberSgReducedMotion()
    val progress = remember { Animatable(0f) }
    return remember(reducedMotion) { SgThemeRevealState(progress, reducedMotion) }
}

private class CircularRevealShape(
    private val progress: Float,
    private val center: Offset
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (progress >= 1f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val maxRadius = hypot(size.width, size.height) * 1.15f
        val radius = maxRadius * progress.coerceIn(0f, 1f)
        val path = Path().apply {
            addOval(
                Rect(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius
                )
            )
        }
        return Outline.Generic(path)
    }
}

@Composable
fun SgThemeRevealHost(
    baseTheme: AppTheme,
    revealState: SgThemeRevealState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(AppTheme) -> Unit
) {
    val overlayTheme = revealState.overlayTheme
    val progress = revealState.progress.value
    val originInWindow = revealState.origin
    var hostPositionInWindow by remember { mutableStateOf(Offset.Zero) }
    val localOrigin = originInWindow - hostPositionInWindow

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { hostPositionInWindow = it.positionInWindow() }
    ) {
        content(baseTheme)

        if (overlayTheme != null && progress > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircularRevealShape(progress, localOrigin))
            ) {
                content(overlayTheme)
            }
        }
    }
}

/** Centre de la zone en coordonnées fenêtre — origine du balayage circulaire. */
@Composable
fun Modifier.sgThemeRevealOrigin(onCenterInWindow: (Offset) -> Unit): Modifier = onGloballyPositioned { coords ->
    onCenterInWindow(coords.boundsInWindow().center)
}

/** Lance une révélation de thème depuis [origin] puis appelle [onThemeCommitted]. */
fun launchThemeReveal(
    revealState: SgThemeRevealState,
    scope: kotlinx.coroutines.CoroutineScope,
    targetTheme: AppTheme,
    currentTheme: AppTheme,
    origin: Offset,
    onThemeCommitted: (AppTheme) -> Unit
) {
    if (targetTheme == currentTheme || revealState.isAnimating) return
    scope.launch {
        revealState.reveal(targetTheme, origin) { theme ->
            onThemeCommitted(theme)
        }
    }
}
