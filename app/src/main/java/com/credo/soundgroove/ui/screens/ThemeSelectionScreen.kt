package com.credo.soundgroove.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.ui.theme.*

@Composable
fun ThemeSelectionScreen(
    onThemeSelected: (AppTheme) -> Unit
) {
    var selectedTheme by remember { mutableStateOf(AppTheme.CLASSIC_DARK) }

    // Dégradé de fond qui s'adapte légèrement selon le thème sélectionné pour un effet super immersif
    val backgroundBrush = remember(selectedTheme) {
        when (selectedTheme) {
            AppTheme.CLASSIC_DARK -> Brush.verticalGradient(
                listOf(Color(0xFF0A0F0D), Color(0xFF000000))
            )
            AppTheme.ORIGINAL_PURPLE -> Brush.verticalGradient(
                listOf(Color(0xFF140B22), Color(0xFF09040F))
            )
            AppTheme.CORAL_VIBRANT -> Brush.verticalGradient(
                listOf(Color(0xFF220B0F), Color(0xFF130406))
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Section En-tête
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp)
            ) {
                Text(
                    text = "SoundGroove",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.5.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Choisis ton style",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (selectedTheme) {
                        AppTheme.CLASSIC_DARK -> ClassicAccent
                        AppTheme.ORIGINAL_PURPLE -> LightPurple
                        AppTheme.CORAL_VIBRANT -> CoralAccent
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Sélectionne ton ambiance de départ. Tu pourras la modifier plus tard dans les paramètres.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    lineHeight = 20.sp
                )
            }

            // Liste des Thèmes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                ThemeCard(
                    theme = AppTheme.CLASSIC_DARK,
                    title = "Vert Émeraude",
                    description = "Le style classique des applications de musique, sombre et épuré.",
                    accentColor = ClassicAccent,
                    bgColor = ClassicSurface,
                    isSelected = selectedTheme == AppTheme.CLASSIC_DARK,
                    onClick = { selectedTheme = AppTheme.CLASSIC_DARK }
                )

                ThemeCard(
                    theme = AppTheme.ORIGINAL_PURPLE,
                    title = "Violet Original",
                    description = "L'esprit SoundGroove original avec ses teintes de violet profond.",
                    accentColor = LightPurple,
                    bgColor = DarkPurple,
                    isSelected = selectedTheme == AppTheme.ORIGINAL_PURPLE,
                    onClick = { selectedTheme = AppTheme.ORIGINAL_PURPLE }
                )

                ThemeCard(
                    theme = AppTheme.CORAL_VIBRANT,
                    title = "Corail Vibrant",
                    description = "Une ambiance chaleureuse, dynamique et pleine d'énergie.",
                    accentColor = CoralAccent,
                    bgColor = CoralSurface,
                    isSelected = selectedTheme == AppTheme.CORAL_VIBRANT,
                    onClick = { selectedTheme = AppTheme.CORAL_VIBRANT }
                )
            }

            // Bouton de Validation
            val activeColor = when (selectedTheme) {
                AppTheme.CLASSIC_DARK -> ClassicAccent
                AppTheme.ORIGINAL_PURPLE -> LightPurple
                AppTheme.CORAL_VIBRANT -> CoralAccent
            }

            Button(
                onClick = { onThemeSelected(selectedTheme) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(
                    text = "Commencer l'expérience",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ThemeCard(
    theme: AppTheme,
    title: String,
    description: String,
    accentColor: Color,
    bgColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderAlpha by animateFloatAsState(if (isSelected) 0.8f else 0.15f, label = "borderAlpha")
    val cardBg = if (isSelected) bgColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.03f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor.copy(alpha = borderAlpha) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini mockup de l'interface du player pour donner un aperçu réel
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor.copy(alpha = 0.8f))
                    .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                ) {
                    // Mini pochette d'album
                    Box(
                        modifier = Modifier
                            .size(24.dp)
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
                    // Mini barre de progression
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight()
                                .background(accentColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Textes descriptifs
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Cercle ou Checkmark de sélection
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Sélectionné",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                )
            }
        }
    }
}
