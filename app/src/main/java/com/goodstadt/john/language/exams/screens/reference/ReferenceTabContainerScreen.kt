package com.goodstadt.john.language.exams.screens.reference

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
import com.goodstadt.john.language.exams.navigation.RefScreen
import com.goodstadt.john.language.exams.navigation.getRefScreenRouteFromTitle
import com.goodstadt.john.language.exams.screens.CategoryTabScreen
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
fun ReferenceTabContainerScreen(viewModel: MeTabViewModel = hiltViewModel()) {

    // --- THIS IS THE CORRECTED LOGIC ---
    // 1. Get an instance of the main TabsViewModel. Hilt will correctly scope this
    //    to the parent navigation graph (the main NavHost) automatically.
    //    The complex 'findActivity' logic is not needed here.
    //val tabsViewModel: TabsViewModel = hiltViewModel()
    // --- END OF CORRECTION ---

//    val bottomSheetNavigator = rememberBottomSheetNavigator()


    val refTabNavController = rememberNavController()
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
    val menuItems = LanguageConfig.refTabMenuItems
    // --- THIS IS THE KEY ---
    // 1. Observe the back stack of the NESTED NavController.
    val navBackStackEntry by refTabNavController.currentBackStackEntryAsState()
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
                        getRefScreenRouteFromTitle(title)?.let { route ->
                            refTabNavController.navigate(route) {
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }

        // Part B: The NavHost that displays the content
        NavHost(
            navController = refTabNavController,
            startDestination = RefScreen.Quiz.route,
//                    startDestination = "progress_detail/Initial",
            modifier = Modifier.weight(1f)
        ) {
            composable(RefScreen.RefRoot.route) {
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
//            composable(RefScreen.Settings.route) { SettingsScreen() }
//            composable(RefScreen.Search.route) { SearchScreen() }
//
//            composable(RefScreen.Progress.route) {
//                ProgressMapScreen(
//                    //activeRoute = currentRoute,
//                    // --- Add a callback for when a tile is tapped ---
//                    onTileTapped = { categoryTitle ->
//                        viewModel.onTileTapped(categoryTitle)
////                            val encodedTitle = categoryTitle.urlEncode()
////                          meTabNavController.navigate("progress_detail/$encodedTitle")
////                            Timber.d("Attempting to navigate to: progress_detail/Test")
//                    }
//                )
//            }
//            composable(RefScreen.Paragraph.route) { ParagraphScreen() }
            composable(RefScreen.Quiz.route) { QuizScreen() }
            composable(RefScreen.Conjugations.route) { ConjugationsScreen() }
            composable(RefScreen.Prepositions.route) { PrepositionsScreen() }

//            composable(RefScreen.Paragraph.route) { GeminiExampleScreen() }
//            composable(RefScreen.Conversation.route) { MeTabPlaceholderScreen("Conversation") }
        }
    } //:Column

    // --- The M3 ModalBottomSheet ---
    // This is placed at the end so it draws on top of everything else.
    if (isSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onSheetDismissed() },
            sheetState = sheetState
        ) {
            RefProgressDetailView(
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
fun ReferenceTabPlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Hello, World! from $title Screen")
    }
}
@Composable
fun RefProgressDetailView(title: String, selectedVoiceName: String) {
    // It's just a wrapper around your super-flexible CategoryTabScreen!
    val viewModel: CategoryTabViewModel = hiltViewModel(key = title)

    CategoryTabScreen(
        categoryTitle = title, // <-- Provide the category title
        selectedVoiceName = selectedVoiceName,
        viewModel = viewModel
    )
}