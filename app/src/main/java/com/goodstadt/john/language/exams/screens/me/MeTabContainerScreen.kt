package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.navigation.MeScreen
import com.goodstadt.john.language.exams.navigation.getMeScreenRouteFromTitle
import com.goodstadt.john.language.exams.screens.CategoryTabScreen
import com.goodstadt.john.language.exams.screens.GeminiExampleScreen
import com.goodstadt.john.language.exams.screens.ParagraphScreen
import com.goodstadt.john.language.exams.screens.recall.RecallScreen
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.viewmodels.CategoryTabViewModel
import com.goodstadt.john.language.exams.viewmodels.MeTabViewModel
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import kotlinx.coroutines.launch

/**
 * This is the main container for the entire "Me" tab. It sets up the persistent
 * horizontal menu and a NavHost below it to display the content for the selected item.
 */
@OptIn(ExperimentalMaterialNavigationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MeTabContainerScreen(viewModel: MeTabViewModel = hiltViewModel()) {

    // --- THIS IS THE CORRECTED LOGIC ---
    // 1. Get an instance of the main TabsViewModel. Hilt will correctly scope this
    //    to the parent navigation graph (the main NavHost) automatically.
    //    The complex 'findActivity' logic is not needed here.
    //val tabsViewModel: TabsViewModel = hiltViewModel()
    // --- END OF CORRECTION ---

//    val bottomSheetNavigator = rememberBottomSheetNavigator()


    val meTabNavController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true // This is the correct M3 parameter
    )
    val scope = rememberCoroutineScope()
    val isSheetVisible = uiState.selectedCategoryTitle != null



    LaunchedEffect(isSheetVisible) {
        if (!isSheetVisible) {
            scope.launch { sheetState.hide() }.join()
        }
    }

    // val menuItems by tabsViewModel.meTabMenuItems.collectAsState()
    val menuItems = LanguageConfig.meTabMenuItems
    // --- THIS IS THE KEY ---
    // 1. Observe the back stack of the NESTED NavController.
    val navBackStackEntry by meTabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

//    ModalBottomSheetLayout(bottomSheetNavigator = bottomSheetNavigator) {

    var selectedChipTitle by remember(menuItems) {
        mutableStateOf(menuItems.firstOrNull() ?: "")
    }
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
                    isSelected =  (title == selectedChipTitle),
                    onClick = {
                        selectedChipTitle = title
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
            startDestination = MeScreen.Focusing.route,
            modifier = Modifier.weight(1f)
        ) {
            composable(MeScreen.MeRoot.route) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Select an option from the menu above.")
                }
            }
            // All the screen destinations remain the same
            composable(MeScreen.Focusing.route) { RecallScreen() }
            composable(MeScreen.Settings.route) { SettingsScreen() }
            composable(MeScreen.Search.route) { SearchScreen() }

            composable(MeScreen.Progress.route) {
                ProgressMapScreen(
                    //activeRoute = currentRoute,
                    // --- Add a callback for when a tile is tapped ---
                    onTileTapped = { categoryTitle ->
                        viewModel.onTileTapped(categoryTitle)
                    }
                )
            }
            composable(MeScreen.Paragraph.route) { ParagraphScreen() }
        }
    } //:Column

    // --- The M3 ModalBottomSheet ---
    // This is placed at the end so it draws on top of everything else.
    if (isSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onSheetDismissed() },
            sheetState = sheetState
        ) {
            ProgressDetailView(
                title = uiState.selectedCategoryTitle!!, // The title from the parent screen
                selectedVoiceName =  uiState.currentVoiceName
            )
        }
    }
//    }
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
@Composable
fun ProgressDetailView(title: String, selectedVoiceName: String) {
    // It's just a wrapper around your super-flexible CategoryTabScreen!
    val viewModel: CategoryTabViewModel = hiltViewModel(key = title)

    CategoryTabScreen(
        categoryTitle = title, // <-- Provide the category title
        selectedVoiceName = selectedVoiceName,
        viewModel = viewModel
    )
}