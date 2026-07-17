#!/usr/bin/env python3
"""Split MainActivity.kt into focused screen/component files."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/credo/soundgroove/MainActivity.kt"

# 1-based inclusive line ranges from current MainActivity.kt
SECTIONS = [
    (
        ROOT / "app/src/main/java/com/credo/soundgroove/data/model/Song.kt",
        "com.credo.soundgroove.data.model",
        88,
        104,
    ),
    (
        ROOT / "app/src/main/java/com/credo/soundgroove/ui/screens/MainScreen.kt",
        "com.credo.soundgroove.ui.screens",
        136,
        819,
    ),
    (
        ROOT / "app/src/main/java/com/credo/soundgroove/ui/components/BottomNavBar.kt",
        "com.credo.soundgroove.ui.components",
        822,
        900,
    ),
    (
        ROOT / "app/src/main/java/com/credo/soundgroove/ui/screens/HomeTab.kt",
        "com.credo.soundgroove.ui.screens",
        902,
        1340,
    ),
    (
        ROOT / "app/src/main/java/com/credo/soundgroove/ui/components/SongItem.kt",
        "com.credo.soundgroove.ui.components",
        1342,
        1583,
    ),
    (
        ROOT / "app/src/main/java/com/credo/soundgroove/ui/screens/RecentlyPlayedScreen.kt",
        "com.credo.soundgroove.ui.screens",
        1585,
        1690,
    ),
    (
        ROOT / "app/src/main/java/com/credo/soundgroove/ui/screens/PlayerScreen.kt",
        "com.credo.soundgroove.ui.screens",
        1692,
        2216,
    ),
    (
        ROOT / "app/src/main/java/com/credo/soundgroove/ui/screens/ProfileTab.kt",
        "com.credo.soundgroove.ui.screens",
        2268,
        2882,
    ),
    (
        ROOT / "app/src/main/java/com/credo/soundgroove/ui/screens/LibraryTab.kt",
        "com.credo.soundgroove.ui.screens",
        2884,
        4161,
    ),
    (
        ROOT / "app/src/main/java/com/credo/soundgroove/ui/screens/QueueScreen.kt",
        "com.credo.soundgroove.ui.screens",
        4167,
        None,
    ),
]

COMMON_IMPORTS = """import android.Manifest
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
"""

MAIN_ACTIVITY = """package com.credo.soundgroove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.credo.soundgroove.ui.navigation.AppNavigation
import com.credo.soundgroove.ui.screens.ThemeSelectionScreen
import com.credo.soundgroove.ui.theme.SoundGrooveTheme
import com.credo.soundgroove.ui.theme.accentColorForTheme
import com.credo.soundgroove.viewmodel.SoundGrooveViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SoundGrooveViewModel = viewModel()
            val currentTheme by viewModel.currentTheme.collectAsState()
            val showThemeSelection by viewModel.showThemeSelection.collectAsState()

            SoundGrooveTheme(appTheme = currentTheme) {
                if (showThemeSelection) {
                    ThemeSelectionScreen(
                        onThemeSelected = { theme ->
                            viewModel.completeThemeSelection(theme)
                        }
                    )
                } else {
                    val accentColor = accentColorForTheme(currentTheme)
                    AppNavigation(
                        viewModel = viewModel,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}
"""

COMPAT = """package com.credo.soundgroove

typealias Song = com.credo.soundgroove.data.model.Song
typealias Playlist = com.credo.soundgroove.data.model.Playlist
"""

MODELS_KT = """package com.credo.soundgroove.data.model

// Song et Playlist sont définis dans Song.kt
"""


def read_lines() -> list[str]:
    return SRC.read_text(encoding="utf-8").splitlines(keepends=True)


def slice_lines(lines: list[str], start: int, end: int | None) -> str:
    start_idx = start - 1
    end_idx = len(lines) if end is None else end
    body = "".join(lines[start_idx:end_idx])
    return body.strip() + "\n"


def write_file(path: Path, package: str, body: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    content = f"package {package}\n\n{COMMON_IMPORTS}\n{body}\n"
    path.write_text(content, encoding="utf-8")
    print(f"Wrote {path.relative_to(ROOT)} ({len(content.splitlines())} lines)")


def main() -> None:
    lines = read_lines()
    for path, package, start, end in SECTIONS:
        body = slice_lines(lines, start, end)
        write_file(path, package, body)

    (ROOT / "app/src/main/java/com/credo/soundgroove/data/model/Models.kt").write_text(
        MODELS_KT, encoding="utf-8"
    )
    (ROOT / "app/src/main/java/com/credo/soundgroove/ModelCompat.kt").write_text(
        COMPAT, encoding="utf-8"
    )
    SRC.write_text(MAIN_ACTIVITY, encoding="utf-8")
    print(f"Wrote slim MainActivity.kt ({len(MAIN_ACTIVITY.splitlines())} lines)")


if __name__ == "__main__":
    main()
