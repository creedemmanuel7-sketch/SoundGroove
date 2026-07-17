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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.R
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.util.AudioFormatInfo
import com.credo.soundgroove.util.PlaybackPreferences
import com.credo.soundgroove.util.StorageMaintenance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    playbackPitch: Float = 1f,
    gaplessEnabled: Boolean = true,
    onGaplessChange: (Boolean) -> Unit = {},
    crossfadeDurationMs: Int = 0,
    onOpenCrossfade: () -> Unit = {},
    equalizerEnabled: Boolean = true,
    equalizerPresetLabel: String = "Normal",
    onOpenEqualizer: () -> Unit = {},
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
    onClearRecentlyPlayed: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onClearSearchHistory: () -> Unit = {}
) {
    val revealState = rememberSgThemeRevealState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showClearRecentConfirm by remember { mutableStateOf(false) }
    var showClearSearchConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var cacheSizeLabel by remember { mutableStateOf<String?>(null) }

    suspend fun refreshCacheSize() {
        cacheSizeLabel = withContext(Dispatchers.IO) {
            StorageMaintenance.formatBytes(StorageMaintenance.computeBreakdown(context).clearableBytes)
        }
    }

    LaunchedEffect(Unit) { refreshCacheSize() }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SgThemeRevealHost(
            baseTheme = currentTheme,
            revealState = revealState,
            modifier = Modifier.fillMaxSize()
        ) { theme ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeBackgroundBrush(theme))
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
                        title = "Noir Absolu",
                        description = "Noir profond, accent violet signature",
                        accentColor = BrandPurple,
                        isSelected = currentTheme == AppTheme.NOIR_ABSOLU,
                        onClick = { origin ->
                            launchThemeReveal(
                                revealState, scope, AppTheme.NOIR_ABSOLU, currentTheme, origin, onThemeSelected
                            )
                        }
                    )
                    ThemeOptionRow(
                        title = "Clair Argent",
                        description = "Fond clair, violet profond WCAG",
                        accentColor = BrandPurpleDeep,
                        isSelected = currentTheme == AppTheme.ARGENT_CLAIR,
                        onClick = { origin ->
                            launchThemeReveal(
                                revealState, scope, AppTheme.ARGENT_CLAIR, currentTheme, origin, onThemeSelected
                            )
                        }
                    )
                    ThemeOptionRow(
                        title = "Graphite",
                        description = "Graphite mat, violet atténué + argent",
                        accentColor = BrandPurpleMuted,
                        isSelected = currentTheme == AppTheme.GRAPHITE,
                        onClick = { origin ->
                            launchThemeReveal(
                                revealState, scope, AppTheme.GRAPHITE, currentTheme, origin, onThemeSelected
                            )
                        }
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
                            text = "Vitesse et tonalité",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val speedLabel = if (playbackSpeed == playbackSpeed.toLong().toFloat()) {
                            "${playbackSpeed.toLong()}x"
                        } else {
                            "${playbackSpeed}x"
                        }
                        val pitchLabel = if (playbackPitch == playbackPitch.toLong().toFloat()) {
                            "${playbackPitch.toLong()}x"
                        } else {
                            "${playbackPitch}x"
                        }
                        Text(
                            text = "Vitesse : $speedLabel · Tonalité : $pitchLabel",
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

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorder.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEqualizer() }
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
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Égaliseur",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (equalizerEnabled) equalizerPresetLabel else "Désactivé",
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

                Spacer(modifier = Modifier.height(12.dp))

                com.credo.soundgroove.ui.components.PlaybackModeIndicator(
                    gaplessEnabled = gaplessEnabled,
                    crossfadeMs = crossfadeDurationMs,
                    accentColor = accentColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    icon = Icons.Filled.SkipNext,
                    title = "Lecture gapless",
                    description = "Enchaînement fluide entre les pistes (ExoPlayer/Media3)",
                    checked = gaplessEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onGaplessChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCrossfade() }
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
                            imageVector = Icons.Filled.BlurLinear,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Crossfade",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = PlaybackPreferences.crossfadeLabel(crossfadeDurationMs),
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

            SettingsSection(title = "Sauvegarde") {
                SettingsActionRow(
                    iconRes = R.drawable.ic_songs,
                    title = "Exporter les données",
                    description = "Favoris, playlists et thème au format JSON",
                    accentColor = accentColor,
                    onClick = onExportBackup
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsActionRow(
                    iconRes = R.drawable.ic_playlists,
                    title = "Restaurer une sauvegarde",
                    description = "Remplacer favoris et playlists depuis un fichier JSON",
                    accentColor = accentColor,
                    onClick = { showImportConfirm = true }
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
                Spacer(modifier = Modifier.height(12.dp))
                SettingsActionRow(
                    iconRes = R.drawable.ic_trash,
                    title = "Effacer l'historique de recherche",
                    description = "Supprime les recherches enregistrées dans l'écran Recherche",
                    accentColor = FavoritePink,
                    onClick = { showClearSearchConfirm = true }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingsSection(title = "Stockage") {
                SettingsActionRow(
                    iconRes = R.drawable.ic_trash,
                    title = "Vider le cache",
                    description = "Paroles en cache, recherche web et cartes de partage · ${cacheSizeLabel ?: "Calcul…"}",
                    accentColor = accentColor,
                    onClick = { showClearCacheConfirm = true }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Les pochettes personnalisées et vos fichiers .lrc/.txt à côté des morceaux " +
                        "ne sont jamais supprimés par cette action.",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
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
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = AudioFormatInfo.FORMATS_ABOUT_TEXT,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
                }
            }
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

    if (showClearSearchConfirm) {
        AlertDialog(
            onDismissRequest = { showClearSearchConfirm = false },
            title = { Text("Effacer l'historique de recherche ?", color = TextPrimary) },
            text = {
                Text(
                    "Cette action supprime toutes les recherches récentes enregistrées.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearSearchHistory()
                        showClearSearchConfirm = false
                    }
                ) {
                    Text("Effacer", color = FavoritePink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchConfirm = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Vider le cache ?", color = TextPrimary) },
            text = {
                Text(
                    "Supprime le cache local des paroles (re-téléchargeables via LRCLIB ou " +
                        "relisibles depuis le fichier voisin de l'audio), le cache de la recherche " +
                        "web de paroles et les cartes de partage temporaires.\n\n" +
                        "Vos pochettes personnalisées et vos fichiers .lrc/.txt ne sont pas touchés.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                StorageMaintenance.clearClearableCaches(context)
                            }
                            refreshCacheSize()
                        }
                    }
                ) {
                    Text("Vider", color = FavoritePink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("Restaurer une sauvegarde ?", color = TextPrimary) },
            text = {
                Text(
                    "Les favoris et playlists actuels seront remplacés par le contenu du fichier choisi.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        onImportBackup()
                    }
                ) {
                    Text("Continuer", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
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
    onClick: (Offset) -> Unit
) {
    var revealOrigin by remember { mutableStateOf(Offset.Zero) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sgThemeRevealOrigin { revealOrigin = it }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.11f) else GlassSurface.copy(alpha = 0.28f))
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor.copy(alpha = 0.45f) else GlassBorder.copy(alpha = 0.26f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick(revealOrigin) }
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
