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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.navigation.IconResource
import com.goodstadt.john.language.exams.navigation.Screen
import com.goodstadt.john.language.exams.navigation.bottomNavItems
import com.goodstadt.john.language.exams.screens.me.MeTabContainerScreen
import com.goodstadt.john.language.exams.screens.recall.RecallScreen
import com.goodstadt.john.language.exams.viewmodels.AuthUiState
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import com.goodstadt.john.language.exams.viewmodels.TabsViewModel
//import com.goodstadt.john.language.exams.viewmodels.VocabDataUiState

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val tabsViewModel: TabsViewModel = hiltViewModel()
    val globalUiState by tabsViewModel.uiState.collectAsState()
   // val selectedVoiceName by tabsViewModel.selectedVoiceName.collectAsState()

    val authState = globalUiState.authState

    when (authState) {
        is AuthUiState.Loading -> {
            // Show a full-screen loading indicator while Firebase connects
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text(modifier = Modifier.padding(top = 60.dp), text = "Connecting...")
            }
        }
        is AuthUiState.Error -> {
            // Show an error message if sign-in fails
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: ${(authState as AuthUiState.Error).message}", color = Color.Red)
            }
        }
        is AuthUiState.Success -> {
            // Once successful, show the main app content
            // The entire Scaffold and NavHost goes inside here
            MainAppContent(
                navController = navController,
                selectedVoiceName = globalUiState.selectedVoiceName
            )
        }
    }

}
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainAppContent(navController: NavHostController, selectedVoiceName: String) {

    //val selectedVoiceName by tabsViewModel.selectedVoiceName.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            when (val icon = screen.icon) {
                                is IconResource.VectorIcon -> {
                                    Icon(
                                        imageVector = icon.imageVector,
                                        contentDescription = screen.title
                                    )
                                }
                                is IconResource.DrawableIcon -> {
                                    Icon(
                                        painter = painterResource(id = icon.id),
                                        contentDescription = screen.title
                                    )
                                }
                            }
                        },
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
        // ... inside MainAppContent function ...

        NavHost(
            navController,
            startDestination = Screen.Tab1.route,
            Modifier.padding(innerPadding)
        ) {

            // --- THE NEW, SIMPLIFIED NAVIGATION ---

            composable(Screen.Tab1.route) {
                // Hilt automatically provides a unique instance of CategoryTabViewModel
                // scoped to this specific destination in the navigation graph.
                CategoryTabScreen(
                    tabIdentifier = "tab1", // Tell the screen which tab it is
                    selectedVoiceName = selectedVoiceName
                )
            }

            composable(Screen.Tab2.route) {
                // Hilt provides another, separate instance of the ViewModel for this screen.
                CategoryTabScreen(
                    tabIdentifier = "tab2",
                    selectedVoiceName = selectedVoiceName
                )
            }

            composable(Screen.Tab3.route) {
                CategoryTabScreen(
                    tabIdentifier = "tab3",
                    selectedVoiceName = selectedVoiceName
                )
            }

            composable(Screen.Tab4.route) {
                RecallScreen() // This was already correct
            }

            composable(Screen.Tab5.route) {
                MeTabContainerScreen()
            }
        }
    }

// You can now DELETE the RenderCategoryTab composable entirely, as it's no longer needed.
// The NavHost now calls CategoryTabScreen directly.

}

