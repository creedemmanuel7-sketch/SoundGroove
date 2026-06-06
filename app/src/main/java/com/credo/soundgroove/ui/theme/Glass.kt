package com.credo.soundgroove.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val GlassSurface = Color(0x14FFFFFF)
val GlassBorder = Color(0x28FFFFFF)
val GlassSurfaceDark = Color(0x0AFFFFFF)
val GlassHighlight = Color(0x1FFFFFFF)

fun surfaceBrush(accentColor: Color = LightPurple): Brush = Brush.linearGradient(
    colors = listOf(
        SurfaceElevated.copy(alpha = 0.95f),
        SurfaceOverlay.copy(alpha = 0.88f),
        accentColor.copy(alpha = 0.06f)
    )
)

fun Modifier.glassEffect(
    cornerRadius: Dp = SgRadius.lg,
    accentColor: Color = LightPurple,
    borderAlpha: Float = 0.18f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(surfaceBrush(accentColor))
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = borderAlpha),
                accentColor.copy(alpha = borderAlpha * 0.4f),
                Color.White.copy(alpha = borderAlpha * 0.15f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = SgRadius.lg,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = accentColor.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(surfaceBrush(accentColor))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        accentColor.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            ),
        content = content
    )
}

@Composable
fun SgBottomSheetContainer(
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        SurfaceOverlay.copy(alpha = 0.98f),
                        DeepPurple.copy(alpha = 0.99f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = 0.2f), Color.Transparent)
                ),
                shape = RoundedCornerShape(topStart = SgRadius.xl, topEnd = SgRadius.xl)
            ),
        content = content
    )
}
