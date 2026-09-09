package com.credo.soundgroove.ui.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop

/**
 * Accent Rive optionnel résolu par nom de raw (`res/raw/<name>.riv`).
 *
 * Si le fichier est absent → [fallback] (souvent rien). Permet d’embarquer
 * `sg_pulse.riv` plus tard sans casser le build : déposer le fichier dans
 * `app/src/main/res/raw/` puis rebuild. Voir `docs/MOTION.md` et
 * `tools/generate_sg_pulse_riv.mjs`.
 */
@Composable
fun SgRiveAccent(
    rawName: String = "sg_pulse",
    modifier: Modifier = Modifier,
    artboardName: String? = "Pulse",
    animationName: String? = "pulse",
    autoplay: Boolean = true,
    loop: Loop = Loop.LOOP,
    fit: Fit = Fit.CONTAIN,
    alignment: Alignment = Alignment.Center,
    fallback: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val resId = remember(rawName) {
        context.resources.getIdentifier(rawName, "raw", context.packageName)
    }
    if (resId == 0) {
        if (fallback != null) {
            Box(modifier = modifier, contentAlignment = alignment) { fallback() }
        }
        return
    }
    SgRive(
        rawRes = resId,
        modifier = modifier,
        artboardName = artboardName,
        animationName = animationName,
        autoplay = autoplay,
        loop = loop,
        fit = fit,
        alignment = alignment,
        fallback = fallback,
    )
}
