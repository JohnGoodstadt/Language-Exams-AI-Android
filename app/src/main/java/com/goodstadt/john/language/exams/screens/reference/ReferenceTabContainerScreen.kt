package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import timber.log.Timber

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
    // ✅ MODIFIED: The LaunchedEffect now performs the routing based on screenType
    LaunchedEffect(uiState.selectedTabId) {
        val selectedTab = uiState.tabs.firstOrNull { it.id == uiState.selectedTabId }
            ?: return@LaunchedEffect

        val definition = selectedTab.definition

        // This is the new routing logic
        val route = when (definition.screenType) {
            "FixedScreen" -> {
                // For fixed screens, we can use the tab's ID as the route
                selectedTab.id
            }
            "VocabScreen" -> definition.firestoreDocumentId?.let { docId ->
                RefScreen.DynamicSheet.createRoute(docId)
            }
            "GroupedVocabScreen" -> {
                // For grouped screens, we pass the parent tab's ID
                RefScreen.GroupedSheet.createRoute(selectedTab.id)
            }
            else -> {
                Timber.w("Unknown screenType '${definition.screenType}' for tab '${selectedTab.id}'. Cannot navigate.")
                null // For any unknown screen types, do nothing
            }
        }

        // The navigation call remains the same
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
                        text = tab.definition.title,
                        isSelected = (tab.id == uiState.selectedTabId),
                        onClick = { viewModel.onTabSelected(tab.id) }
                    )
                }
            }
        }

        if (uiState.tabs.isNotEmpty()) {
            // Part B: The Dynamic NavHost (now uses the single uiState)
            NavHost(
//                navController = refTabNavController,
//                startDestination = getRefScreenRouteFromTitle(uiState.tabs.firstOrNull()?.definition?.title ?: "") ?: RefScreen.Quiz.route,
//                modifier = Modifier.weight(1f)
                navController = refTabNavController,
                // The startDestination is now derived from the first tab's ID
                startDestination = uiState.tabs.first().id,
                modifier = Modifier.weight(1f)

            ) {

                composable(RefScreen.Quiz.route) { QuizScreen() }
                composable(RefScreen.Conjugations.route) { ConjugationsScreen() }
                composable(RefScreen.Prepositions.route) { PrepositionsScreen() }

                // 2. Dynamic VocabScreen
                composable(
                    route = RefScreen.DynamicSheet.route,
                    arguments = listOf(navArgument("documentId") { type = NavType.StringType })
                ) {
                    // This is correct: Hilt's SavedStateHandle will pass the documentId
                    ReferenceGenericScreen()
                }

                // 3. Dynamic GroupedScreen
                composable(
                    route = RefScreen.GroupedSheet.route,
                    arguments = listOf(navArgument("tabId") { type = NavType.StringType })
                ) {
                    // This is also correct: Hilt will pass the tabId
                    GroupedSheetScreen()
                }

            }
        }//: is Not Empty
        else{
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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