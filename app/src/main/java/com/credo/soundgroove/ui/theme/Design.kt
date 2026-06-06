package com.credo.soundgroove.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val accent = accentColorForTheme(appTheme)
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
                        listOf(accent.copy(alpha = 0.18f), Color.Transparent)
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
                        listOf(accent.copy(alpha = 0.10f), Color.Transparent)
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SgRadius.pill))
            .background(
                if (selected) accentColor.copy(alpha = 0.24f) else SurfaceElevated.copy(alpha = 0.28f)
            )
            .border(
                width = 1.dp,
                color = if (selected) accentColor.copy(alpha = 0.42f) else BorderSubtle.copy(alpha = 0.35f),
                shape = RoundedCornerShape(SgRadius.pill)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accentColor else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

fun Modifier.albumArtShape(cornerRadius: Dp = SgRadius.md): Modifier =
    clip(RoundedCornerShape(cornerRadius))
