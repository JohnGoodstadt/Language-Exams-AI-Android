package com.goodstadt.john.language.exams.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.UpdateState
import com.goodstadt.john.language.exams.navigation.IconResource
import com.goodstadt.john.language.exams.navigation.Screen
import com.goodstadt.john.language.exams.navigation.bottomNavItems
import com.goodstadt.john.language.exams.packages.CategoryTab.CategoryTabScreen
import com.goodstadt.john.language.exams.packages.MeTabContainer.MeTabContainerScreen
import com.goodstadt.john.language.exams.packages.ReadinessAudit.ReadinessAuditScreen
import com.goodstadt.john.language.exams.screens.ReferenceTabContainer.ReferenceTabContainerScreen
import com.goodstadt.john.language.exams.packages.diagnostic.DiagnosticScreen
import com.goodstadt.john.language.exams.screens.me.ChooseEnglishAndExamSheet
import com.goodstadt.john.language.exams.packages.reference.NavigationViewModel
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestNavTarget
import com.goodstadt.john.language.exams.ui.theme.DarkSecondary
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.utils.findActivity
import com.goodstadt.john.language.exams.viewmodels.AuthUiState
import com.goodstadt.john.language.exams.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    val globalUiState by mainViewModel.uiState.collectAsState()
    val authState = globalUiState.authState
    val updateState by mainViewModel.updateState.collectAsState()

    // ✅ 1. Get the Activity-Scoped Navigation ViewModel -- navigate auto on onClick
    val context = LocalContext.current
//    val navViewModel: NavigationViewModel = hiltViewModel(context as ComponentActivity)

    val activity = LocalContext.current.findActivity() as? androidx.activity.ComponentActivity
    val navViewModel: NavigationViewModel = if (activity != null) {
        hiltViewModel(activity)
    } else {
        hiltViewModel() // Fallback (though this branch shouldn't happen in valid UI)
    }
//    val navigationState by navViewModel.pendingNavigation.collectAsState()
    // ✅ 2. Listen for requests to switch tabs
    LaunchedEffect(Unit) {
        navViewModel.navigationEvent.collect { target ->
            //val target = navigationState
            if (target != null) {
                // Check if we need to switch tabs
                // if (target is SideQuestNavTarget.Reference || target is SideQuestNavTarget.Quiz) {
                if (target is SideQuestNavTarget.Reference) {

                    // Assuming "Reference" is the second tab (index 1)
                    // Switch the Tab Router
                    //navController.navigate("reference_graph_route") {
                    navController.navigate(Screen.Tab4.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }

                    // Note: We do NOT consume the event here yet.
                    // We let the Reference screen consume it so it knows which sub-tab to pick.

                }
                else if (target is SideQuestNavTarget.MainTab)  {

                    // Logic to define which route corresponds to index 0, 1, 2
                    val route = when(target.tabIndex) {
                        0 -> Screen.Tab1.route
                        1 -> Screen.Tab2.route
                        2 -> Screen.Tab3.route
                        3 -> Screen.Tab4.route
                        4 -> Screen.Tab5.route
                        else -> Screen.Tab1.route
                    }

                    navController.navigate(route) {
                        // Standard Bottom Navigation cleanup
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                else if (target is SideQuestNavTarget.MainSubTab)  {

                    // Logic to define which route corresponds to index 0, 1, 2
                    val route = when(target.tabIndex) {
                        0 -> Screen.Tab1.route
                        1 -> Screen.Tab2.route
                        2 -> Screen.Tab3.route
                        3 -> Screen.Tab4.route
                        4 -> Screen.Tab5.route
                        else -> Screen.Tab1.route
                    }

                    navController.navigate(route) {
                        // Standard Bottom Navigation cleanup
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                else{
                    Timber.i("target is not Reference or Main")
                }
            }
        }


    }

    ChangeStatusBarColor(color = Color.Transparent, darkIcons = false)

    when (authState) {
        is AuthUiState.Loading -> {
            // Show a full-screen loading indicator while Firebase connects
            Timber.d("MainScreen.Loading")
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text(modifier = Modifier.padding(top = 60.dp), text = "Connecting...")
            }
        }
        is AuthUiState.Error -> {
            Timber.d("MainScreen.Error")
            // Show an error message if sign-in fails
//            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                Text(text = "Error: ${(authState as AuthUiState.Error).message}", color = Color.Red)
//            }
            NoConnectionScreen(
                errorMessage = authState.message,
                onRetry = { mainViewModel.onRetryConnection() } // Hook up the retry button
            )
        }
        is AuthUiState.Success -> {
            // Once successful, show the main app content
            // The entire Scaffold and NavHost goes inside here
            MainAppContent(
                navController = navController,
                selectedVoiceName = globalUiState.selectedVoiceName,
                //loadingManager
            )
        }
    }

    // This will conditionally display the update dialog on top of your app content.
    when (val state = updateState) {
        is UpdateState.ForcedUpdate -> {
            UpdateDialog(
                message = state.message,
                updateUrl = state.url,
                isForced = true,
                onDismiss = { /* Non-dismissible, so this does nothing */ }
            )
        }
        is UpdateState.OptionalUpdate -> {
            UpdateDialog(
                message = state.message,
                updateUrl = state.url,
                isForced = false,
                onDismiss = { mainViewModel.dismissOptionalUpdate() }
            )
        }
        is UpdateState.NoUpdateNeeded -> {
            // Do nothing, no dialog is shown
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainAppContent(navController: NavHostController, selectedVoiceName: String) {

    val mainViewModel: MainViewModel = hiltViewModel()
    val globalUiState by mainViewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) // Make it non-dismissible by swiping
//    val isLoading by loadingManager.isShowingGlobalLoading.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val navBarColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accentColor, // Your "accentColor"
                    selectedTextColor = accentColor,
                    unselectedIconColor = DarkSecondary,
                    unselectedTextColor = DarkSecondary,
                    indicatorColor = Color.Transparent
                )

                bottomNavItems.forEach { screen ->
                    val badgeCount = globalUiState.badgeCounts[screen.route] ?: 0

                    NavigationBarItem(
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badgeCount > 0) {
                                        Badge (
                                            containerColor = Color.Red,
                                            contentColor = Color.White
                                        ) {

                                            val text = if (badgeCount > 99) "99+" else badgeCount.toString()
                                            Text(text)
                                        }
                                    }
                                }
                            ) {
                                // This is your existing icon rendering logic. It goes inside the BadgedBox.
                                when (val icon = screen.icon) {
                                    is IconResource.VectorIcon -> Icon(icon.imageVector, contentDescription = screen.title)
                                    is IconResource.DrawableIcon -> Icon(painterResource(id = icon.id), contentDescription = screen.title)
                                }
                            }
                        },
                        label = { Text(screen.title, maxLines = 1, textAlign = TextAlign.Center) },
                        colors = navBarColors,
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
//            startDestination = Screen.Tab1.route,
            startDestination = mainViewModel.startDestination,
            Modifier.padding(bottom = innerPadding.calculateBottomPadding())

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
                ReferenceTabContainerScreen()
            }

            composable(Screen.Tab5.route) {
                MeTabContainerScreen()
            }
            composable("diagnostic_test") {
                DiagnosticScreen(
                    onFinished = {
                        // When the user finishes or skips, take them back to Settings
                        navController.popBackStack()
                    }
                )
            }

        }
    } //: Scaffold

    if (globalUiState.showEnglishChoiceSheet && LanguageConfig.hasDialectSelection) {
        ModalBottomSheet(
            // An empty lambda makes the sheet non-dismissible by dragging or tapping outside.
            // The user MUST make a choice.
            onDismissRequest = { },
            sheetState = sheetState,
            modifier = Modifier.fillMaxHeight(0.80f) //NOTE: if too low then button off screen
        ) {
                // Your existing 2-choice sheet (Dialect + Exam Level)
                ChooseEnglishAndExamSheet(
                    onClose = { mainViewModel.onLanguageChoiceDismissed() }
                )
//            else if ( false ) { //Do I need this as I am testing user to choose?
//                // The new 1-choice sheet (Exam Level only)
//                ChooseExamLevelSheet(
//                    onClose = { mainViewModel.onLanguageChoiceDismissed() }
//                )
//            }
        }
    }

    if (globalUiState.showReadinessAuditIntroSheet) {
        var showCloseWarning by remember { mutableStateOf(false) }
        val introSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val coroutineScope = rememberCoroutineScope()

        // By the time onDismissRequest fires (swipe, scrim tap, or back press), Material3 has
        // already finished animating the sheet to Hidden - re-showing it (rather than just
        // suppressing the boolean that controls composing it) is what makes it "bounce back"
        // when the user isn't allowed to close yet.
        fun reopenIntroSheet() {
            coroutineScope.launch { introSheetState.show() }
        }

        ModalBottomSheet(
            onDismissRequest = {
                if (mainViewModel.isReadinessAuditBaselineComplete()) {
                    mainViewModel.dismissReadinessAuditIntroSheet()
                } else {
                    // Don't close yet - ask for confirmation first.
                    showCloseWarning = true
                }
            },
            sheetState = introSheetState,
            modifier = Modifier.fillMaxHeight(0.92f)
        ) {
            ReadinessAuditScreen(
                onFinished = { mainViewModel.dismissReadinessAuditIntroSheet() }
            )
        }

        if (showCloseWarning) {
            AlertDialog(
                onDismissRequest = {
                    showCloseWarning = false
                    reopenIntroSheet()
                },
                title = { Text("Skip the English check?") },
                text = { Text("Completing the first quiz helps me set up the app accurately for you. Are you sure you want to close before finishing it?") },
                confirmButton = {
                    TextButton(onClick = {
                        showCloseWarning = false
                        mainViewModel.dismissReadinessAuditIntroSheet()
                    }) {
                        Text("Close Anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCloseWarning = false
                        reopenIntroSheet()
                    }) {
                        Text("Continue Test")
                    }
                }
            )
        }
    }

    if (false) {
        // A semi-transparent black background that blocks clicks
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(enabled = false) {}, // Block touch events
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }

// You can now DELETE the RenderCategoryTab composable entirely, as it's no longer needed.
// The NavHost now calls CategoryTabScreen directly.

}



@Composable
fun ChangeStatusBarColor(color: Color, darkIcons: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            // Set the status bar color
            window.statusBarColor = color.toArgb()
            // Set the status bar icon/text color
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
        }
    }
}
@Composable
fun UpdateDialog(
    message: String,
    updateUrl: String,
    isForced: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            // Only allow dismissal if it's not a forced update
            if (!isForced) {
                onDismiss()
            }
        },
        title = { Text("Update Available") },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                    context.startActivity(intent)
                }
            ) {
                Text("Update Now")
            }
        },
        dismissButton = {
            if (!isForced) {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}



@Composable
fun NoConnectionScreen(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = "No Connection",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Connection Error",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}