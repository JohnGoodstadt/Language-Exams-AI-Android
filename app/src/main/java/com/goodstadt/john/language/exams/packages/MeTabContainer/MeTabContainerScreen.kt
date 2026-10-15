package com.goodstadt.john.language.exams.packages.MeTabContainer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.goodstadt.john.language.exams.ui.LocalIsExpanded
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.navigation.MeScreen
import com.goodstadt.john.language.exams.navigation.getMeScreenRouteFromTitle
import com.goodstadt.john.language.exams.packages.CategoryTab.CategoryTabScreen
import com.goodstadt.john.language.exams.packages.CategoryTab.CategoryTabViewModel
import com.goodstadt.john.language.exams.packages.CategoryTab.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.packages.Focus.FocusScreen
import com.goodstadt.john.language.exams.packages.MyProgress.MyProgressScreen
import com.goodstadt.john.language.exams.packages.SavedPractice.SavedPracticeScreen
import com.goodstadt.john.language.exams.packages.Settings.SettingsScreen
import com.goodstadt.john.language.exams.packages.UploadJson.UploadJsonScreen
import com.goodstadt.john.language.exams.packages.dailydictionary.DictionaryEntryBrowserScreen
import com.goodstadt.john.language.exams.packages.dailydictionary.DictionaryEntryBrowserViewModel
import com.goodstadt.john.language.exams.packages.diagnostic.DiagnosticScreen
import com.goodstadt.john.language.exams.packages.me.SearchScreen
import com.goodstadt.john.language.exams.packages.reference.NavigationViewModel
import com.goodstadt.john.language.exams.screens.ParagraphScreen
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.ui.readableWidth
import com.goodstadt.john.language.exams.utils.findActivity
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * This is the main container for the entire "Me" tab. It sets up the persistent
 * horizontal menu and a NavHost below it to display the content for the selected item.
 */
@OptIn(ExperimentalMaterialNavigationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MeTabContainerScreen(viewModel: MeTabContainerTabViewModel = hiltViewModel()) {


    val meTabNavController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true // This is the correct M3 parameter
    )
    val scope = rememberCoroutineScope()
    val isSheetVisible = uiState.selectedCategoryTitle != null

    val context = LocalContext.current
//    val navViewModel: NavigationViewModel = hiltViewModel(context as ComponentActivity)

    val activity = LocalContext.current.findActivity() as? ComponentActivity
    val navViewModel: NavigationViewModel = if (activity != null) {
        hiltViewModel(activity)
    } else {
        hiltViewModel() // Fallback (though this branch shouldn't happen in valid UI)
    }
    LaunchedEffect(isSheetVisible) {
        if (!isSheetVisible) {
            scope.launch { sheetState.hide() }.join()
        }
    }

    // val menuItems by tabsViewModel.meTabMenuItems.collectAsState()
    val menuItems = LanguageConfig.meTabMenuItems
//    val navBackStackEntry by meTabNavController.currentBackStackEntryAsState()

    var selectedChipTitle by remember(menuItems) {
        mutableStateOf(menuItems.firstOrNull() ?: "")
    }

    // Deep link (e.g. from a practice-reminder notification): auto-select a Me sub-tab, once.
    LaunchedEffect(Unit) {
        navViewModel.pendingMeSubTabTitle?.let { title ->
            selectedChipTitle = title
            getMeScreenRouteFromTitle(title)?.let { route ->
                meTabNavController.navigate(route) { launchSingleTop = true }
            }
            navViewModel.pendingMeSubTabTitle = null
        }
    }

    val entryPoint = remember(key1 = context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            StatsSheetEntryPoint::class.java
        )
    }

    // Tablet (V4_tablet Phase 3): master-detail. On Expanded windows the sub-tabs move to a left
    // sidebar list and the content shows beside it; phones keep the top horizontal chip row.
    val isExpanded = LocalIsExpanded.current

    // Shared selection used by both the phone chip row and the tablet sidebar.
    val selectMeTab: (String) -> Unit = { title ->
        selectedChipTitle = title
        getMeScreenRouteFromTitle(title)?.let { route ->
            meTabNavController.navigate(route) { launchSingleTop = true }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding() ) {
        // Part A: The Persistent Horizontal Menu (phone only; tablet uses the sidebar below).
        if (!isExpanded) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(menuItems) { title ->
                    MenuItemChip(
                        text = title,
                        isSelected =  (title == selectedChipTitle),
                        onClick = { selectMeTab(title) }
                    )
                }
            }
        }

        // Part B: The NavHost content, as a reusable slot so the phone (full width) and tablet
        // (beside the sidebar) layouts share one NavHost. readableWidth (Phase 1) still caps it.
        val detailPane: @Composable (Modifier) -> Unit = { paneModifier ->
        Box(
            modifier = paneModifier
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
        NavHost(
            navController = meTabNavController,
//            startDestination = MeScreen.Focusing.route,
            //startDestination = MeScreen.Settings.route,
            startDestination = MeScreen.Progress.route,
            modifier = Modifier
                .fillMaxSize()
                .readableWidth()
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
            composable(MeScreen.Focus.route) {
                FocusScreen(onNavigate = { target -> navViewModel.requestNavigation(target) })
            }
            composable(MeScreen.Saved.route) { SavedPracticeScreen() }
            composable(MeScreen.Settings.route) { SettingsScreen(navController = meTabNavController) }
            composable(MeScreen.Search.route) { SearchScreen() }
            composable(MeScreen.Progress.route) {
                MyProgressScreen(
                    xpManager = entryPoint.getXPManager(),
                    onNavigate = { target ->
                    // Send the signal to switch tabs/screens
                    navViewModel.requestNavigation(target)
                })
            }
            composable(MeScreen.Paragraph.route) { ParagraphScreen() }
            composable(MeScreen.DailyWord.route) {
                val vm: DictionaryEntryBrowserViewModel = hiltViewModel()
                DictionaryEntryBrowserScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = vm
                )
            }
            composable("diagnostic_test") {
                DiagnosticScreen(
                    onFinished = {
                        // When the user finishes or skips, take them back to Settings
                        //navController.popBackStack()
                        Timber.e("popBackStack")
                    }
                )
            }
            composable("upload_json") {
                UploadJsonScreen(onClose = { meTabNavController.popBackStack() })
            }
           // composable(MeScreen..route) { WordQuizScreen() }

        }
        } //: Box - readable-width wrapper (tablet)
        } //: detailPane slot

        if (isExpanded) {
            // Master-detail: sub-tab sidebar | divider | detail pane.
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MeSidebar(
                    titles = menuItems,
                    selectedTitle = selectedChipTitle,
                    onSelect = selectMeTab,
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                )
                VerticalDivider()
                detailPane(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            detailPane(Modifier.weight(1f))
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

//@Composable
//fun WordQuizScreen() {
//    Box(
//        modifier = Modifier.fillMaxSize(),
//        contentAlignment = Alignment.Center
//    ) {
//        Text(text = "Hello, World!")
//    }
//}

/**
 * Tablet (V4_tablet Phase 3): the Me sub-tab list as a left sidebar (master pane).
 * One selectable row per menu item; the selected row is highlighted. Replaces the horizontal chip
 * row on Expanded windows.
 */
@Composable
private fun MeSidebar(
    titles: List<String>,
    selectedTitle: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(titles, key = { it }) { title ->
            val selected = title == selectedTitle
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(title) }
                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
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