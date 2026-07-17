package com.credo.soundgroove.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.ui.theme.SgMotion
import com.credo.soundgroove.ui.theme.rememberSgReducedMotion

/**
 * Badge "en lecture" — remplace un simple titre coloré par un indicateur beaucoup
 * plus visible (demande explicite : « pas trop discret ») : petites barres
 * d'égaliseur animées sur fond pilule teinté à l'accent. Utilisé dans toutes les
 * listes de chansons (Bibliothèque, File d'attente...) à côté du morceau en cours.
 *
 * `isPlaying = false` (piste en cours mais en pause) gèle les barres à mi-hauteur
 * plutôt que de les arrêter net — cohérent avec la rotation du vinyle dans
 * PlayerScreen ("pause = stop", pas de reset).
 */
@Composable
fun NowPlayingBadge(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(accentColor.copy(alpha = 0.16f))
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NowPlayingBars(isPlaying = isPlaying, accentColor = accentColor)
    }
}

/** Les barres seules (sans le fond pilule) — pour les emplacements déjà "badgés" (ex. pochette de la file d'attente). */
@Composable
fun NowPlayingBars(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    barHeight: androidx.compose.ui.unit.Dp = 12.dp
) {
    val reducedMotion = rememberSgReducedMotion()
    Box(
        modifier = modifier.height(barHeight),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(3) { index ->
                val infiniteTransition = rememberInfiniteTransition(label = "nowPlayingBar$index")
                val animatedFraction by infiniteTransition.animateFloat(
                    initialValue = 0.28f + index * 0.08f,
                    targetValue = 1f - index * 0.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(SgMotion.SlowMs, easing = SgMotion.Standard),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "nowPlayingBarHeight$index"
                )
                val heightFraction = when {
                    reducedMotion -> 0.42f + index * 0.12f
                    isPlaying -> animatedFraction
                    else -> 0.42f
                }
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(barHeight * heightFraction)
                        .background(accentColor, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

/**
 * Bouton flottant « Revenir à la piste en cours » — apparaît quand l'utilisateur a
 * scrollé loin de l'item en lecture dans une LazyColumn (cf. demande §A). Style
 * cohérent avec les autres FAB circulaires de l'app (dégradé radial accent).
 */
@Composable
fun ScrollToCurrentFab(
    visible: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(SgMotion.tweenFastOf()) +
            scaleIn(initialScale = 0.8f, animationSpec = SgMotion.SpringSnappy),
        exit = fadeOut(SgMotion.tweenFastAccelOf()) +
            scaleOut(targetScale = 0.8f, animationSpec = SgMotion.tweenFastAccelOf()),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(6.dp, CircleShape, spotColor = accentColor.copy(alpha = 0.4f))
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(accentColor, accentColor.copy(alpha = 0.75f))))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.GpsFixed,
                contentDescription = "Revenir à la piste en cours",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
