package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.layout.*
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.navigation.MeScreen
import com.goodstadt.john.language.exams.navigation.getMeScreenRouteFromTitle
import com.goodstadt.john.language.exams.screens.ParagraphScreen
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.viewmodels.TabsViewModel

/**
 * This is the main container for the entire "Me" tab. It sets up the persistent
 * horizontal menu and a NavHost below it to display the content for the selected item.
 */
@Composable
fun MeTabContainerScreen() {
    // --- THIS IS THE CORRECTED LOGIC ---
    // 1. Get an instance of the main TabsViewModel. Hilt will correctly scope this
    //    to the parent navigation graph (the main NavHost) automatically.
    //    The complex 'findActivity' logic is not needed here.
    val tabsViewModel: TabsViewModel = hiltViewModel()
    // --- END OF CORRECTION ---

    val meTabNavController = rememberNavController()
   // val menuItems by tabsViewModel.meTabMenuItems.collectAsState()
    val menuItems = LanguageConfig.meTabMenuItems
//    val menuItems = listOf(
//        "Settings",
//        "Search",
//        "Quiz",
//        "Progress",
//        "Conjugations",
//        "Prepositions",
//        "Paragraph",
//        "Conversation"
//    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Part A: The Persistent Horizontal Menu
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
                                meTabNavController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                )
            }
        }

        // Part B: The NavHost that displays the content
        NavHost(
                navController = meTabNavController,
                startDestination = MeScreen.Settings.route,
                modifier = Modifier.weight(1f)
        ) {
            composable(MeScreen.MeRoot.route) {
                Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Text("Select an option from the menu above.")
                }
            }
            // All the screen destinations remain the same
            composable(MeScreen.Settings.route) { SettingsScreen() }
            composable(MeScreen.Search.route) { SearchScreen() }
            //composable(MeScreen.Quiz.route) { MeTabPlaceholderScreen("Quiz") }

            composable(MeScreen.Quiz.route) { QuizScreen() }

            composable(MeScreen.Progress.route) { MeTabPlaceholderScreen("Progress") }
            composable(MeScreen.Conjugations.route) { ConjugationsScreen() }
            composable(MeScreen.Prepositions.route) { PrepositionsScreen() }
           // composable(MeScreen.Paragraph.route) { MeTabPlaceholderScreen("Paragraph") }
            composable(MeScreen.Paragraph.route) { ParagraphScreen() }

            composable(MeScreen.Conversation.route) { MeTabPlaceholderScreen("Conversation") }
        }
    }
}
/**
 * A reusable placeholder screen for any destination within the "Me" tab.
 * THIS IS THE FUNCTION THAT WAS MISSING.
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