package com.credo.soundgroove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.ui.theme.*

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
            textAlign = TextAlign.Center
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

@Composable
fun SgLoadingState(
    message: String = "Chargement de votre musique…",
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SgSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = accentColor,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(SgSpacing.md))
        Text(
            text = message,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}
