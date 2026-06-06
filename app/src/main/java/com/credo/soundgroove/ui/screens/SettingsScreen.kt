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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.R
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
    listeningTimeLabel: String = "0 min",
    sleepTimerRemainingSeconds: Int?,
    playbackSpeed: Float,
    onBack: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onOpenPlaybackSpeed: () -> Unit,
    smartNotificationsEnabled: Boolean = true,
    onSmartNotificationsChange: (Boolean) -> Unit = {},
    persistentMiniPlayerEnabled: Boolean = true,
    onPersistentMiniPlayerChange: (Boolean) -> Unit = {},
    performanceModeEnabled: Boolean = false,
    onPerformanceModeChange: (Boolean) -> Unit = {},
    onReloadMusic: () -> Unit = {},
    onClearRecentlyPlayed: () -> Unit = {}
) {
    val backgroundBrush = themeBackgroundBrush(currentTheme)
    var showClearRecentConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(38.dp))

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
                        painter = painterResource(R.drawable.ic_back),
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

            Spacer(modifier = Modifier.height(18.dp))

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

            Spacer(modifier = Modifier.height(14.dp))

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

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorder.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPlaybackSpeed() }
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
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vitesse de lecture",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val speedLabel = if (playbackSpeed == playbackSpeed.toLong().toFloat()) {
                            "${playbackSpeed.toLong()}x"
                        } else {
                            "${playbackSpeed}x"
                        }
                        Text(
                            text = "Actuelle : $speedLabel",
                            color = accentColor,
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
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingsSection(title = "Confort") {
                SettingsToggleRow(
                    icon = Icons.Filled.Notifications,
                    title = "Notifications intelligentes",
                    description = "Prépare les rappels discrets et résumés d'écoute",
                    checked = smartNotificationsEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onSmartNotificationsChange
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleRow(
                    icon = Icons.Filled.LibraryMusic,
                    title = "Mini-player persistant",
                    description = "Garder les contrôles rapides en bas de l'écran",
                    checked = persistentMiniPlayerEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onPersistentMiniPlayerChange
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleRow(
                    icon = Icons.Filled.Bolt,
                    title = "Mode performance",
                    description = "Réduire les effets visuels lors des longues sessions",
                    checked = performanceModeEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onPerformanceModeChange
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingsSection(title = "Bibliothèque") {
                SettingsActionRow(
                    iconRes = R.drawable.ic_songs,
                    title = "Rescanner la bibliothèque",
                    description = "Rechercher les nouveaux fichiers audio",
                    accentColor = accentColor,
                    onClick = onReloadMusic
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsActionRow(
                    iconRes = R.drawable.ic_trash,
                    title = "Vider les écoutes récentes",
                    description = "Efface l'historique local, sans toucher aux fichiers",
                    accentColor = FavoritePink,
                    onClick = { showClearRecentConfirm = true }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingsSection(title = "Statistiques") {
                StatRowIcon(R.drawable.ic_songs, "Morceaux", "$songCount")
                Spacer(modifier = Modifier.height(10.dp))
                StatRowIcon(R.drawable.ic_favorite_filled, "Favoris", "$favoriteCount")
                Spacer(modifier = Modifier.height(10.dp))
                StatRowIcon(R.drawable.ic_playlists, "Playlists", "$playlistCount")
                Spacer(modifier = Modifier.height(10.dp))
                StatRowIcon(R.drawable.ic_play, "Heures d'écoute", listeningTimeLabel)
            }

            Spacer(modifier = Modifier.height(14.dp))

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

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showClearRecentConfirm) {
        AlertDialog(
            onDismissRequest = { showClearRecentConfirm = false },
            title = { Text("Vider les écoutes récentes ?", color = TextPrimary) },
            text = {
                Text(
                    "Cette action supprime uniquement l'historique local affiché dans Profil.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearRecentlyPlayed()
                        showClearRecentConfirm = false
                    }
                ) {
                    Text("Vider", color = FavoritePink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearRecentConfirm = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SgSpacing.sm)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = GlassBorder.copy(alpha = 0.22f))
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
            .background(if (isSelected) accentColor.copy(alpha = 0.11f) else GlassSurface.copy(alpha = 0.28f))
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor.copy(alpha = 0.45f) else GlassBorder.copy(alpha = 0.26f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accentColor.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassSurface
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    iconRes: Int,
    title: String,
    description: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accentColor.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = TextSecondary, fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
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

@Composable
private fun StatRowIcon(
    iconRes: Int,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
