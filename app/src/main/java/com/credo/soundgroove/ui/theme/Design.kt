package com.credo.soundgroove.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SgSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    /** @deprecated Préférer [xxl] (24) — conservé pour migrations douces. */
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Padding horizontal / gutter standard des écrans principaux. */
    val screenHorizontal = lg
    val gutter = lg

    /** Marge haute sous la barre de statut (titres d'écran). */
    val screenTop = xxl

    /** Espacement vertical entre sections de liste. */
    val sectionGap = md

    /** Espacement entre items de liste. */
    val listItemGap = sm

    /** Padding interne des cartes / rows cliquables. */
    val cardPadding = md

    /**
     * Inset bas pour listes scrollables : mini-player (~64) + nav (~64) + marges.
     * Évite que le contenu soit masqué sous le chrome bas.
     */
    val contentInsetBottom = 152.dp

    val hitTarget = 48.dp
    val iconSize = 24.dp
    val chipHeight = 40.dp
    val buttonHeight = 48.dp
    val listRowHeight = 56.dp
    val listRowTall = 64.dp
    val miniPlayerHeight = 64.dp
    val miniPlayerArt = 40.dp
    val navHeight = 64.dp
    val seekTrack = 4.dp
    val seekThumb = 16.dp
    /** Zone tactile verticale du seek Player (au-delà du thumb 16). */
    val seekHitSlop = 44.dp
    val progressThumb = 12.dp

    /** Thumb Switch exact (Material 3 ne fixe pas 28dp). */
    val switchThumb = 28.dp
    val switchTrackHeight = 32.dp
    val switchTrackWidth = 52.dp

    /** Fade latéral des LazyRow chips (Bibliothèque / Recherche). */
    val chipEdgeFade = 24.dp
}

object SgRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val full = 999.dp
    /** Alias historique — même valeur que [full]. */
    val pill = full
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
            .size(40.dp)
            .clip(CircleShape)
            .background(SurfaceElevated)
            .border(1.dp, TextPrimary.copy(alpha = 0.08f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

enum class SgChipVariant { Active, Inactive, Utility }

@Composable
fun SgChip(
    text: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SgChipVariant = if (selected) SgChipVariant.Active else SgChipVariant.Inactive,
) {
    val bgColor = when (variant) {
        SgChipVariant.Active -> accentColor.copy(alpha = 0.20f)
        SgChipVariant.Inactive -> SurfaceElevated.copy(alpha = 0.55f)
        SgChipVariant.Utility -> SurfaceElevated
    }
    val borderColor = when (variant) {
        SgChipVariant.Active -> accentColor.copy(alpha = 0.45f)
        SgChipVariant.Inactive -> TextPrimary.copy(alpha = 0.08f)
        SgChipVariant.Utility -> TextPrimary.copy(alpha = 0.10f)
    }
    val textColor = when (variant) {
        SgChipVariant.Active -> accentColor
        SgChipVariant.Inactive -> TextSecondary
        SgChipVariant.Utility -> TextSecondary
    }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .heightIn(min = SgSpacing.chipHeight)
            .clip(RoundedCornerShape(SgRadius.pill))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(SgRadius.pill))
            .sgPressScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = SgSpacing.md, vertical = SgSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (variant == SgChipVariant.Active) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

fun Modifier.albumArtShape(cornerRadius: Dp = SgRadius.md): Modifier =
    clip(RoundedCornerShape(cornerRadius))

/**
 * Fade latéral ~24dp start/end pour LazyRow chips, afin d'adoucir la coupe
 * et laisser percevoir le chip suivant (peek).
 */
fun Modifier.sgChipRowEdgeFade(fadeWidth: Dp = SgSpacing.chipEdgeFade): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadePx = fadeWidth.toPx().coerceAtMost(size.width / 2f)
            if (fadePx <= 0f || size.width <= 0f) return@drawWithContent
            val startStop = (fadePx / size.width).coerceIn(0f, 0.5f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        startStop to Color.Black,
                        (1f - startStop) to Color.Black,
                        1f to Color.Transparent
                    )
                ),
                blendMode = BlendMode.DstIn
            )
        }

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
