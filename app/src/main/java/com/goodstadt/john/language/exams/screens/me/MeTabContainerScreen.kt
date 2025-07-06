package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.goodstadt.john.language.exams.navigation.MeScreen
import com.goodstadt.john.language.exams.navigation.getMeScreenRouteFromTitle
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.viewmodels.TabsViewModel
import com.goodstadt.john.language.exams.screens.me.SearchScreen
/**
 * This is the main container for the entire "Me" tab. It sets up the persistent
 * horizontal menu and a NavHost below it to display the content for the selected item.
 */
@Composable
fun MeTabContainerScreen(viewModel: TabsViewModel = hiltViewModel()) {
    // 1. Create the NavController that will manage the content area below the menu.
    val meTabNavController = rememberNavController()
    val menuItems by viewModel.meTabMenuItems.collectAsState()

    // 2. Use a Column to stack the menu on top of the content area.
    Column(modifier = Modifier.fillMaxSize()) {
        // --- Part A: The Persistent Horizontal Menu ---
        LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(menuItems) { title ->
                MenuItemChip(
                        text = title,
                        onClick = {
                            getMeScreenRouteFromTitle(title)?.let { route ->
                                // Navigate the inner NavController.
                                // We add launchSingleTop = true to avoid building up a large
                                // back stack if the user taps the same item repeatedly.
                                meTabNavController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                )
            }
        }

        // --- Part B: The NavHost that displays the content ---
        // This NavHost takes up the remaining space in the Column.
        NavHost(
                navController = meTabNavController,
                // The start destination can be a "home" screen or an empty placeholder.
                startDestination = MeScreen.MeRoot.route,
                modifier = Modifier.weight(1f) // Ensures it fills the rest of the space
        ) {
            // Define a destination for the initial, empty state
            composable(MeScreen.MeRoot.route) {
                // This is the content shown BEFORE any menu item is tapped.
                // It can be blank or show a welcome message.
                Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Text("Select an option from the menu above.")
                }
            }
            // Define a destination for each possible screen
            composable(MeScreen.Settings.route) { MeTabPlaceholderScreen("Settings") }
//            composable(MeScreen.Search.route) { MeTabPlaceholderScreen("Search") }
            composable(MeScreen.Search.route) { SearchScreen() }
            composable(MeScreen.Quiz.route) { MeTabPlaceholderScreen("Quiz") }
            composable(MeScreen.Progress.route) { MeTabPlaceholderScreen("Progress") }
            composable(MeScreen.Conjugations.route) { ConjugationsScreen() }
            composable(MeScreen.Prepositions.route) { MeTabPlaceholderScreen("Prepositions") }
            composable(MeScreen.Paragraph.route) { MeTabPlaceholderScreen("Paragraph") }
            composable(MeScreen.Conversation.route) { MeTabPlaceholderScreen("Conversation") }
        }
    }
}

/**
 * A reusable placeholder screen for any destination within the "Me" tab.
 */
@Composable
fun MeTabPlaceholderScreen(title: String) {
    Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
    ) {
        Text("Hello, World! from $title Screen")
    }
}