package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.navigation.RefScreen
import com.goodstadt.john.language.exams.navigation.getRefScreenRouteFromTitle
import com.goodstadt.john.language.exams.screens.CategoryTabScreen
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.viewmodels.CategoryTabViewModel
import com.goodstadt.john.language.exams.viewmodels.ReferenceTabViewModel
import com.goodstadt.john.language.exams.viewmodels.ReferenceViewModel
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import kotlinx.coroutines.launch

/**
 * This is the main container for the entire "Me" tab. It sets up the persistent
 * horizontal menu and a NavHost below it to display the content for the selected item.
 */
@OptIn(ExperimentalMaterialNavigationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReferenceTabContainerScreen(viewModel: ReferenceViewModel = hiltViewModel())
{

    val refTabNavController = rememberNavController()
    // MODIFIED: We only have ONE uiState to collect now
    val uiState by viewModel.uiState.collectAsState()

    // --- Bottom Sheet Logic ---
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // MODIFIED: Use the merged state to determine if the sheet is visible
    val isSheetVisible = uiState.selectedCategoryTitleForSheet != null
    // This LaunchedEffect for hiding the sheet remains the same
    val scope = rememberCoroutineScope()
    LaunchedEffect(isSheetVisible) {
        if (!isSheetVisible) {
            scope.launch { sheetState.hide() }.join()
        }
    }
        // Perform the navigation

    // --- Navigation Logic ---
    // This LaunchedEffect now uses the merged uiState
    LaunchedEffect(uiState.selectedTabId) {
        val selectedTab = uiState.tabs.firstOrNull { it.id == uiState.selectedTabId }
            ?: return@LaunchedEffect

        val route = when (selectedTab.type) {
            "fixed_view" -> getRefScreenRouteFromTitle(selectedTab.title)
            "dynamic_sheet" -> selectedTab.firestoreDocumentId?.let { docId ->
                RefScreen.DynamicSheet.createRoute(docId)
            }
            else -> null
        }

        route?.let {
            refTabNavController.navigate(it) {
                popUpTo(refTabNavController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Part A: The Dynamic Horizontal Menu (now uses the single uiState)
        if (uiState.tabs.isNotEmpty()) {
            LazyRow(/*...*/) {
                items(uiState.tabs, key = { it.id }) { tab ->
                    MenuItemChip(
                        text = tab.title,
                        isSelected = (tab.id == uiState.selectedTabId),
                        onClick = { viewModel.onTabSelected(tab.id) }
                    )
                }
            }
        }

        // Part B: The Dynamic NavHost (now uses the single uiState)
        NavHost(
            navController = refTabNavController,
            startDestination = getRefScreenRouteFromTitle(uiState.tabs.firstOrNull()?.title ?: "") ?: RefScreen.Quiz.route,
            modifier = Modifier.weight(1f)
        ) {

            composable(RefScreen.Quiz.route) { QuizScreen() }
            composable(RefScreen.Conjugations.route) { ConjugationsScreen() }
            composable(RefScreen.Prepositions.route) { PrepositionsScreen() }

            composable(
                // 1. The route definition from your RefScreen sealed class
                route = RefScreen.DynamicSheet.route,

                // 2. Define the arguments this route expects. This MUST match the route string.
                arguments = listOf(
                    navArgument("documentId") {
                        type = NavType.StringType
                        // You could add nullable = false if it's always required
                    }
                )
            ) { backStackEntry ->
                // 3. Extract the documentId safely from the navigation arguments
                val documentId = backStackEntry.arguments?.getString("documentId")

                // 4. Call your generic screen, handling the case where the ID might be null
                if (documentId != null) {
                    // This is the new, reusable screen that will display the content
                    //GenericVocabScreen(firestoreDocumentId = documentId)
                    ReferenceGenericScreen()
                } else {
                    // Display an error if for some reason the ID is missing
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Error: Document ID was not provided.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

        }
    } //:Column



    if (isSheetVisible) {
        ModalBottomSheet(
            // MODIFIED: Use the merged viewModel for the dismiss callback
            onDismissRequest = { viewModel.onSheetDismissed() },
            sheetState = sheetState
        ) {
            RefProgressDetailView(
                // MODIFIED: Use the merged uiState for the title and voice name
                title = uiState.selectedCategoryTitleForSheet!!,
                selectedVoiceName = uiState.currentVoiceName
            )
        }
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
@Composable
fun GenericVocabScreen(firestoreDocumentId: String) {
    // TODO: Create a ViewModel for this screen that takes the documentId,
    // fetches the data from Firestore, and displays it.
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "This is the generic screen.\nIt should now load data from Firestore for document:\n\n'$firestoreDocumentId'",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}