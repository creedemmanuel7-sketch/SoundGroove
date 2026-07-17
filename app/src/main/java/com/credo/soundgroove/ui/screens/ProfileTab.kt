package com.credo.soundgroove.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.data.repository.ListeningStats
import com.credo.soundgroove.ui.components.SongItem
import com.credo.soundgroove.ui.theme.*

@Composable
fun ProfileTab(
    songs: List<Song>,
    recentlyPlayed: List<Song>,
    favoriteSongs: List<Song>,
    playlists: List<Playlist>,
    listeningStats: ListeningStats,
    formatListeningTime: (Long) -> String,
    currentTheme: AppTheme,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    smartNotificationsEnabled: Boolean = true,
    onSmartNotificationsChange: (Boolean) -> Unit = {},
    performanceModeEnabled: Boolean = false,
    onPerformanceModeChange: (Boolean) -> Unit = {},
    onThemeSelected: (AppTheme) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    appVersion: String = "1.0",
    onSongClick: (Song) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("soundgroove_prefs", android.content.Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(prefs.getString("profile_name", "Credson") ?: "Credson") }
    var avatarUri by remember {
        mutableStateOf(prefs.getString("profile_avatar_uri", null)?.let { Uri.parse(it) })
    }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            avatarUri = it
            prefs.edit().putString("profile_avatar_uri", it.toString()).apply()
        }
    }

    val topArtists = remember(recentlyPlayed) {
        recentlyPlayed
            .groupBy { it.artist }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { it.key }
    }

    val mostPlayedRecentSongs = remember(recentlyPlayed) {
        recentlyPlayed
            .groupBy { it.id }
            .values
            .sortedByDescending { it.size }
            .mapNotNull { group -> group.firstOrNull()?.let { it to group.size } }
            .take(3)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SgSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(SgSpacing.sectionGap)
    ) {
        item {
            Spacer(modifier = Modifier.height(SgSpacing.screenTop))
            ProfileIdentityCard(
                userName = userName,
                avatarUri = avatarUri,
                themeLabel = themeDisplayName(currentTheme),
                accentColor = accentColor,
                onEditClick = { showEditDialog = true },
                onAvatarClick = { avatarPicker.launch("image/*") }
            )
        }

        item {
            ProfileStatsSection(
                listeningStats = listeningStats,
                formatListeningTime = formatListeningTime,
                accentColor = accentColor
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileLibraryStat(
                    value = "${songs.size}",
                    label = "Titres",
                    iconRes = R.drawable.ic_playlists,
                    tint = accentColor,
                    modifier = Modifier.weight(1f)
                )
                ProfileLibraryStat(
                    value = "${favoriteSongs.size}",
                    label = "Favoris",
                    iconRes = R.drawable.ic_favorite_filled,
                    tint = FavoritePink,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = "RACCOURCIS",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileShortcut(
                        title = "Favoris",
                        subtitle = "${favoriteSongs.size} titres",
                        iconRes = R.drawable.ic_favorite_filled,
                        tint = FavoritePink,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenFavorites
                    )
                    ProfileShortcut(
                        title = "Playlists",
                        subtitle = "${playlists.size} listes",
                        iconRes = R.drawable.ic_playlists,
                        tint = accentColor,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPlaylists
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileShortcut(
                        title = "Paramètres",
                        subtitle = "Lecture et apparence",
                        iconRes = R.drawable.ic_settings,
                        tint = accentColor,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSettings
                    )
                    ProfileShortcut(
                        title = "Sauvegarde",
                        subtitle = "Exporter ou restaurer",
                        iconRes = R.drawable.ic_songs,
                        tint = accentColor,
                        modifier = Modifier.weight(1f),
                        onClick = { showBackupDialog = true }
                    )
                }
            }
        }

        item {
            ProfileQuickPreferences(
                currentTheme = currentTheme,
                accentColor = accentColor,
                smartNotificationsEnabled = smartNotificationsEnabled,
                onSmartNotificationsChange = onSmartNotificationsChange,
                performanceModeEnabled = performanceModeEnabled,
                onPerformanceModeChange = onPerformanceModeChange,
                onThemeSelected = onThemeSelected
            )
        }

        if (topArtists.isNotEmpty()) {
            item {
                ProfileTopArtistsSection(topArtists = topArtists, recentlyPlayed = recentlyPlayed)
            }
        }

        if (mostPlayedRecentSongs.isNotEmpty()) {
            item {
                Text(
                    text = "MORCEAUX À RETROUVER",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(mostPlayedRecentSongs) { (song, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongItem(
                        song = song,
                        isPlaying = false,
                        onClick = { onSongClick(song) },
                        modifier = Modifier.weight(1f),
                        accentColor = accentColor
                    )
                    Text(
                        text = "×$count",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showAboutDialog = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "À propos de SoundGroove",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showEditDialog) {
        ProfileEditNameDialog(
            initialName = userName,
            onDismiss = { showEditDialog = false },
            onSave = { name ->
                userName = name.ifBlank { "Credson" }
                prefs.edit().putString("profile_name", userName).apply()
                showEditDialog = false
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text("SoundGroove", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Lecteur musical local, élégant et fluide.\n\nVersion $appVersion",
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Fermer", color = accentColor)
                }
            },
            containerColor = CardSurface
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Sauvegarde", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Exportez vos favoris et playlists, ou restaurez une sauvegarde existante.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackupDialog = false
                    onExportBackup()
                }) {
                    Text("Exporter", color = accentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackupDialog = false
                    onImportBackup()
                }) {
                    Text("Restaurer", color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }
}

@Composable
private fun ProfileIdentityCard(
    userName: String,
    avatarUri: Uri?,
    themeLabel: String,
    accentColor: Color,
    onEditClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        cornerRadius = 20.dp,
        accentColor = accentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(accentColor.copy(0.22f), accentColor.copy(0.04f), Color.Transparent)
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(accentColor, accentColor.copy(alpha = 0.45f))),
                        CircleShape
                    )
                    .border(2.dp, accentColor.copy(alpha = 0.5f), CircleShape)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Photo de profil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = userName.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = themeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Appuie pour modifier le nom",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ProfileStatsSection(
    listeningStats: ListeningStats,
    formatListeningTime: (Long) -> String,
    accentColor: Color
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = SgRadius.lg, accentColor = accentColor) {
        Column(modifier = Modifier.padding(SgSpacing.lg)) {
            Text(
                text = "STATISTIQUES D'ÉCOUTE",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileMetric(
                    label = "Cette semaine",
                    value = formatListeningTime(listeningStats.weekSeconds),
                    modifier = Modifier.weight(1f)
                )
                ProfileMetric(
                    label = "Ce mois",
                    value = formatListeningTime(listeningStats.monthSeconds),
                    modifier = Modifier.weight(1f)
                )
                ProfileMetric(
                    label = "Total",
                    value = formatListeningTime(listeningStats.totalSeconds),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            // Gamification légère (sobre, sans confettis) : la série mérite plus de
            // présence qu'une simple case du grid ci-dessus — feu + microcopy qui
            // change avec la longueur de la série, cf. demande explicite "pas trop
            // discret" (mais reste dans les tokens thème, pas de couleur inventée).
            StreakHighlightRow(streakDays = listeningStats.streakDays, accentColor = accentColor)
            todayMilestoneLabel(listeningStats.todaySeconds)?.let { milestone ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    MilestoneChip(label = milestone, accentColor = accentColor)
                }
            }
        }
    }
}

@Composable
private fun StreakHighlightRow(
    streakDays: Int,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SgRadius.md))
            .background(
                Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.16f), Color.Transparent))
            )
            .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(SgRadius.md))
            .padding(horizontal = SgSpacing.md, vertical = SgSpacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    Brush.radialGradient(listOf(accentColor, accentColor.copy(alpha = 0.55f))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = "Série d'écoute",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(SgSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (streakDays > 0) {
                    "$streakDays jour${if (streakDays > 1) "s" else ""} de série"
                } else {
                    "Pas encore de série"
                },
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = streakMicrocopy(streakDays),
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MilestoneChip(label: String, accentColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(SgRadius.pill))
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(SgRadius.pill))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Une seule micro-copie motivante par palier — pas de superposition de messages. */
private fun streakMicrocopy(streakDays: Int): String = when {
    streakDays <= 0 -> "Écoute aujourd'hui pour démarrer une série."
    streakDays == 1 -> "Un jour d'écoute. Continue demain !"
    streakDays < 7 -> "Belle régularité, jour après jour."
    streakDays < 30 -> "Une semaine ou plus d'affilée, impressionnant."
    else -> "Une régularité remarquable. Bravo."
}

/** Jalon discret du jour (1 seul affiché, le plus haut palier atteint) — ex. "1 h aujourd'hui". */
private fun todayMilestoneLabel(todaySeconds: Long): String? = when {
    todaySeconds >= 7200L -> "2 h d'écoute aujourd'hui"
    todaySeconds >= 3600L -> "1 h d'écoute aujourd'hui"
    todaySeconds >= 1800L -> "30 min d'écoute aujourd'hui"
    else -> null
}

@Composable
private fun ProfileLibraryStat(
    value: String,
    label: String,
    iconRes: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier, cornerRadius = SgRadius.lg, accentColor = tint) {
        Column(modifier = Modifier.padding(SgSpacing.lg)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tint.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = tint,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun ProfileQuickPreferences(
    currentTheme: AppTheme,
    accentColor: Color,
    smartNotificationsEnabled: Boolean,
    onSmartNotificationsChange: (Boolean) -> Unit,
    performanceModeEnabled: Boolean,
    onPerformanceModeChange: (Boolean) -> Unit,
    onThemeSelected: (AppTheme) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = SgRadius.lg, accentColor = accentColor) {
        Column(modifier = Modifier.padding(SgSpacing.lg)) {
            Text(
                text = "PRÉFÉRENCES RAPIDES",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Thème",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTheme.entries.forEach { theme ->
                    ProfileThemeChip(
                        label = themeShortName(theme),
                        accent = accentColorForTheme(theme),
                        selected = currentTheme == theme,
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeSelected(theme) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            ProfileToggleRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                description = "Rappels et résumés de session",
                checked = smartNotificationsEnabled,
                accentColor = accentColor,
                onCheckedChange = onSmartNotificationsChange
            )
            Spacer(modifier = Modifier.height(10.dp))
            ProfileToggleRow(
                icon = Icons.Filled.Speed,
                title = "Mode performance",
                description = "Réduit les effets visuels",
                checked = performanceModeEnabled,
                accentColor = accentColor,
                onCheckedChange = onPerformanceModeChange
            )
        }
    }
}

@Composable
private fun ProfileThemeChip(
    label: String,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) accent.copy(alpha = 0.22f) else GlassSurface.copy(alpha = 0.32f)
    val borderColor = if (selected) accent else GlassBorder.copy(alpha = 0.3f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(accent, CircleShape)
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

@Composable
private fun ProfileToggleRow(
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
                .size(38.dp)
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
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, color = TextSecondary, fontSize = 11.sp)
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
private fun ProfileTopArtistsSection(
    topArtists: List<String>,
    recentlyPlayed: List<Song>
) {
    Text(
        text = "TOP ARTISTES",
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        topArtists.take(3).forEach { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                val artistSong = recentlyPlayed.firstOrNull { it.artist == artist }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(listOf(GraphiteMid, GraphiteCard)),
                            CircleShape
                        )
                        .border(1.5.dp, GlassBorder.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (artistSong?.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artistSong.albumArtUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = artist.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = artist,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ProfileEditNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var tempName by remember { mutableStateOf(initialName) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .background(CardSurface, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text(
                text = "Modifier le profil",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = tempName,
                onValueChange = { tempName = it },
                label = { Text("Nom", color = TextSecondary) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GraphiteCard,
                    unfocusedContainerColor = GraphiteCard,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = SilverAccent,
                    focusedIndicatorColor = SilverAccent,
                    unfocusedIndicatorColor = TextSecondary
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Annuler",
                    color = TextSecondary,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(SilverAccent, RoundedCornerShape(12.dp))
                        .clickable { onSave(tempName) }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Enregistrer",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface.copy(alpha = 0.32f))
            .border(1.dp, GlassBorder.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileShortcut(
    title: String,
    subtitle: String,
    iconRes: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.clickable { onClick() },
        cornerRadius = SgRadius.lg,
        accentColor = tint
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(tint.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun themeDisplayName(theme: AppTheme): String = when (theme) {
    AppTheme.NOIR_ABSOLU -> "Noir Absolu"
    AppTheme.ARGENT_CLAIR -> "Clair Argent"
    AppTheme.GRAPHITE -> "Graphite"
}

private fun themeShortName(theme: AppTheme): String = when (theme) {
    AppTheme.NOIR_ABSOLU -> "Noir"
    AppTheme.ARGENT_CLAIR -> "Clair"
    AppTheme.GRAPHITE -> "Graphite"
}
