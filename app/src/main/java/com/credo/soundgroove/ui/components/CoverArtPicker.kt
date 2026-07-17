package com.credo.soundgroove.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.credo.soundgroove.data.model.Song

@Composable
fun rememberSongCoverArtPicker(
    onCoverSelected: (Song, Uri) -> Unit
): (Song) -> Unit {
    var pendingSong by remember { mutableStateOf<Song?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { picked ->
            pendingSong?.let { song -> onCoverSelected(song, picked) }
        }
        pendingSong = null
    }
    return { song ->
        pendingSong = song
        launcher.launch("image/*")
    }
}
