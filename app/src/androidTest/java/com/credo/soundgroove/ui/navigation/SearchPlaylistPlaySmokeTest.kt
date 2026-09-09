package com.credo.soundgroove.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parcours Compose mock : Search → add playlist (tap) → play intent → Player.
 * Sans MediaController / buffering (harness [SmokeNavGraph] uniquement).
 * Inscription : N/A (lecteur local).
 */
@RunWith(AndroidJUnit4::class)
class SearchPlaylistPlaySmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun search_addPlaylist_playIntent_opensPlayerHarness() {
        composeRule.setContent { SmokeNavGraph() }

        composeRule.onNodeWithTag("smoke_home").assertIsDisplayed()
        composeRule.onNodeWithTag("smoke_open_search").performClick()
        composeRule.onNodeWithTag("smoke_search").assertIsDisplayed()
        composeRule.onNodeWithTag("smoke_add_playlist").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("smoke_play_intent").performClick()
        composeRule.onNodeWithTag("smoke_player").assertIsDisplayed()
    }
}
