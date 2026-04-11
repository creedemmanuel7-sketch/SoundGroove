package com.credo.soundgroove.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Couleurs glass
val GlassSurface = Color(0x1AFFFFFF)        // blanc 10%
val GlassBorder = Color(0x33FFFFFF)          // blanc 20%
val GlassSurfaceDark = Color(0x0DFFFFFF)    // blanc 5%
val GlassHighlight = Color(0x26FFFFFF)       // blanc 15%

// Brush dégradé glass violet
val GlassBrushPurple = Brush.linearGradient(
    colors = listOf(
        Color(0x33BB86FC),  // violet clair 20%
        Color(0x1A6B3FA0),  // violet moyen 10%
        Color(0x0D1A0A2E),  // violet foncé 5%
    )
)

val GlassBrushDark = Brush.linearGradient(
    colors = listOf(
        Color(0x26FFFFFF),  // blanc 15%
        Color(0x0DFFFFFF),  // blanc 5%
    )
)

// Modificateur glass réutilisable
fun Modifier.glassEffect(
    cornerRadius: Dp = 16.dp,
    borderAlpha: Float = 0.2f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(GlassBrushPurple)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = borderAlpha),
                Color.White.copy(alpha = borderAlpha * 0.5f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

// Composant GlassCard réutilisable
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(GlassBrushPurple)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            ),
        content = content
    )
}