package com.credo.soundgroove.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.ui.components.formatDuration
import com.credo.soundgroove.ui.theme.*
import com.credo.soundgroove.util.PlayerGuards
import com.credo.soundgroove.util.blendWithAlbumArt
import com.credo.soundgroove.util.rememberAlbumArtAccentColor
import kotlinx.coroutines.launch

@Composable
fun BottomNavBar(selectedTab: Int, accentColor: Color, onTabSelected: (Int) -> Unit) {
    data class NavItem(val label: String, val iconRes: Int)

    val tabs = listOf(
        NavItem("Accueil", R.drawable.ic_home),
        NavItem("Bibliothèque", R.drawable.ic_playlists),
        NavItem("Recherche", R.drawable.ic_search),
        NavItem("Profil", R.drawable.ic_profile)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SgSpacing.sm, vertical = SgSpacing.xs)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(SgSpacing.navHeight),
            cornerRadius = SgRadius.pill,
            accentColor = accentColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SgSpacing.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val reducedMotion = rememberSgReducedMotion()
                val pillSpec = if (reducedMotion) {
                    androidx.compose.animation.core.snap()
                } else {
                    SgMotion.tweenFastOf<Color>()
                }
                tabs.forEachIndexed { index, item ->
                    val selected = selectedTab == index
                    val tabInteraction = remember(index) { MutableInteractionSource() }
                    // Pill sélection : couleurs FastMs (Mode perf = snap).
                    val tabBg by animateColorAsState(
                        targetValue = if (selected) accentColor.copy(alpha = 0.20f) else Color.Transparent,
                        animationSpec = pillSpec,
                        label = "navTabBg"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) accentColor else TextTertiary,
                        animationSpec = pillSpec,
                        label = "navIconTint"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .sgPressScale(tabInteraction, pressedScale = 0.94f, pressedAlpha = 0.85f)
                            .clip(RoundedCornerShape(SgRadius.pill))
                            .background(tabBg)
                            .clickable(
                                interactionSource = tabInteraction,
                                indication = null,
                            ) { onTabSelected(index) }
                            .padding(vertical = SgSpacing.sm, horizontal = SgSpacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(item.iconRes),
                                contentDescription = item.label,
                                tint = iconTint,
                                modifier = Modifier.size(SgSpacing.iconSize)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = iconTint,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

