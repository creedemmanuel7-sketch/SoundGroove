package com.credo.soundgroove.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/** Durées et courbes partagées pour une motion cohérente dans toute l'app. */
object SgMotion {
    const val FastMs = 150
    const val MediumMs = 240
    const val SlowMs = 320
    const val ProgressMs = 600

    /** Décélération "emphasized" (Material 3) : entrée plus douce, sans à-coup final. */
    val EaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EaseIn = FastOutLinearInEasing
    val SpringSnappy = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)
    val SpringSoft = spring<Float>(dampingRatio = 0.88f, stiffness = 300f)

    fun tweenFast() = tween<Float>(FastMs, easing = EaseOut)
    fun tweenMedium() = tween<Float>(MediumMs, easing = EaseOut)
    fun tweenSlow() = tween<Float>(SlowMs, easing = EaseOut)
    fun tweenProgress() = tween<Float>(ProgressMs, easing = LinearEasing)

    fun <T> tweenMediumOf() = tween<T>(MediumMs, easing = EaseOut)
    fun <T> tweenFastOf() = tween<T>(FastMs, easing = EaseOut)

    // ── NavHost ─────────────────────────────────────────────────────────────

    fun navForwardEnter(): EnterTransition =
        slideInHorizontally(initialOffsetX = { it / 2 }, animationSpec = tweenMediumOf()) +
            fadeIn(tweenMediumOf())

    fun navForwardExit(): ExitTransition =
        slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tweenFastOf()) +
            fadeOut(tweenFastOf())

    fun navPopEnter(): EnterTransition =
        slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tweenMediumOf()) +
            fadeIn(tweenMediumOf())

    fun navPopExit(): ExitTransition =
        slideOutHorizontally(targetOffsetX = { it / 2 }, animationSpec = tweenMediumOf()) +
            fadeOut(tweenFastOf())

    fun fadeEnter(): EnterTransition = fadeIn(tweenMediumOf())
    fun fadeExit(): ExitTransition = fadeOut(tweenFastOf())

    fun playerEnter(): EnterTransition =
        slideInVertically(initialOffsetY = { it }, animationSpec = tweenMediumOf()) +
            fadeIn(tweenMediumOf())

    fun playerExit(): ExitTransition =
        slideOutVertically(targetOffsetY = { it }, animationSpec = tweenMediumOf()) +
            fadeOut(tweenFastOf())

    fun playerPopEnter(): EnterTransition = fadeIn(tweenFastOf())

    // ── Overlays (mini-player, queue, sheets) ───────────────────────────────

    fun slideUpEnter(): EnterTransition =
        slideInVertically(initialOffsetY = { it }, animationSpec = tweenMediumOf()) +
            fadeIn(tweenMediumOf())

    fun slideUpExit(): ExitTransition =
        slideOutVertically(targetOffsetY = { it }, animationSpec = tweenFastOf()) +
            fadeOut(tweenFastOf())

    fun queueEnter(): EnterTransition =
        slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tweenMediumOf()) +
            fadeIn(tweenFastOf())

    fun queueExit(): ExitTransition =
        slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tweenFastOf()) +
            fadeOut(tweenFastOf())

    // ── Tab / chip content ──────────────────────────────────────────────────

    fun tabContentEnter(): EnterTransition =
        fadeIn(tweenFastOf()) + scaleIn(initialScale = 0.98f, animationSpec = tweenFastOf())

    fun tabContentExit(): ExitTransition =
        fadeOut(tweenFastOf()) + scaleOut(targetScale = 0.98f, animationSpec = tweenFastOf())
}

/** Scale léger au press pour boutons play / chips. */
fun Modifier.sgPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = SgMotion.SpringSnappy,
        label = "sgPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Couleur de fond animée pour chips / onglets sélectionnés. */
@Composable
fun animateChipColors(
    selected: Boolean,
    accentColor: Color,
    unselectedBg: Color = SurfaceElevated.copy(alpha = 0.28f)
): Pair<Color, Color> {
    val bg by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.24f) else unselectedBg,
        animationSpec = SgMotion.tweenFastOf(),
        label = "chipBg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.42f) else BorderSubtle.copy(alpha = 0.35f),
        animationSpec = SgMotion.tweenFastOf(),
        label = "chipBorder"
    )
    return bg to border
}
