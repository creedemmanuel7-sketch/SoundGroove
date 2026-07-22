package com.credo.soundgroove.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SgEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    compact: Boolean = false,
    actionLabel: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (compact) Modifier.padding(vertical = SgSpacing.lg)
                else Modifier.fillMaxHeight().padding(horizontal = SgSpacing.lg)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (compact) Arrangement.Top else Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 64.dp else 72.dp)
                .background(GlassSurface, CircleShape)
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(if (compact) 28.dp else 32.dp)
                )
                iconPainter != null -> Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(if (compact) 28.dp else 32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(SgSpacing.md))
        Text(
            text = title,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SgSpacing.xs))
        Text(
            text = subtitle,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.88f)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(SgSpacing.md))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SgRadius.md))
                    .background(accentColor)
                    .clickable { onAction() }
                    .padding(horizontal = SgSpacing.lg, vertical = SgSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(actionLabel, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val LibraryScanMessages = listOf(
    "Analyse de votre bibliothèque musicale",
    "Indexation des titres et artistes",
    "Organisation de vos albums",
    "Préparation de votre espace d'écoute",
)

/**
 * Chargement générique — expérience waveform/pulse (plus de spinner brutal).
 * Pour le premier scan bibliothèque, préférer [LibraryScanLoading].
 */
@Composable
fun SgLoadingState(
    message: String = "Chargement de votre musique…",
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    LibraryScanLoading(
        accentColor = accentColor,
        modifier = modifier,
        messages = listOf(message),
        showLogo = false,
    )
}

/**
 * Écran d'analyse bibliothèque : pulse + waveform, messages FR rotatifs,
 * transition douce. Reduced motion : barres figées, message fixe.
 */
@Composable
fun LibraryScanLoading(
    accentColor: Color,
    modifier: Modifier = Modifier,
    messages: List<String> = LibraryScanMessages,
    showLogo: Boolean = true,
) {
    val reducedMotion = rememberSgReducedMotion()
    var messageIndex by remember { mutableIntStateOf(0) }
    val safeMessages = messages.ifEmpty { LibraryScanMessages }

    LaunchedEffect(reducedMotion, safeMessages) {
        if (reducedMotion || safeMessages.size <= 1) return@LaunchedEffect
        while (true) {
            delay(2_400)
            messageIndex = (messageIndex + 1) % safeMessages.size
        }
    }

    val infinite = rememberInfiniteTransition(label = "libraryScanPulse")
    val animatedHalo by infinite.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(SgMotion.SlowMs * 3, easing = SgMotion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanHalo"
    )
    val animatedProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_200, easing = SgMotion.Standard),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanProgress"
    )
    val haloAlpha = if (reducedMotion) 0.22f else animatedHalo
    val progressShift = if (reducedMotion) 0f else animatedProgress

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SgSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        alpha = if (reducedMotion) 0.22f else haloAlpha
                    }
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accentColor.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )
            if (showLogo) {
                SoundGrooveLogo(size = 96.dp, markColor = BrandPurple)
            }
        }

        Spacer(modifier = Modifier.height(SgSpacing.xl))

        LibraryScanWaveform(
            accentColor = accentColor,
            reducedMotion = reducedMotion,
            modifier = Modifier.height(36.dp)
        )

        Spacer(modifier = Modifier.height(SgSpacing.lg))

        AnimatedContent(
            targetState = if (reducedMotion) 0 else messageIndex,
            transitionSpec = {
                fadeIn(SgMotion.tweenMediumOf()) togetherWith fadeOut(SgMotion.tweenFastAccelOf())
            },
            label = "libraryScanMessage"
        ) { index ->
            Text(
                text = safeMessages[index.coerceIn(0, safeMessages.lastIndex)],
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f)
            )
        }

        Spacer(modifier = Modifier.height(SgSpacing.sm))

        Text(
            text = "Cela ne prendra qu'un instant",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(SgSpacing.lg))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .height(3.dp)
                .clip(RoundedCornerShape(SgRadius.full))
                .background(accentColor.copy(alpha = 0.12f))
        ) {
            if (reducedMotion) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.45f)
                        .align(Alignment.CenterStart)
                        .background(accentColor.copy(alpha = 0.55f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.38f)
                        .graphicsLayer {
                            translationX = progressShift * size.width * 1.8f - size.width * 0.4f
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    accentColor.copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun LibraryScanWaveform(
    accentColor: Color,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val barCount = 7
    val staticFractions = listOf(0.35f, 0.55f, 0.78f, 1f, 0.72f, 0.48f, 0.32f)
    val infinite = rememberInfiniteTransition(label = "scanWave")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val animated by infinite.animateFloat(
                initialValue = 0.28f + (index % 3) * 0.08f,
                targetValue = 0.95f - (index % 4) * 0.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = SgMotion.SlowMs * 2 + index * 70,
                        easing = SgMotion.Standard
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scanBar$index"
            )
            val fraction = if (reducedMotion) staticFractions[index] else animated
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(SgRadius.full))
                    .background(accentColor.copy(alpha = 0.55f + fraction * 0.4f))
            )
        }
    }
}
