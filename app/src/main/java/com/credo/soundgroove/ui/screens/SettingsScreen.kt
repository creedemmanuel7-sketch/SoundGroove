package com.credo.soundgroove.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.ui.theme.*

fun formatSleepTimerDisplay(seconds: Int?): String? {
    if (seconds == null) return null
    if (seconds < 0) return "Fin de piste"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d restantes".format(m, s)
}

@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    accentColor: Color,
    appVersion: String,
    songCount: Int,
    favoriteCount: Int,
    playlistCount: Int,
    sleepTimerRemainingSeconds: Int?,
    onBack: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    val backgroundBrush = themeBackgroundBrush(currentTheme)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Paramètres",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            SettingsSection(title = "Apparence") {
                Text(
                    text = "Thème",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeOptionRow(
                        title = "Vert Émeraude",
                        description = "Style classique sombre",
                        accentColor = ClassicAccent,
                        isSelected = currentTheme == AppTheme.CLASSIC_DARK,
                        onClick = { onThemeSelected(AppTheme.CLASSIC_DARK) }
                    )
                    ThemeOptionRow(
                        title = "Violet Original",
                        description = "L'esprit SoundGroove",
                        accentColor = LightPurple,
                        isSelected = currentTheme == AppTheme.ORIGINAL_PURPLE,
                        onClick = { onThemeSelected(AppTheme.ORIGINAL_PURPLE) }
                    )
                    ThemeOptionRow(
                        title = "Corail Vibrant",
                        description = "Ambiance chaleureuse",
                        accentColor = CoralAccent,
                        isSelected = currentTheme == AppTheme.CORAL_VIBRANT,
                        onClick = { onThemeSelected(AppTheme.CORAL_VIBRANT) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsSection(title = "Lecture") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSleepTimer() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(accentColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bedtime,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Minuterie de sommeil",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val timerText = formatSleepTimerDisplay(sleepTimerRemainingSeconds)
                        Text(
                            text = timerText ?: "Arrêter la lecture automatiquement",
                            color = if (timerText != null) accentColor else TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (sleepTimerRemainingSeconds != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onCancelSleepTimer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Annuler la minuterie", color = accentColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsSection(title = "Statistiques") {
                StatRow(Icons.Filled.MusicNote, "Morceaux", "$songCount")
                Spacer(modifier = Modifier.height(10.dp))
                StatRow(Icons.Filled.Favorite, "Favoris", "$favoriteCount")
                Spacer(modifier = Modifier.height(10.dp))
                StatRow(Icons.Filled.PlaylistPlay, "Playlists", "$playlistCount")
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsSection(title = "À propos") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("SoundGroove", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("v$appVersion", color = TextSecondary, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.04f), Color.Transparent)
                    )
                )
                .padding(18.dp)
        ) {
            Text(
                text = title.uppercase(),
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    description: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor.copy(alpha = 0.6f) else GlassBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(accentColor.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(accentColor, CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, color = TextSecondary, fontSize = 11.sp)
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Sélectionné",
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
