package com.credo.soundgroove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.ui.theme.AppTheme
import com.credo.soundgroove.ui.theme.GlassBorder
import com.credo.soundgroove.ui.theme.GlassSurface
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.TextSecondary
import com.credo.soundgroove.ui.theme.sgThemeRevealOrigin
import com.credo.soundgroove.ui.theme.themePreviewColor

/**
 * Sélecteur de thème 3-up (Profil, Paramètres, onboarding).
 * Même layout partout pour unifier l'apparence.
 */
@Composable
fun ThemePicker(
    currentTheme: AppTheme,
    selectedRingColor: Color,
    onThemeClick: (AppTheme, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SgSpacing.sm)
    ) {
        AppTheme.entries.forEach { theme ->
            ThemePickerCell(
                label = themeShortLabel(theme),
                previewColor = themePreviewColor(theme),
                selected = currentTheme == theme,
                selectedRing = selectedRingColor,
                modifier = Modifier.weight(1f),
                onClick = { origin -> onThemeClick(theme, origin) }
            )
        }
    }
}

@Composable
private fun ThemePickerCell(
    label: String,
    previewColor: Color,
    selected: Boolean,
    selectedRing: Color,
    modifier: Modifier = Modifier,
    onClick: (Offset) -> Unit
) {
    var revealOrigin by remember { mutableStateOf(Offset.Zero) }
    val bg = if (selected) selectedRing.copy(alpha = 0.14f) else GlassSurface.copy(alpha = 0.32f)
    val borderColor = if (selected) selectedRing.copy(alpha = 0.5f) else GlassBorder.copy(alpha = 0.3f)
    Column(
        modifier = modifier
            .sgThemeRevealOrigin { revealOrigin = it }
            .clip(RoundedCornerShape(SgRadius.md))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(SgRadius.md))
            .clickable { onClick(revealOrigin) }
            .padding(vertical = 10.dp, horizontal = SgSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(previewColor, CircleShape)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun themeShortLabel(theme: AppTheme): String = when (theme) {
    AppTheme.NOIR_ABSOLU -> "Noir"
    AppTheme.ARGENT_CLAIR -> "Clair"
    AppTheme.GRAPHITE -> "Graphite"
}

fun themeFullLabel(theme: AppTheme): String = when (theme) {
    AppTheme.NOIR_ABSOLU -> "Noir Absolu"
    AppTheme.ARGENT_CLAIR -> "Clair Argent"
    AppTheme.GRAPHITE -> "Graphite"
}
