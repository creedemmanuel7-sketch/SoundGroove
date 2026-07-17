package com.credo.soundgroove.ui.theme

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Clé sharedBounds grille album → hero détail (`Uri.encode` = route Nav). */
fun sgAlbumCoverSharedKey(albumName: String): String =
    "album_cover_${Uri.encode(albumName)}"

/** Clé sharedBounds liste artiste → avatar hero détail. */
fun sgArtistAvatarSharedKey(artistName: String): String =
    "artist_avatar_${Uri.encode(artistName)}"

/** Clé sharedBounds bouton play mini-player ↔ Player plein écran. */
fun sgPlayControlSharedKey(songId: Long): String = "play_control_$songId"

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

    /** Morph mini-player ↔ Player : plus court que SlowMs pour limiter la latence perçue. */
    const val PlayerMorphMs = 220

    /** Révélation circulaire lors d'un changement de thème. */
    const val ThemeRevealMs = 280

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

    val SpringSnappy = spring<Float>(dampingRatio = 0.78f, stiffness = 650f)
    val SpringSoft = spring<Float>(dampingRatio = 0.82f, stiffness = 480f)

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

    // Pas de slide vertical : le morph shared element (pochette) porte le mouvement ;
    // un slide NavHost en parallèle créait double motion + latence perçue.
    fun playerEnter(): EnterTransition = fadeIn(tweenFastOf())

    fun playerExit(): ExitTransition = fadeOut(tweenFastAccelOf())

    fun playerPopEnter(): EnterTransition = fadeIn(tweenFastOf())

    fun playerPopExit(): ExitTransition = ExitTransition.None

    private fun <T> tweenSlowOf() = tween<T>(SlowMs, easing = EmphasizedDecelerate)

    /** Scale initial pochette ≈ taille mini-player (44dp vs plein écran). */
    const val PlayerArtMiniScale = 0.14f

    /**
     * Offset Y (px) de repli pour l'entrée (Mini → Player, comportement inchangé,
     * jugé correct) et pour la sortie si la vraie position de la pochette n'a pas
     * encore été mesurée (cf. `artRestCenterPx` dans PlayerScreen — normalement
     * jamais utilisé en pratique, juste un filet de sécurité).
     */
    const val PlayerArtEnterOffsetY = 220f

    // ── Cible réelle de sortie (Player → Mini) ──────────────────────────────
    // La sortie doit amener la pochette à l'endroit exact qu'occupe la pochette
    // du mini-player (cf. MiniPlayer.kt), pas juste la faire rétrécir sur place :
    // sans ces valeurs, artScale/artOffsetY rétrécissaient la pochette autour de
    // son propre centre avec un décalage Y minime (220px) — visuellement, elle
    // "rétrécit au centre puis disparaît" au lieu de "descendre vers le bas".
    // Ces dp reproduisent la géométrie de MiniPlayer.kt : conteneur
    // `.padding(horizontal = SgSpacing.sm)` (8dp) + Row
    // `.padding(horizontal = SgSpacing.md)` (12dp) + moitié de la pochette 44dp.

    /** Distance du centre de la pochette mini-player par rapport au bord gauche. */
    val MiniArtCenterXDp = 42.dp // 8 (padding conteneur) + 12 (padding Row) + 22 (moitié de 44dp)

    /**
     * Distance du centre de la pochette mini-player par rapport au bas de la zone
     * de contenu (hors barre de navigation, ajoutée séparément via les vrais
     * insets système — cf. PlayerScreen). 8dp = `padding(bottom = 8.dp)` posé par
     * `AppNavigation` sur le mini-player ; 33dp ≈ moitié de sa hauteur totale
     * (barre de progression 2dp + padding vertical Row 2×8dp + pochette 44dp).
     */
    val MiniArtCenterFromBottomDp = 41.dp

    /** Anim du morph de la pochette à l'ouverture du Player (mini → plein). */
    fun playerArtEnterSpec(): AnimationSpec<Float> = tween(PlayerMorphMs, easing = EmphasizedDecelerate)

    /** Morph symétrique à la fermeture (plein → mini). */
    fun playerArtExitSpec(): AnimationSpec<Float> = tween(FastMs, easing = EmphasizedAccelerate)

    fun playerArtOffsetEnterSpec(): AnimationSpec<Float> = tween(PlayerMorphMs, easing = EmphasizedDecelerate)

    fun playerArtOffsetExitSpec(): AnimationSpec<Float> = tween(FastMs, easing = EmphasizedAccelerate)

    /** Chrome (titre, slider, contrôles) : en parallèle du morph, sans délai artificiel. */
    const val PlayerChromeDelayMs = 0

    /** Anim de fade du "chrome" en parallèle du morph pochette. */
    fun playerChromeEnterSpec(): AnimationSpec<Float> = tween(FastMs, easing = EmphasizedDecelerate)

    fun playerChromeExitSpec(): AnimationSpec<Float> = tween(FastMs, easing = EmphasizedAccelerate)

    fun themeRevealSpec(): AnimationSpec<Float> = tween(ThemeRevealMs, easing = Standard)

    // ── Contenu interactif de bottom sheet (Sheet Infos...) ─────────────────
    // Le `ModalBottomSheet` M3 gère déjà nativement le drag/cancel/spring-back
    // (son `SheetState` interne, basé sur un `AnchoredDraggableState`, règle
    // seul via `positionalThreshold`/`velocityThreshold` : relâcher avant le
    // seuil = retour à l'état ouvert, au-delà = fermeture — comportement geré
    // hors de notre contrôle, volontairement non ré-implémenté ici). Ce qui
    // manquait : une apparition du CONTENU plus douce qu'un "pop" instantané
    // dès que la sheet devient visible — cf. §1 "apparition micro-anim courte,
    // pas un pop brutal".

    /** Apparition contenu sheet : scale+fade court, découplé du slide natif. */
    fun sheetContentEnterSpec(): AnimationSpec<Float> = tween(MediumMs, easing = EmphasizedDecelerate)

    /** Échelle de départ du contenu (proche de 1 : effet subtil, pas un "zoom"). */
    const val SheetContentInitialScale = 0.94f

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

    // ── Paroles (Player ↔ Lyrics) ───────────────────────────────────────────
    // Écrans "pairs" plein écran : glissement horizontal (pas vertical, pour ne
    // pas se confondre avec l'ouverture/fermeture du Player lui-même) — cohérent
    // avec le swipe horizontal qui bascule entre les deux (cf. PlayerScreen /
    // LyricsScreen).

    fun lyricsEnter(): EnterTransition =
        slideInHorizontally(initialOffsetX = { it }, animationSpec = tweenSlowOf()) +
            fadeIn(tweenMediumOf())

    fun lyricsExit(): ExitTransition =
        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tweenMediumAccelOf()) +
            fadeOut(tweenFastAccelOf())

    // ── Tab / chip content ──────────────────────────────────────────────────

    fun tabContentEnter(): EnterTransition =
        fadeIn(tweenFastOf()) + scaleIn(initialScale = 0.98f, animationSpec = tweenFastOf())

    fun tabContentExit(): ExitTransition =
        fadeOut(tweenFastAccelOf()) + scaleOut(targetScale = 0.98f, animationSpec = tweenFastAccelOf())

    /** Transition directionnelle entre onglets navbar (fade + slide subtil). */
    fun tabNavTransition(fromTab: Int, toTab: Int): ContentTransform {
        val direction = when {
            toTab > fromTab -> 1
            toTab < fromTab -> -1
            else -> 0
        }
        val enter = fadeIn(tweenMediumOf()) +
            slideInHorizontally(
                initialOffsetX = { direction * it / 10 },
                animationSpec = tweenMediumOf()
            ) +
            scaleIn(initialScale = 0.985f, animationSpec = tweenMediumOf())
        val exit = fadeOut(tweenFastAccelOf()) +
            slideOutHorizontally(
                targetOffsetX = { -direction * it / 10 },
                animationSpec = tweenFastAccelOf()
            ) +
            scaleOut(targetScale = 0.985f, animationSpec = tweenFastAccelOf())
        return enter togetherWith exit
    }

    /**
     * Variante Mode perf / reduced motion : fade only (ou snap via durée 0),
     * sans slide ni scale — un seul porteur, le plus léger possible.
     */
    fun tabNavTransitionReduced(): ContentTransform =
        fadeIn(tween(0)) togetherWith fadeOut(tween(0))

    /** Nav forward/back snap (Mode perf) — état final immédiat. */
    fun navSnapEnter(): EnterTransition = EnterTransition.None
    fun navSnapExit(): ExitTransition = ExitTransition.None

    // ── Paroles (highlight) ─────────────────────────────────────────────────
    /** Scale ligne active — SpringSoft, une fois (pas de bounce répété). */
    const val LyricsActiveScale = 1.06f

    /** Halo accent derrière la ligne active. */
    const val LyricsHaloAlpha = 0.14f
}

/**
 * Flag prefs « Mode performance » (Profil / Paramètres). Fourni par
 * [AppNavigation] ; combiné en OR avec le réglage système dans
 * [rememberSgReducedMotion].
 */
val LocalSgPerformanceMode = staticCompositionLocalOf { false }

/**
 * Équivalent Android du réglage "Reduce Motion" des Apple HIG
 * (developer.apple.com/design/human-interface-guidelines → Motion : « make
 * motion optional », toujours respecter le réglage système). Sous Android il
 * n'existe pas de flag "reduce motion" dédié exposé à l'app ; le proxy standard
 * est l'échelle de durée d'animation système (`ANIMATOR_DURATION_SCALE`), mise
 * à 0 par l'utilisateur via Accessibilité → Suppression des animations, ou par
 * les options développeur ("Échelle des animations" → Désactivée).
 *
 * Aussi `true` si [LocalSgPerformanceMode] est actif (prefs Mode performance) —
 * checklist Perf du plan motion : même traitement snap / effets off.
 *
 * Usage : `if (rememberSgReducedMotion()) { /* état final immédiat */ } else { /* animer */ }`
 */
@Composable
fun rememberSgReducedMotion(): Boolean {
    val context = LocalContext.current
    val systemReduced = remember(context) {
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
    val performanceMode = LocalSgPerformanceMode.current
    return systemReduced || performanceMode
}

/**
 * Durée Coil crossfade : 0 en Mode perf / reduced motion, sinon [defaultMs]
 * plafonnée à [SgMotion.FastMs] (checklist Perf — pas de morph image long).
 */
@Composable
fun sgCoilCrossfadeMs(defaultMs: Int = SgMotion.FastMs): Int {
    val reduced = rememberSgReducedMotion()
    return if (reduced) 0 else defaultMs.coerceAtMost(SgMotion.FastMs)
}

/**
 * Scale + fade léger au press (boutons play / chips / contrôles queue).
 * Scale : [SgMotion.SpringSnappy] ; alpha : [SgMotion.FastMs] (tween) pour un
 * décalage de quelques ms vs le ressort — reduced motion / Mode perf : no-op.
 */
fun Modifier.sgPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f,
    pressedAlpha: Float = 0.72f,
): Modifier = composed {
    val reducedMotion = rememberSgReducedMotion()
    if (reducedMotion) return@composed this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = SgMotion.SpringSnappy,
        label = "sgPressScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) pressedAlpha else 1f,
        animationSpec = SgMotion.tweenFastOf(),
        label = "sgPressAlpha"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/** Couleur de fond animée pour chips / onglets sélectionnés. */
@Composable
fun animateChipColors(
    selected: Boolean,
    accentColor: Color,
    unselectedBg: Color? = null
): Pair<Color, Color> {
    val reducedMotion = rememberSgReducedMotion()
    val colorSpec = if (reducedMotion) snap() else SgMotion.tweenFastOf<Color>()
    val defaultUnselected = unselectedBg ?: SurfaceElevated.copy(alpha = 0.28f)
    val bg by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.24f) else defaultUnselected,
        animationSpec = colorSpec,
        label = "chipBg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.42f) else BorderSubtle.copy(alpha = 0.35f),
        animationSpec = colorSpec,
        label = "chipBorder"
    )
    return bg to border
}

// ── Shared element pochette (mini-player ↔ Player) ──────────────────────────
// cf. docs/FEATURES_C_SHARED_ELEMENT.md — implémentation réelle via
// SharedTransitionLayout (Compose Animation 1.7, disponible dans ce projet).
//
// Le mini-player existe à deux endroits (overlay AppNavigation + overlay interne
// MainScreen/HomeTab) et la pochette plein écran vit dans une destination NavHost.
// Plutôt que de faire remonter le SharedTransitionScope/AnimatedVisibilityScope par
// paramètre à travers toute la hiérarchie d'appel (MainScreen, LegacyMainHost...),
// on les expose via CompositionLocal — approche explicitement recommandée par la
// doc officielle quand plusieurs call sites doivent partager le même scope :
// https://developer.android.com/develop/ui/compose/animation/shared-elements

/**
 * Le [SharedTransitionScope] actif, fourni par `AppNavigation` qui enveloppe tout
 * son contenu (NavHost + overlays) dans un `SharedTransitionLayout`. `null` si
 * l'appelant est rendu hors de ce contexte (previews, tests...).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * L'[AnimatedVisibilityScope] du point d'entrée courant : soit l'`AnimatedContentScope`
 * de la destination "player" du NavHost, soit l'`AnimatedVisibility` qui héberge le
 * mini-player (overlay ou intégré à MainScreen). Requis par `Modifier.sharedElement`
 * pour savoir si l'élément entre ou sort de la composition.
 */
val LocalSgAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * `true` si un vrai shared element Compose est utilisable dans le contexte courant.
 * Permet à `PlayerScreen` de désactiver son morph manuel (scale + offset, cf.
 * `artScale`/`artOffsetY`) et de laisser `Modifier.sharedElement` piloter la
 * transition — sans exposer de type expérimental dans une signature publique.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun rememberSgSharedElementActive(): Boolean {
    // Mode perf / reduced motion : snap immédiat, pas de morph shared.
    if (rememberSgReducedMotion()) return false
    return LocalSharedTransitionScope.current != null &&
        LocalSgAnimatedVisibilityScope.current != null
}

/**
 * Applique un vrai `Modifier.sharedElement` (pochette mini-player ↔ Player plein
 * écran, morph de position + taille géré nativement par Compose) si le contexte
 * [LocalSharedTransitionScope]/[LocalSgAnimatedVisibilityScope] est disponible.
 *
 * Sinon ne fait rien : c'est le filet de sécurité qui permet à `MiniPlayer` et
 * `PlayerScreen` de rester utilisables hors SharedTransitionLayout (previews,
 * futurs call sites non encore raccordés) — dans ce cas, `PlayerScreen` retombe
 * sur son morph manuel scale+position existant (cf. `runEnterAnimation`).
 *
 * Mode perf / reduced motion : no-op (snap, pas de morph).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sgSharedAlbumArt(key: String): Modifier {
    if (rememberSgReducedMotion()) return this
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalSgAnimatedVisibilityScope.current ?: return this
    return with(sharedTransitionScope) {
        this@sgSharedAlbumArt.sharedElement(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}

/**
 * Variante `sharedBounds` (pas `sharedElement`) pour les éléments texte partagés
 * entre le mini-player et le Player plein écran (titre, artiste) : le CONTENU
 * diffère (taille de police, mise en page) entre les deux, seules les BORNES
 * (position + taille du conteneur) doivent être interpolées nativement — c'est
 * exactement la distinction que fait la doc officielle entre les deux API :
 * https://developer.android.com/develop/ui/compose/animation/shared-elements
 *
 * Même filet de sécurité que [sgSharedAlbumArt] : no-op hors contexte
 * `SharedTransitionLayout`/`AnimatedVisibilityScope`. Mode perf : no-op.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sgSharedBounds(key: String): Modifier {
    if (rememberSgReducedMotion()) return this
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalSgAnimatedVisibilityScope.current ?: return this
    return with(sharedTransitionScope) {
        this@sgSharedBounds.sharedBounds(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}
