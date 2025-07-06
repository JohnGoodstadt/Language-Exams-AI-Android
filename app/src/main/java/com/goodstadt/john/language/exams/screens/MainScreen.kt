package com.goodstadt.john.language.exams.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.navigation.Screen
import com.goodstadt.john.language.exams.navigation.bottomNavItems
import com.goodstadt.john.language.exams.screens.shared.TabWithHorizontalMenu
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import com.goodstadt.john.language.exams.viewmodels.TabsViewModel
import com.goodstadt.john.language.exams.viewmodels.VocabDataUiState
import androidx.navigation.compose.NavHost
import com.goodstadt.john.language.exams.navigation.MeScreen
import com.goodstadt.john.language.exams.screens.me.MeTabContainerScreen
//import com.goodstadt.john.language.exams.screens.me.MeTabMenuScreen
import com.goodstadt.john.language.exams.screens.me.MeTabPlaceholderScreen

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val tabsViewModel: TabsViewModel = hiltViewModel()

    Scaffold(
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title, maxLines = 1, textAlign = TextAlign.Center) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                        )
                    }
                }
            }
    ) { innerPadding ->
        NavHost(
                navController,
                startDestination = Screen.Tab1.route,
                Modifier.padding(innerPadding)
        ) {
            // --- Reusable Logic for Tabs 1, 2, 3 ---
            composable(Screen.Tab1.route) {
                val uiState by tabsViewModel.vocabUiState.collectAsState()
                val categories by tabsViewModel.tab1Categories.collectAsState()
                val menuItems by tabsViewModel.tab1MenuItems.collectAsState()
                val indexMap by tabsViewModel.tab1CategoryIndexMap.collectAsState()
                // Collect the playback state here, where tabsViewModel is known
                val playbackState by tabsViewModel.playbackState.collectAsState()

                RenderCategoryTab(
                        uiState = uiState,
                        menuItems = menuItems,
                        categories = categories,
                        categoryIndexMap = indexMap,
                        playbackState = playbackState, // Pass the state down
                        onRowTapped = { word, sentence -> tabsViewModel.playTrack(word, sentence) } // Pass the action down
                )
            }

            composable(Screen.Tab2.route) {
                val uiState by tabsViewModel.vocabUiState.collectAsState()
                val categories by tabsViewModel.tab2Categories.collectAsState()
                val menuItems by tabsViewModel.tab2MenuItems.collectAsState()
                val indexMap by tabsViewModel.tab2CategoryIndexMap.collectAsState()
                // Collect the playback state here, where tabsViewModel is known
                val playbackState by tabsViewModel.playbackState.collectAsState()

                RenderCategoryTab(
                        uiState = uiState,
                        menuItems = menuItems,
                        categories = categories,
                        categoryIndexMap = indexMap,
                        playbackState = playbackState, // Pass the state down
                        onRowTapped = { word, sentence -> tabsViewModel.playTrack(word, sentence) } // Pass the action down
                )
            }

            composable(Screen.Tab3.route) {
                val uiState by tabsViewModel.vocabUiState.collectAsState()
                val categories by tabsViewModel.tab3Categories.collectAsState()
                val menuItems by tabsViewModel.tab3MenuItems.collectAsState()
                val indexMap by tabsViewModel.tab3CategoryIndexMap.collectAsState()
                // Collect the playback state here, where tabsViewModel is known
                val playbackState by tabsViewModel.playbackState.collectAsState()

                RenderCategoryTab(
                        uiState = uiState,
                        menuItems = menuItems,
                        categories = categories,
                        categoryIndexMap = indexMap,
                        playbackState = playbackState, // Pass the state down
                        onRowTapped = { word, sentence -> tabsViewModel.playTrack(word, sentence) } // Pass the action down
                )
            }
            // --- End of Reusable Logic ---

            composable(Screen.Tab4.route) {
                Tab4Screen()
            }

            composable(Screen.Tab5.route) {
                MeTabContainerScreen()
            }
        }
    }
}

@Composable
fun Tab4Screen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Hello, World!", style = MaterialTheme.typography.headlineMedium)
    }
}

// Add this function inside MainScreen.kt, below the MainScreen composable

/**
 * A helper composable that handles the loading/error/success states
 * and renders the CategoryTabScreen. This avoids duplicating the `when` statement.
 */
@Composable
private fun RenderCategoryTab(
    uiState: VocabDataUiState,
    menuItems: List<String>,
    categories: List<Category>,
    categoryIndexMap: Map<String, Int>,
        // --- ADD THIS NEW PARAMETER ---
    playbackState: PlaybackState,
        // --- ADD THIS NEW PARAMETER ---
    onRowTapped: (word: VocabWord, sentence: Sentence) -> Unit
) {

    when (uiState) {
        is VocabDataUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is VocabDataUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: ${uiState.message}", color = Color.Red)
            }
        }
        is VocabDataUiState.Success -> {
            CategoryTabScreen(
                    menuItems = menuItems,
                    categories = categories,
                    categoryIndexMap = categoryIndexMap,
                    // Pass the new parameters down to the next screen
                    playbackState = playbackState,
                    onRowTapped = onRowTapped
            )
        }
    }
}