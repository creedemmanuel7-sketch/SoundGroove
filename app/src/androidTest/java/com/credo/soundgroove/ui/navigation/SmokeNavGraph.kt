package com.credo.soundgroove.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Harness instrumenté : parcours Home → Player → Lyrics sans MediaController.
 * Tags stables pour CI ; n'importe pas PlayerScreen / ProfileTab (évite conflits sprint).
 */
@Composable
fun SmokeNavGraph() {
    val navController = rememberNavController()
    var lyricsOpen by remember { mutableStateOf(false) }
    var lyricsProgress by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
        ) {
            composable(Routes.HOME) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("smoke_home"),
                ) {
                    Text("Accueil")
                    Text(
                        text = "Ouvrir le lecteur",
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .testTag("smoke_open_player")
                            .clickable { navController.navigate(Routes.PLAYER) },
                    )
                }
            }
            composable(Routes.PLAYER) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("smoke_player"),
                ) {
                    Text("Lecteur")
                    Text(
                        text = "Ouvrir les paroles",
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .testTag("smoke_open_lyrics")
                            .clickable {
                                lyricsOpen = true
                                lyricsProgress = 1f
                            },
                    )
                    Text(
                        text = "Retour",
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("smoke_player_back")
                            .clickable { navController.popBackStack() },
                    )
                }
            }
        }

        if (lyricsOpen && lyricsProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("smoke_lyrics")
                    .clickable {
                        lyricsProgress = 0f
                        lyricsOpen = false
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Paroles",
                    modifier = Modifier.testTag("smoke_lyrics_title"),
                )
            }
        }
    }
}
