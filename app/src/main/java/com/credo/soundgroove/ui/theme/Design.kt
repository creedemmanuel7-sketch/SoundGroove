package com.credo.soundgroove.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SgSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Padding horizontal standard des écrans principaux. */
    val screenHorizontal = lg

    /** Marge haute sous la barre de statut (titres d'écran). */
    val screenTop = xl + sm

    /** Espacement vertical entre sections de liste. */
    val sectionGap = md

    /** Espacement entre items de liste. */
    val listItemGap = sm

    /** Padding interne des cartes / rows cliquables. */
    val cardPadding = md
}

object SgRadius {
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 28.dp
    val pill = 999.dp
}

@Composable
fun SgScreenBackground(
    appTheme: AppTheme,
    accent: AppAccent = AppAccent.VIOLET,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val accentColor = resolveAccentColor(appTheme, accent)
    val haloAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(SgMotion.SlowMs, easing = SgMotion.EaseOut),
        label = "haloAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeBackgroundBrush(appTheme))
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-60).dp, y = 80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(accentColor.copy(alpha = 0.18f * haloAlpha), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = 200.dp)
                .background(
                    Brush.radialGradient(
                        listOf(accentColor.copy(alpha = 0.10f * haloAlpha), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        content()
    }
}

@Composable
fun SgSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

@Composable
fun SgIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(SurfaceElevated.copy(alpha = 0.46f))
            .border(1.dp, accentColor.copy(alpha = 0.08f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun SgChip(
    text: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor) = animateChipColors(selected, accentColor)
    val textColor by animateColorAsState(
        targetValue = if (selected) accentColor else TextSecondary,
        animationSpec = SgMotion.tweenFastOf(),
        label = "chipText"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SgRadius.pill))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(SgRadius.pill))
            .sgPressScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

fun Modifier.albumArtShape(cornerRadius: Dp = SgRadius.md): Modifier =
    clip(RoundedCornerShape(cornerRadius))

/**
 * Zone tactile minimale (Fitts's Law — lawsofux.com/fittss-law : le temps pour
 * atteindre une cible dépend de sa taille et de la distance à parcourir).
 * Agrandit la surface cliquable à [minSize] sans changer la taille visuelle
 * du contenu, qui reste centré dans la zone.
 *
 * Utile pour les icônes de contrôle du player (suivant, shuffle, répéter…)
 * dont le glyphe est volontairement petit mais dont la cible tactile doit
 * respecter les ~48dp recommandés par Material 3.
 */
@Composable
fun SgTapTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minSize: Dp = 48.dp,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(minSize)
            .clickable(interactionSource = source, indication = indication) { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}
