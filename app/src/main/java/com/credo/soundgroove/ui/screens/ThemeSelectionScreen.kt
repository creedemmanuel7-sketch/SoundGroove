package com.credo.soundgroove.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.ui.components.SoundGrooveLogo
import com.credo.soundgroove.ui.theme.*

@Composable
fun ThemeSelectionScreen(
    onThemeSelected: (AppTheme) -> Unit
) {
    var selectedTheme by remember { mutableStateOf(AppTheme.NOIR_ABSOLU) }
    val activeColor = accentColorForTheme(selectedTheme)

    SgScreenBackground(appTheme = selectedTheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SgSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SgSpacing.xxxl)
            ) {
                SoundGrooveLogo(accentColor = activeColor)
                Spacer(modifier = Modifier.height(SgSpacing.xl))
                Text(
                    text = "SoundGroove",
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(SgSpacing.sm))
                Text(
                    text = "Choisis ton ambiance",
                    style = MaterialTheme.typography.headlineSmall,
                    color = activeColor
                )
                Spacer(modifier = Modifier.height(SgSpacing.md))
                Text(
                    text = "Sélectionne ton style visuel. Tu pourras le modifier plus tard dans les paramètres.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = SgSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(SgSpacing.lg, Alignment.CenterVertically)
            ) {
                ThemeCard(
                    title = "Noir Absolu",
                    description = "Noir profond, accent cyan — identité de l'icône.",
                    accentColor = BrandCyan,
                    bgColor = AbsoluteBlackSurface,
                    isSelected = selectedTheme == AppTheme.NOIR_ABSOLU,
                    onClick = { selectedTheme = AppTheme.NOIR_ABSOLU }
                )
                ThemeCard(
                    title = "Clair Argent",
                    description = "Fond blanc argenté, texte sombre, accent cyan doux.",
                    accentColor = ArgentClairAccent,
                    bgColor = ArgentClairSurface,
                    isSelected = selectedTheme == AppTheme.ARGENT_CLAIR,
                    onClick = { selectedTheme = AppTheme.ARGENT_CLAIR }
                )
                ThemeCard(
                    title = "Graphite",
                    description = "Graphite mat et technique, accent argent/platine.",
                    accentColor = SilverAccent,
                    bgColor = GraphiteCard,
                    isSelected = selectedTheme == AppTheme.GRAPHITE,
                    onClick = { selectedTheme = AppTheme.GRAPHITE }
                )
            }

            Button(
                onClick = { onThemeSelected(selectedTheme) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = SgSpacing.lg),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor,
                    contentColor = if (selectedTheme == AppTheme.ARGENT_CLAIR) TextPrimary else Color.White
                ),
                shape = RoundedCornerShape(SgRadius.pill),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    text = "Commencer l'expérience",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ThemeCard(
    title: String,
    description: String,
    accentColor: Color,
    bgColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderAlpha by animateFloatAsState(if (isSelected) 0.7f else 0.12f, label = "borderAlpha")

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = SgRadius.lg,
        accentColor = accentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) accentColor.copy(alpha = 0.08f) else Color.Transparent
                )
                .padding(SgSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(SgRadius.md))
                    .background(bgColor)
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(SgRadius.md)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(TextPrimary.copy(alpha = if (IsLightTheme) 0.10f else 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .fillMaxHeight()
                                .background(accentColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(SgSpacing.lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Sélectionné",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                val ringColor = if (IsLightTheme) Color.Black else Color.White
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(1.5.dp, ringColor.copy(alpha = borderAlpha), CircleShape)
                )
            }
        }
    }
}
