package com.credo.soundgroove.ui.motion

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.model.KeyPath
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion

/**
 * Accent Lottie design-system : charge un JSON `res/raw`, respecte
 * [rememberSgReducedMotion] (dernière frame / progress figé, pas de loop).
 *
 * Compose [com.credo.soundgroove.ui.theme.SgMotion] reste la base ; Lottie est
 * réservé aux accents (égaliseur, pulse subtil).
 */
@Composable
fun SgLottie(
    @RawRes rawRes: Int,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
    isPlaying: Boolean = true,
    restartOnPlay: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    tint: Color? = null,
    /** Progress figé si reduced motion (0f = début, 1f = fin). */
    reducedMotionProgress: Float = 1f,
    alignment: Alignment = Alignment.Center,
    fallback: (@Composable () -> Unit)? = null,
) {
    val reducedMotion = rememberSgReducedMotion()
    val compositionResult = rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val composition = compositionResult.value

    if (composition == null) {
        if (fallback != null) {
            Box(modifier = modifier, contentAlignment = alignment) { fallback() }
        }
        return
    }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isPlaying && !reducedMotion,
        restartOnPlay = restartOnPlay,
        iterations = if (reducedMotion) 1 else iterations,
        speed = if (reducedMotion) 0f else 1f,
    )

    val displayProgress = if (reducedMotion) {
        reducedMotionProgress.coerceIn(0f, 1f)
    } else {
        progress
    }

    val tintArgb = tint?.toArgb()
    val dynamicProperties = if (tintArgb != null) {
        rememberLottieDynamicProperties(
            remember(tintArgb) {
                LottieDynamicProperty(
                    property = LottieProperty.COLOR_FILTER,
                    keyPath = KeyPath("**"),
                    value = SimpleColorFilter(tintArgb),
                )
            }
        )
    } else {
        null
    }

    LottieAnimation(
        composition = composition,
        progress = { displayProgress },
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        dynamicProperties = dynamicProperties,
    )
}

/**
 * Variante assets (`assets/<name>.json`) — utile si le JSON n’est pas en `res/raw`.
 */
@Composable
fun SgLottieAsset(
    assetFileName: String,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
    isPlaying: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    tint: Color? = null,
    reducedMotionProgress: Float = 1f,
    alignment: Alignment = Alignment.Center,
    fallback: (@Composable () -> Unit)? = null,
) {
    val reducedMotion = rememberSgReducedMotion()
    val compositionResult = rememberLottieComposition(LottieCompositionSpec.Asset(assetFileName))
    val composition = compositionResult.value

    if (composition == null) {
        if (fallback != null) {
            Box(modifier = modifier, contentAlignment = alignment) { fallback() }
        }
        return
    }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isPlaying && !reducedMotion,
        iterations = if (reducedMotion) 1 else iterations,
        speed = if (reducedMotion) 0f else 1f,
    )

    val displayProgress = if (reducedMotion) {
        reducedMotionProgress.coerceIn(0f, 1f)
    } else {
        progress
    }

    val tintArgb = tint?.toArgb()
    val dynamicProperties = if (tintArgb != null) {
        rememberLottieDynamicProperties(
            remember(tintArgb) {
                LottieDynamicProperty(
                    property = LottieProperty.COLOR_FILTER,
                    keyPath = KeyPath("**"),
                    value = SimpleColorFilter(tintArgb),
                )
            }
        )
    } else {
        null
    }

    LottieAnimation(
        composition = composition,
        progress = { displayProgress },
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        dynamicProperties = dynamicProperties,
    )
}
