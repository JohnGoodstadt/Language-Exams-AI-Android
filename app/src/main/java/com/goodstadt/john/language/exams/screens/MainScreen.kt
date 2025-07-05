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
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.goodstadt.john.language.exams.navigation.Screen
import com.goodstadt.john.language.exams.navigation.bottomNavItems
import com.goodstadt.john.language.exams.screens.shared.TabWithHorizontalMenu
import com.goodstadt.john.language.exams.viewmodels.TabsViewModel
import com.goodstadt.john.language.exams.viewmodels.VocabDataUiState

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
            composable(Screen.Tab1.route) {
                // --- NEW LOGIC FOR TAB 1 ---
                val uiState by tabsViewModel.vocabUiState.collectAsState()
                val tab1Categories by tabsViewModel.tab1Categories.collectAsState()
                val tab1MenuItems by tabsViewModel.tab1MenuItems.collectAsState()
                // --- NEW: Get the index map state ---
                val categoryIndexMap by tabsViewModel.categoryIndexMap.collectAsState()

                when (val state = uiState) {
                    is VocabDataUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is VocabDataUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "Error: ${state.message}")
                        }
                    }
                    is VocabDataUiState.Success -> {
                        // If data is loaded successfully, show the main content
                        CategoryTabScreen(
                                menuItems = tab1MenuItems,
                                categories = tab1Categories,
                                categoryIndexMap = categoryIndexMap
                        )
                    }
                }
                // --- END NEW LOGIC FOR TAB 1 ---
            }
            composable(Screen.Tab2.route) {
                val menuItems by tabsViewModel.tab2MenuItems.collectAsState()
                TabWithHorizontalMenu(menuItems = menuItems)
            }
            composable(Screen.Tab3.route) {
                val menuItems by tabsViewModel.tab3MenuItems.collectAsState()
                TabWithHorizontalMenu(menuItems = menuItems)
            }
            composable(Screen.Tab4.route) {
                Tab4Screen()
            }
            composable(Screen.Tab5.route) {
                val menuItems by tabsViewModel.tab5MenuItems.collectAsState()
                TabWithHorizontalMenu(menuItems = menuItems)
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