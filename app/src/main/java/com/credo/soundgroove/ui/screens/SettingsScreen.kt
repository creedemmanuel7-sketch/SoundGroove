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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.R
import com.credo.soundgroove.ui.components.SgSwitch
import com.credo.soundgroove.ui.components.ThemePicker
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
    currentAccent: AppAccent,
    accentColor: Color,
    appVersion: String,
    songCount: Int,
    favoriteCount: Int,
    playlistCount: Int,
    listeningTimeLabel: String = "0 min",
    listeningWeekLabel: String? = null,
    listeningMonthLabel: String? = null,
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
    onAccentSelected: (AppAccent) -> Unit,
    albumCoverAccentEnabled: Boolean = false,
    onAlbumCoverAccentChange: (Boolean) -> Unit = {},
    onOpenSleepTimer: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onOpenPlaybackSpeed: () -> Unit,
    smartNotificationsEnabled: Boolean = true,
    onSmartNotificationsChange: (Boolean) -> Unit = {},
    persistentMiniPlayerEnabled: Boolean = true,
    onPersistentMiniPlayerChange: (Boolean) -> Unit = {},
    performanceModeEnabled: Boolean = false,
    onPerformanceModeChange: (Boolean) -> Unit = {},
    onOpenCarMode: () -> Unit = {},
    remoteHostEnabled: Boolean = false,
    remotePin: String? = null,
    remoteLanIp: String? = null,
    remotePort: Int = 3847,
    remoteClientCount: Int = 0,
    remoteHostError: String? = null,
    onRemoteHostChange: (Boolean) -> Unit = {},
    onRegenerateRemotePin: () -> Unit = {},
    onReloadMusic: () -> Unit = {},
    onClearRecentlyPlayed: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    libraryFolderCount: Int = 0,
    onAddLibraryFolder: () -> Unit = {},
    scrobbleTotal: Int = 0,
) {
    val revealState = rememberSgThemeRevealState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showClearRecentConfirm by remember { mutableStateOf(false) }
    var showClearSearchConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var cacheSizeLabel by remember { mutableStateOf<String?>(null) }
    var appDataSizeLabel by remember { mutableStateOf<String?>(null) }

    suspend fun refreshCacheSize() {
        val breakdown = withContext(Dispatchers.IO) {
            StorageMaintenance.computeBreakdown(context)
        }
        cacheSizeLabel = StorageMaintenance.formatBytes(breakdown.clearableBytes)
        appDataSizeLabel = StorageMaintenance.formatBytes(breakdown.totalAppDataBytes)
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
                        .padding(horizontal = SgSpacing.gutter)
                        .padding(bottom = SgSpacing.contentInsetBottom)
                ) {
            Spacer(modifier = Modifier.height(SgSpacing.screenTop))

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
                ThemePicker(
                    currentTheme = currentTheme,
                    selectedRingColor = accentColor,
                    onThemeClick = { theme, origin ->
                        launchThemeReveal(
                            revealState, scope, theme, currentTheme, origin, onThemeSelected
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Couleur d'accent",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                AccentSwatchRow(
                    currentAccent = currentAccent,
                    selectedRingColor = TextPrimary,
                    manualSelectionEnabled = !albumCoverAccentEnabled,
                    onAccentSelected = onAccentSelected
                )

                Spacer(modifier = Modifier.height(14.dp))

                SettingsToggleRow(
                    icon = Icons.Filled.Album,
                    title = "Accent de la pochette",
                    description = if (albumCoverAccentEnabled) {
                        "Couleur extraite du morceau en cours"
                    } else {
                        "Utiliser l'accent choisi ci-dessus"
                    },
                    checked = albumCoverAccentEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onAlbumCoverAccentChange
                )

                if (albumCoverAccentEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Les pastilles restent votre choix de repli lorsque cette option est désactivée.",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
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

                // NavigationRow plates (pas de fill accent sur les wells)
                SettingsNavRow(
                    icon = Icons.Filled.Speed,
                    title = "Vitesse et tonalité",
                    subtitle = run {
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
                        "Vitesse : $speedLabel · Tonalité : $pitchLabel"
                    },
                    onClick = onOpenPlaybackSpeed
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorder.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))

                SettingsNavRow(
                    icon = Icons.Filled.GraphicEq,
                    title = "Égaliseur",
                    subtitle = if (equalizerEnabled) equalizerPresetLabel else "Désactivé",
                    onClick = onOpenEqualizer
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    icon = Icons.Filled.SkipNext,
                    title = "Enchaînement sans coupure",
                    description = "Passe d'un morceau à l'autre sans silence",
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
                            .background(SurfaceElevated, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BlurLinear,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(SgSpacing.iconSize)
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
                Spacer(modifier = Modifier.height(12.dp))
                SettingsVectorActionRow(
                    icon = Icons.Filled.DirectionsCar,
                    title = "Mode voiture",
                    description = "Plein écran, gros boutons, listes courtes, sombre forcé",
                    accentColor = accentColor,
                    onClick = onOpenCarMode
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleRow(
                    icon = Icons.Filled.Computer,
                    title = "Remote PC",
                    description = "Exposer le lecteur sur le LAN (WebSocket port $remotePort)",
                    checked = remoteHostEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onRemoteHostChange
                )
                if (remoteHostEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    RemoteHostInfoCard(
                        pin = remotePin,
                        lanIp = remoteLanIp,
                        port = remotePort,
                        clientCount = remoteClientCount,
                        error = remoteHostError,
                        accentColor = accentColor,
                        onRegeneratePin = onRegenerateRemotePin,
                    )
                } else if (remoteHostError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = remoteHostError,
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingsSection(title = "Sauvegarde") {
                SettingsActionRow(
                    iconRes = R.drawable.ic_songs,
                    title = "Exporter les données",
                    description = "Favoris, playlists, thème et accent au format JSON",
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
                    iconRes = R.drawable.ic_folder,
                    title = "Ajouter un dossier",
                    description = if (libraryFolderCount > 0) {
                        "$libraryFolderCount dossier(s) SAF en plus du MediaStore"
                    } else {
                        "Inclure un dossier via l'accès aux fichiers"
                    },
                    accentColor = accentColor,
                    onClick = onAddLibraryFolder
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                appDataSizeLabel?.let { totalLabel ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Données app (cache + base + réglages) · $totalLabel",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingsSection(title = "Statistiques") {
                StatRowIcon(R.drawable.ic_songs, "Morceaux", "$songCount")
                Spacer(modifier = Modifier.height(10.dp))
                StatRowIcon(R.drawable.ic_favorite_filled, "Favoris", "$favoriteCount")
                Spacer(modifier = Modifier.height(10.dp))
                StatRowIcon(R.drawable.ic_playlists, "Playlists", "$playlistCount")
                Spacer(modifier = Modifier.height(10.dp))
                listeningWeekLabel?.let { weekLabel ->
                    StatRowIcon(R.drawable.ic_play, "Cette semaine", weekLabel)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                listeningMonthLabel?.let { monthLabel ->
                    StatRowIcon(R.drawable.ic_play, "Ce mois", monthLabel)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                StatRowIcon(R.drawable.ic_play, "Depuis le début", listeningTimeLabel)
                if (scrobbleTotal > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    StatRowIcon(R.drawable.ic_repeat, "Scrobbles locaux", "$scrobbleTotal")
                }
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
private fun RemoteHostInfoCard(
    pin: String?,
    lanIp: String?,
    port: Int,
    clientCount: Int,
    error: String?,
    accentColor: Color,
    onRegeneratePin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "PIN · ${pin ?: "······"}",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "IP · ${lanIp ?: "non détectée"}  ·  port $port",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = if (clientCount > 0) "PC connecté ($clientCount)" else "En attente du PC…",
            color = if (clientCount > 0) accentColor else TextSecondary,
            fontSize = 12.sp,
        )
        if (error != null) {
            Text(text = error, color = TextSecondary, fontSize = 12.sp)
        }
        Text(
            text = "Nouveau PIN",
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onRegeneratePin() },
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
private fun AccentSwatchRow(
    currentAccent: AppAccent,
    selectedRingColor: Color,
    manualSelectionEnabled: Boolean = true,
    onAccentSelected: (AppAccent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!manualSelectionEnabled) Modifier.graphicsLayer { alpha = 0.45f } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppAccent.entries.forEach { accent ->
            val isSelected = accent == currentAccent
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.primary)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) selectedRingColor else GlassBorder.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
                    .clickable { onAccentSelected(accent) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = accent.label,
                        tint = if (com.credo.soundgroove.util.relativeLuminance(accent.primary) > 0.55f) {
                            Color(0xFF1A1D23)
                        } else {
                            Color.White
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
            .heightIn(min = SgSpacing.listRowHeight, max = SgSpacing.listRowTall)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // IconWell : surface-2 + icon secondary
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SurfaceElevated, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(SgSpacing.iconSize)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = TextSecondary, fontSize = 12.sp)
        }
        SgSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            accentColor = accentColor
        )
    }
}

@Composable
private fun SettingsVectorActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    @Suppress("UNUSED_PARAMETER") accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SurfaceElevated, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(SgSpacing.iconSize),
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
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsActionRow(
    iconRes: Int,
    title: String,
    description: String,
    @Suppress("UNUSED_PARAMETER") accentColor: Color,
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
                .background(SurfaceElevated, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(SgSpacing.iconSize)
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
private fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SgSpacing.listRowTall)
            .clickable(onClick = onClick)
            .padding(vertical = SgSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SurfaceElevated, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(SgSpacing.iconSize)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
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
private fun StatRowIcon(
    iconRes: Int,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SgSpacing.listRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SurfaceElevated, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(SgSpacing.iconSize)
            )
        }
        Spacer(modifier = Modifier.width(SgSpacing.md))
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
