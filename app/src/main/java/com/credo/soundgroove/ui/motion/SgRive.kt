package com.credo.soundgroove.ui.motion

import android.view.ViewGroup
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion

/**
 * Accent Rive design-system (API View via [AndroidView] — stable, minSdk 21+).
 *
 * - Si le raw `.riv` est absent / illisible → [fallback] (jamais de crash).
 * - Reduced motion / Mode perf → pause, pas de loop spam.
 *
 * Asset produit optionnel : `res/raw/sg_pulse.riv` via [SgRiveAccent] /
 * `tools/generate_sg_pulse_riv.mjs`. Voir `docs/MOTION.md`.
 */
@Composable
fun SgRive(
    @RawRes rawRes: Int,
    modifier: Modifier = Modifier,
    artboardName: String? = null,
    stateMachineName: String? = null,
    animationName: String? = null,
    autoplay: Boolean = true,
    loop: Loop = Loop.LOOP,
    fit: Fit = Fit.CONTAIN,
    alignment: Alignment = Alignment.Center,
    fallback: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val reducedMotion = rememberSgReducedMotion()
    val resourceExists = remember(rawRes) {
        try {
            context.resources.openRawResource(rawRes).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    if (!resourceExists) {
        if (fallback != null) {
            Box(modifier = modifier, contentAlignment = alignment) { fallback() }
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            RiveAnimationView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                try {
                    setRiveResource(
                        resId = rawRes,
                        artboardName = artboardName,
                        animationName = animationName,
                        stateMachineName = stateMachineName,
                        autoplay = autoplay && !reducedMotion,
                        fit = fit,
                        loop = if (reducedMotion) Loop.ONESHOT else loop,
                    )
                    if (reducedMotion) pause()
                } catch (_: Exception) {
                    // Laisser une vue vide ; le caller peut préférer un raw valide.
                }
            }
        },
        update = { view ->
            try {
                if (reducedMotion || !autoplay) {
                    view.pause()
                } else {
                    view.play()
                }
            } catch (_: Exception) {
                // Ignore — asset / worker indisponible.
            }
        },
        onRelease = { view ->
            try {
                view.pause()
            } catch (_: Exception) {
                // Ignore — déjà disposé.
            }
        }
    )
}
