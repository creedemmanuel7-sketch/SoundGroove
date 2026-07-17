package com.credo.soundgroove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.R
import com.credo.soundgroove.ui.theme.GlassSurface
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.SurfaceElevated
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.TextSecondary
import com.credo.soundgroove.ui.theme.themeSecondaryAccent

/**
 * Barre dual CTA unique (Lecture / Aléatoire) — height 48, radius-lg.
 * Primary = fill accent ; secondary = surface-2 + [onSurfaceColor] (contraste AA).
 */
@Composable
fun DualCtaBar(
    accentColor: Color,
    enabled: Boolean = true,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    playLabel: String = "Lecture",
    shuffleLabel: String = "Aléatoire",
    /** Texte/icône du CTA Aléatoire — défaut TextPrimary ; passer palette.onSurface si accent pochette. */
    onSurfaceColor: Color = TextPrimary,
) {
    val shape = RoundedCornerShape(SgRadius.lg)
    val shuffleFg = if (enabled) onSurfaceColor else TextSecondary
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SgSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(SgSpacing.buttonHeight)
                .clip(shape)
                .background(
                    if (enabled) {
                        Brush.horizontalGradient(
                            listOf(accentColor, themeSecondaryAccent(accentColor))
                        )
                    } else {
                        Brush.horizontalGradient(listOf(GlassSurface, GlassSurface))
                    }
                )
                .clickable(enabled = enabled, onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = if (enabled) Color.White else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    playLabel,
                    color = if (enabled) Color.White else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(SgSpacing.buttonHeight)
                .clip(shape)
                .background(SurfaceElevated)
                .border(1.dp, shuffleFg.copy(alpha = 0.18f), shape)
                .clickable(enabled = enabled, onClick = onShuffle),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = null,
                    tint = shuffleFg,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    shuffleLabel,
                    color = shuffleFg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
