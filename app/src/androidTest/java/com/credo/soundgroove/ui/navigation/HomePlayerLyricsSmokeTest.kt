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
 * Smoke Compose : Home → Player → Lyrics (harness [SmokeNavGraph]).
 * Exécution CI : `./gradlew :app:connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class HomePlayerLyricsSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_to_player_to_lyrics_isDisplayed() {
        composeRule.setContent { SmokeNavGraph() }

        composeRule.onNodeWithTag("smoke_home").assertIsDisplayed()
        composeRule.onNodeWithTag("smoke_open_player").performClick()

        composeRule.onNodeWithTag("smoke_player").assertIsDisplayed()
        composeRule.onNodeWithTag("smoke_open_lyrics").performClick()

        composeRule.onNodeWithTag("smoke_lyrics").assertIsDisplayed()
        composeRule.onNodeWithTag("smoke_lyrics_title").assertIsDisplayed()
    }
}
