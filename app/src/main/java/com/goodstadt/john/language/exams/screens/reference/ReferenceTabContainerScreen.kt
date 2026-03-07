package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goodstadt.john.language.exams.models.ScreenType
import com.goodstadt.john.language.exams.navigation.QUIZ_DETAIL_ROUTE
import com.goodstadt.john.language.exams.navigation.RefScreen
import com.goodstadt.john.language.exams.screens.reference.VocabQuiz.CategoryDetailScreen
import com.goodstadt.john.language.exams.screens.reference.VocabQuiz.QuizSet
import com.goodstadt.john.language.exams.screens.reference.VocabQuiz.VocabDashboardScreen
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.utils.findActivity
import com.goodstadt.john.language.exams.viewmodels.ReferenceViewModel
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * This is the main container for the entire "Me" tab. It sets up the persistent
 * horizontal menu and a NavHost below it to display the content for the selected item.
 */

@OptIn(ExperimentalMaterialNavigationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReferenceTabContainerScreen(viewModel: ReferenceViewModel = hiltViewModel()) {

    val refTabNavController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSheetVisible = uiState.selectedCategoryTitleForSheet != null


    // This LaunchedEffect for hiding the sheet remains the same
    val scope = rememberCoroutineScope()
    LaunchedEffect(isSheetVisible) {
        if (!isSheetVisible) {
            scope.launch { sheetState.hide() }.join()
        }
    }
    // Perform the navigation
    val context = LocalContext.current
    val activity = LocalContext.current.findActivity() as? androidx.activity.ComponentActivity
    val navViewModel: NavigationViewModel = if (activity != null) {
        hiltViewModel(activity)
    } else {
        hiltViewModel() // Fallback (though this branch shouldn't happen in valid UI)
    }

    // ✅ 2. React to the pending navigation
    LaunchedEffect(Unit) {
        val stickyTarget = navViewModel.pendingReferenceTabId
        if (stickyTarget != null) {
            viewModel.onTabSelected(stickyTarget)
            navViewModel.pendingReferenceTabId = null // Clear it
        }

        navViewModel.navigationEvent.collect { target ->
            viewModel.checkDeepLink(target)
        }
    }

    // --- Navigation Logic ---
    // This LaunchedEffect now uses the merged uiState
    // ✅ MODIFIED: The LaunchedEffect now performs the routing based on screenType
    LaunchedEffect(uiState.selectedTabId) {

        // Find the full DisplayTab object for the currently selected ID.
        val selectedTab = uiState.tabs.firstOrNull { it.id == uiState.selectedTabId }
            ?: return@LaunchedEffect // If no tab is selected, do nothing.

        // Get the detailed definition for the selected tab.
        val definition = selectedTab.definition

        // This `when` statement is the router. It builds the correct navigation route
        // based on the `screenType` provided by your Remote Config.
        val route: String? = when (definition.screenType) {

            ScreenType.FIXED_SCREEN -> {
                // For fixed screens (Quiz, Conjugations), the route is simply the tab's ID.
                selectedTab.id
            }

            ScreenType.VOCAB_SCREEN -> {
                // For a generic vocab screen, create the route with its documentId.
                definition.firestoreDocumentId?.let { docId ->
                    RefScreen.DynamicSheet.createRoute(docId)
                }
            }

            ScreenType.GROUPED_VOCAB_SCREEN -> {
                // For a grouped screen, create the route with its parent tabId.
                RefScreen.GroupedSheet.createRoute(selectedTab.id)
            }

            ScreenType.FORMAT_1_SCREEN -> definition.firestoreDocumentId?.let { docId ->
                RefScreen.Format1.createRoute(docId)
            }

            ScreenType.GRAMMAR_SCREEN -> {
                Timber.w("Dummy Data Type")
                null
            }

            ScreenType.FORMAT_2_SCREEN -> definition.firestoreDocumentId?.let { docId ->
                RefScreen.Format2.createRoute(docId)
            }
            ScreenType.GROUPED_FORMAT_2_SCREEN -> {
                // We route to a new destination, passing the Parent Tab ID
                RefScreen.GroupedFormat2.createRoute(selectedTab.id)
            }


            ScreenType.FORMAT_3_SCREEN  -> definition.firestoreDocumentId?.let { docId ->
                RefScreen.Format3.createRoute(docId)
            }
            ScreenType.GROUPED_FORMAT_3_SCREEN -> {
                // We route to a new destination, passing the Parent Tab ID
                RefScreen.GroupedFormat3.createRoute(selectedTab.id)
            }

            // The 'else' is not needed because 'when' on an enum is exhaustive.
            // If you add a new ScreenType to the enum, the compiler will force you to handle it here.
            ScreenType.UNKNOWN -> null
//            ScreenType.DICTIONARY -> null
//            ScreenType.DAILY_WORD -> null
            ScreenType.VOCAB_DASHBOARD -> {
                RefScreen.VocabQuizDashboard.route
            }
        }

        // If a valid route was determined, perform the navigation.
        route?.let {
            refTabNavController.navigate(it) {
                // This standard navigation option block prevents a growing back stack
                // when the user is tapping between the main tabs.
                popUpTo(refTabNavController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Part A: The Dynamic Horizontal Menu (now uses the single uiState)
        if (uiState.tabs.isNotEmpty()) {
            LazyRow(/*...*/) {
                items(uiState.tabs, key = { it.id }) { tab ->
                    MenuItemChip(
                        text = tab.definition.title,
                        isSelected = (tab.id == uiState.selectedTabId),
                        onClick = { viewModel.onTabSelected(tab.id) }
                    )
                }
            }
        }

        if (uiState.tabs.isNotEmpty()) {
            // Part B: The Dynamic NavHost (now uses the single uiState)
            val selectedTab = uiState.tabs.firstOrNull { it.id == uiState.selectedTabId }
            if (selectedTab?.definition?.screenType == ScreenType.UNKNOWN) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Content Not Available",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "This feature may require an app update.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                NavHost(
                    navController = refTabNavController,
                    startDestination = uiState.tabs.first().id,
                    modifier = Modifier.weight(1f)

                ) {

                    composable(RefScreen.Quiz.route) {
                        UsageQuizScreen()
                    }//Existing Quiz


                    composable(
                        route = QUIZ_DETAIL_ROUTE,
                        arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val catId = backStackEntry.arguments?.getString("categoryId") ?: ""

                        // Dummy Logic to find the title based on ID
                        val displayTitle = catId.replaceFirstChar { it.uppercase() }

                        // Dummy Data for the sets in this category
                        val dummySets = remember(catId) {
                            listOf(
                                QuizSet("1", "Set 1 of 3", "Ambitious, Reliable...", 10, 10),
                                QuizSet("2", "Set 2 of 3", "Sustainable, Capable...", 4, 10),
                                QuizSet("3", "Set 3 of 3", "Efficient, Logic...", 0, 10)
                            )
                        }

                        CategoryDetailScreen(
                            categoryTitle = displayTitle,
                            quizSets = dummySets,
                            onBackClick = { refTabNavController.popBackStack() },
                            onStartSet = { selectedSet ->
                                Timber.i("Launching actual quiz for ${selectedSet.id}")
                                // Later: refTabNavController.navigate("actual_quiz/${selectedSet.id}")
                            }
                        )
                    }

                    composable(RefScreen.VocabQuizDashboard.route) {
                        VocabDashboardScreen(
                            onNavigateToQuiz = { categoryTitle ->
                                if (categoryTitle == null) {
                                    // 1. "Revi ew Now" (Mixed Quiz) clicked
                                    // Navigate to the main Quiz tab
//                                    refTabNavController.navigate(RefScreen.Quiz.route)
                                    Timber.i("No category to review")
                                } else {
                                    Timber.i("User wants to review: $categoryTitle")
                                }
                            }
                        )
                    }

                    composable(RefScreen.Conjugations.route) { ConjugationsScreen() }

                    // 2. Destination for `ScreenType.VOCAB_SCREEN`
                    composable(
                        route = RefScreen.DynamicSheet.route,
                        arguments = listOf(navArgument("documentId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        ReferenceGenericScreen()
                    }

                    // 3. Destination for `ScreenType.GROUPED_VOCAB_SCREEN`
                    composable(
                        route = RefScreen.GroupedSheet.route,
                        arguments = listOf(navArgument("tabId") { type = NavType.StringType })
                    ) {
                        // Hilt automatically passes the 'tabId' to the GroupedSheetViewModel.
                        GroupedSheetScreen()
                    }

                    composable(
                        route = RefScreen.Format1.route,
                        arguments = listOf(navArgument("documentId") { type = NavType.StringType })
                    ) {
                        // 1. Create an instance of the specific ViewModel for this screen using Hilt.
                        //    Hilt will automatically provide it with the `documentId` via SavedStateHandle.
                        val viewModel: Format1ViewModel = hiltViewModel()

                        // 2. Collect the UI state FROM THAT SPECIFIC VIEWMODEL.
                        val uiState by viewModel.uiState.collectAsState()

                        // 3. Use a 'when' block to display the correct UI based on the ViewModel's state.
                        when (val state = uiState) {
                            is Format1UiState.Loading -> {
                                // Show a loading indicator while the Format1ViewModel is fetching data.
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            is Format1UiState.Success -> {
                                // When the data is successfully loaded, display your Format1Screen
                                // and pass it the data from the Success state object.
//                                Format1BScreen(data = state.data)
                                Format1Screen()
                            }

                            is Format1UiState.Error -> {
                                // If there was an error, display the error message.
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    composable(
                        route = RefScreen.Format2.route,
                        arguments = listOf(navArgument("documentId") { type = NavType.StringType })
                    ) {
                        // 1. Create an instance of the specific ViewModel for THIS screen (Format2ViewModel).
                        //    Hilt automatically provides the `documentId` to it via SavedStateHandle.
                        val format2ViewModel: Format2ViewModel = hiltViewModel()

                        // 2. Collect the UI state FROM THE FORMAT2VIEWMODEL.
                        val uiStateFormat2 by format2ViewModel.uiState.collectAsState()

                        // 3. Use a 'when' block to display the UI based on the Format2ViewModel's state.
                        when (val state = uiStateFormat2) {
                            is Format2UiState.Loading -> {
                                // Show a loading indicator while the Format2ViewModel is fetching data.
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            is Format2UiState.Success -> {
                                Format2Screen(
                                    title = state.format2File.title,
                                    description = state.format2File.description,
                                    levels = state.format2File.data,
                                )


                            }

                            is Format2UiState.Error -> {
                                // If there was an error, display the error message.
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    composable(
                        route = RefScreen.GroupedFormat2.route, // "grouped_format2/{tabId}"
                        arguments = listOf(navArgument("tabId") { type = NavType.StringType })
                    ) {
                        // This is the container screen we just created/discussed
                        Format2GroupedScreen()
                    }

                    composable(
                        route = RefScreen.GroupedFormat3.route,
                        arguments = listOf(navArgument("tabId") { type = NavType.StringType })
                    ) {
                        Format3GroupedScreen()
                    }
                }
            } //: Not Unknown type
        } //: is not empty
        else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }


    }//: Column


} //:Fun

