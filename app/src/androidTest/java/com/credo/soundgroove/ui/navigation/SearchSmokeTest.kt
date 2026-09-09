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
 * Smoke Compose : Home → Search (harness [SmokeNavGraph]).
 */
@RunWith(AndroidJUnit4::class)
class SearchSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_to_search_isDisplayed() {
        composeRule.setContent { SmokeNavGraph() }

        composeRule.onNodeWithTag("smoke_home").assertIsDisplayed()
        composeRule.onNodeWithTag("smoke_open_search").performClick()

        composeRule.onNodeWithTag("smoke_search").assertIsDisplayed()
        composeRule.onNodeWithTag("smoke_search_back").performClick()
        composeRule.onNodeWithTag("smoke_home").assertIsDisplayed()
    }
}
