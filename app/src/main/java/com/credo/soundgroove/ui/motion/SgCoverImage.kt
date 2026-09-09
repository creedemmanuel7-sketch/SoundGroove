package com.credo.soundgroove.ui.motion

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion
import com.credo.soundgroove.ui.theme.sgCoilCrossfadeMs

/**
 * Pochette / cover Coil avec double adoucissement :
 * 1. [Crossfade] Compose sur changement d’URI (évite le hard-cut au skip piste)
 * 2. Coil [crossfade] court aligné [SgMotion] / reduced motion
 *
 * Compatible sous un [Modifier] shared element : on adoucit le bitmap sous le morph,
 * sans combattre les shared bounds.
 *
 * @param decodeWidth / [decodeHeight] optionnels pour sous-échantillonner (listes / fond flouté).
 * @param decodeEdgeDp alternative pratique : côté en dp → px via [LocalDensity] (thumbs).
 */
@Composable
fun SgCoverImage(
    albumArtUri: Uri?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    /** Durée Crossfade URI (plafonnée côté Coil à FastMs via [sgCoilCrossfadeMs]). */
    uriCrossfade: Boolean = true,
    decodeWidth: Int? = null,
    decodeHeight: Int? = null,
    decodeEdgeDp: Dp? = null,
) {
    val reducedMotion = rememberSgReducedMotion()
    val coilMs = sgCoilCrossfadeMs(SgMotion.FastMs)
    val context = LocalContext.current
    val density = LocalDensity.current

    val resolvedWidth = remember(decodeWidth, decodeEdgeDp, density) {
        decodeWidth ?: decodeEdgeDp?.let { with(density) { it.roundToPx() } }
    }
    val resolvedHeight = remember(decodeHeight, decodeEdgeDp, density) {
        decodeHeight ?: decodeEdgeDp?.let { with(density) { it.roundToPx() } }
    }

    fun buildRequest(uri: Uri): ImageRequest {
        val builder = ImageRequest.Builder(context)
            .data(uri)
            .crossfade(coilMs)
            .precision(Precision.INEXACT)
            .allowRgb565(true)
        val w = resolvedWidth
        val h = resolvedHeight
        if (w != null && h != null && w > 0 && h > 0) {
            builder.size(w, h)
        }
        return builder.build()
    }

    if (!uriCrossfade || reducedMotion) {
        if (albumArtUri != null) {
            AsyncImage(
                model = buildRequest(albumArtUri),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
        return
    }

    Crossfade(
        targetState = albumArtUri,
        animationSpec = SgMotion.tweenMediumOf(),
        label = "sgCoverUri",
        modifier = modifier
    ) { uri ->
        if (uri != null) {
            AsyncImage(
                model = buildRequest(uri),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
