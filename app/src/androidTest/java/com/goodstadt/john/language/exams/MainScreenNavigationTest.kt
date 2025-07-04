// <project-root>/app/src/androidTest/java/com/goodstadt/john/language/exams/MainScreenNavigationTest.kt
package com.goodstadt.john.language.exams

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MainScreenNavigationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavBar_displaysAllTabs() {
        composeTestRule.onNodeWithText("Tab 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tab 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tab 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Focussing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Me").assertIsDisplayed()
    }

    @Test
    fun navigateToTab4_showsHelloWorld() {
        // Click on the "Focussing" tab
        composeTestRule.onNodeWithText("Focussing").performClick()

        // Verify that the content for Tab 4 is displayed
        composeTestRule.onNodeWithText("Hello, World!").assertIsDisplayed()
    }

    @Test
    fun tab1_showsHorizontalMenu() {
        // Tab 1 is the start destination
        composeTestRule.onNodeWithText("Personal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shopping").assertIsDisplayed()
    }
}