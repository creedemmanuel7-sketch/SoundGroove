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
import androidx.compose.ui.platform.LocalContext

/**
 * Durées et courbes partagées pour une motion cohérente dans toute l'app,
 * alignées sur les tokens Material Design 3.
 *
 * Références :
 * - M3 Motion — how it works : https://m3.material.io/styles/motion/overview/how-it-works
 * - M3 Easing & duration : https://m3.material.io/styles/motion/easing-and-duration
 * - Android Compose animation quick guide : https://developer.android.com/develop/ui/compose/animation/quick-guide
 *
 * Principe M3 : les entrées utilisent une courbe "decelerate" (arrivée douce,
 * sans à-coup), les sorties une courbe "accelerate" (départ franc, l'élément
 * quitte vite l'attention de l'utilisateur). Les durées "emphasized" (utilisées
 * ici pour les transitions plein-écran comme le Player) sont légèrement plus
 * longues que les durées "standard" (utilisées pour micro-interactions : chips,
 * onglets) car elles portent plus de distance/surface à l'écran.
 */
object SgMotion {

    // ── Durées (tokens M3 : Short/Medium/Long, en ms) ──────────────────────
    // https://m3.material.io/styles/motion/easing-and-duration/tokens-specs

    /** M3 "Short3" (150ms) — micro-interactions : chips, presses, icônes. */
    const val FastMs = 150

    /** M3 "Medium1" (250ms) — transitions standards : onglets, sheets, nav. */
    const val MediumMs = 250

    /** M3 "Medium3" (350ms) — transitions "emphasized" : écrans pleine page. */
    const val SlowMs = 350

    /**
     * Durée de lissage de la barre de progression, calée sur l'intervalle de
     * polling du player (500ms) pour que l'anim rattrape la vraie position
     * sans jamais "traîner" derrière la valeur réelle.
     */
    const val ProgressMs = 500

    // ── Easing (tokens M3) ───────────────────────────────────────────────
    // Courbes officielles Material 3 (cubic-bezier), cf. easing-and-duration.

    /** M3 "Emphasized decelerate" : entrée avec appui marqué puis douceur. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** M3 "Emphasized accelerate" : sortie qui part vite, idéale pour un exit. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** M3 "Standard" : easing par défaut pour micro-interactions symétriques. */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** M3 "Standard decelerate" : variante plus douce pour petites entrées. */
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** M3 "Standard accelerate" : variante pour petites sorties. */
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Alias conservés pour compat : EaseOut = decelerate (entrée), EaseIn = accelerate (sortie). */
    val EaseOut = EmphasizedDecelerate
    val EaseIn = StandardAccelerate

    val SpringSnappy = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)
    val SpringSoft = spring<Float>(dampingRatio = 0.88f, stiffness = 300f)

    fun tweenFast() = tween<Float>(FastMs, easing = EmphasizedDecelerate)
    fun tweenMedium() = tween<Float>(MediumMs, easing = EmphasizedDecelerate)
    fun tweenSlow() = tween<Float>(SlowMs, easing = EmphasizedDecelerate)
    fun tweenProgress() = tween<Float>(ProgressMs, easing = LinearEasing)

    fun <T> tweenMediumOf() = tween<T>(MediumMs, easing = EmphasizedDecelerate)
    fun <T> tweenFastOf() = tween<T>(FastMs, easing = EmphasizedDecelerate)

    /** Variante "accelerate" pour les sorties (M3 : l'exit doit partir vite). */
    fun <T> tweenFastAccelOf() = tween<T>(FastMs, easing = EmphasizedAccelerate)
    fun <T> tweenMediumAccelOf() = tween<T>(MediumMs, easing = EmphasizedAccelerate)

    // ── NavHost ─────────────────────────────────────────────────────────────

    fun navForwardEnter(): EnterTransition =
        slideInHorizontally(initialOffsetX = { it / 2 }, animationSpec = tweenMediumOf()) +
            fadeIn(tweenMediumOf())

    fun navForwardExit(): ExitTransition =
        slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tweenFastAccelOf()) +
            fadeOut(tweenFastAccelOf())

    fun navPopEnter(): EnterTransition =
        slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tweenMediumOf()) +
            fadeIn(tweenMediumOf())

    fun navPopExit(): ExitTransition =
        slideOutHorizontally(targetOffsetX = { it / 2 }, animationSpec = tweenMediumAccelOf()) +
            fadeOut(tweenFastAccelOf())

    fun fadeEnter(): EnterTransition = fadeIn(tweenMediumOf())
    fun fadeExit(): ExitTransition = fadeOut(tweenFastAccelOf())

    // ── Player (mini-player → plein écran) ──────────────────────────────────
    // Inspiré de la transition "Now Playing" d'Apple Music (cf. Kodeco) : peu de
    // translation, l'essentiel du mouvement vient du morph interne de la pochette
    // (voir PlayerScreen : artEnterSpec/chromeEnterSpec) pour éviter l'effet
    // "feuille qui glisse" et donner une sensation de pochette qui "grandit".

    fun playerEnter(): EnterTransition =
        slideInVertically(initialOffsetY = { it / 6 }, animationSpec = tweenSlowOf()) +
            fadeIn(tweenMediumOf())

    fun playerExit(): ExitTransition =
        slideOutVertically(targetOffsetY = { it / 6 }, animationSpec = tweenMediumAccelOf()) +
            fadeOut(tweenFastAccelOf())

    fun playerPopEnter(): EnterTransition = fadeIn(tweenFastOf())

    private fun <T> tweenSlowOf() = tween<T>(SlowMs, easing = EmphasizedDecelerate)

    /** Anim du morph de la pochette à l'ouverture du Player (scale 0.9 → 1). */
    fun playerArtEnterSpec(): AnimationSpec<Float> = tween(SlowMs, easing = EmphasizedDecelerate)

    /** Délai avant le fade-in du "chrome" (titre, slider, contrôles) après la pochette. */
    const val PlayerChromeDelayMs = 60

    /** Anim de fade du "chrome" une fois la pochette lancée. */
    fun playerChromeEnterSpec(): AnimationSpec<Float> = tween(MediumMs, easing = EmphasizedDecelerate)

    // ── Overlays (mini-player, queue, sheets) ───────────────────────────────

    fun slideUpEnter(): EnterTransition =
        slideInVertically(initialOffsetY = { it }, animationSpec = tweenMediumOf()) +
            fadeIn(tweenMediumOf())

    fun slideUpExit(): ExitTransition =
        slideOutVertically(targetOffsetY = { it }, animationSpec = tweenFastAccelOf()) +
            fadeOut(tweenFastAccelOf())

    fun queueEnter(): EnterTransition =
        slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tweenMediumOf()) +
            fadeIn(tweenFastOf())

    fun queueExit(): ExitTransition =
        slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tweenFastAccelOf()) +
            fadeOut(tweenFastAccelOf())

    // ── Tab / chip content ──────────────────────────────────────────────────

    fun tabContentEnter(): EnterTransition =
        fadeIn(tweenFastOf()) + scaleIn(initialScale = 0.98f, animationSpec = tweenFastOf())

    fun tabContentExit(): ExitTransition =
        fadeOut(tweenFastAccelOf()) + scaleOut(targetScale = 0.98f, animationSpec = tweenFastAccelOf())
}

/**
 * Équivalent Android du réglage "Reduce Motion" des Apple HIG
 * (developer.apple.com/design/human-interface-guidelines → Motion : « make
 * motion optional », toujours respecter le réglage système). Sous Android il
 * n'existe pas de flag "reduce motion" dédié exposé à l'app ; le proxy standard
 * est l'échelle de durée d'animation système (`ANIMATOR_DURATION_SCALE`), mise
 * à 0 par l'utilisateur via Accessibilité → Suppression des animations, ou par
 * les options développeur ("Échelle des animations" → Désactivée).
 *
 * Usage : `if (rememberSgReducedMotion()) { /* état final immédiat */ } else { /* animer */ }`
 * Volontairement pas branché partout dans cette passe (risque de régression
 * hors du périmètre de cet agent) — voir docs/UX_MOTION_GUIDELINES.md.
 */
@Composable
fun rememberSgReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
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
    unselectedBg: Color? = null
): Pair<Color, Color> {
    val defaultUnselected = unselectedBg ?: SurfaceElevated.copy(alpha = 0.28f)
    val bg by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.24f) else defaultUnselected,
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
