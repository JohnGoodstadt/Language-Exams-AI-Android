package com.goodstadt.john.language.exams.screens.Format3Grouped

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.config.LanguageConfig.quizSheetWordPairsFilename
import com.goodstadt.john.language.exams.packages.CategoryTab.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.screens.Format3.Format3SheetView
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.packages.reference.NavigationViewModel
import com.goodstadt.john.language.exams.packages.reference.shared.ScrollableHorizontalLevelPicker
import com.goodstadt.john.language.exams.screens.shared.QuizSheetView
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestStatsSheet
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import com.goodstadt.john.language.exams.utils.QuizDataConverter
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Format3GroupedScreen(
    viewModel: Format3GroupedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navViewModel: NavigationViewModel = hiltViewModel(context as ComponentActivity)

    // Rate Limit State
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsStateWithLifecycle()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsStateWithLifecycle()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsStateWithLifecycle()

    // Side Quest Sheet
    var showSideQuestSheet by remember { mutableStateOf(false) }
    val sheetStateSideQuest = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showQuizSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // 1. SUB-TAB PICKER (Reuse your logic for small screens if needed)
        if (uiState.subTabs.isNotEmpty()) {
            val configuration = LocalConfiguration.current
            val isSmallScreen = configuration.screenWidthDp < 380

        /*    val options = remember(uiState.subTabs, isSmallScreen) {
                uiState.subTabs.map { if (isSmallScreen) getShortTabTitle(it.title) else it.title }
            }*/
            val options = remember(uiState.subTabs, isSmallScreen) {
                uiState.subTabs.map { getShortTabTitle(it.title) }
            }
            val rawSelectedTitle = uiState.selectedSubTab?.title ?: ""

            val selectedOption = getShortTabTitle(rawSelectedTitle)

            ScrollableHorizontalLevelPicker(
                options = options,
                selectedOption = selectedOption,
                onOptionSelected = { selectedDisplayTitle ->
                    val newTab = uiState.subTabs.firstOrNull { tab ->
                        val display = getShortTabTitle(tab.title)
                        display == selectedDisplayTitle
                    }
                    if (newTab != null) viewModel.onSubTabSelected(newTab)
                }
            )
        }

        // 2. CONTENT AREA
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (uiState.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${uiState.error}", color = Color.Red)
            }
        } else {
            // Render the Format 2 Content

            uiState.currentFormat3File?.let { file ->
                if (file.categories.isNotEmpty()) {


                    Format3SheetView(
                        file = file,
                        onSentenceTapped = { sentence ->
                            Timber.i("tapped on $sentence")
                            viewModel.handleSentenceTap(sentence)
                        }
                    )
                }
            }
        }
    }

    // --- SHEETS ---

    // Rate Limits
    if (isRateLimitingSheetVisible) RateLimitOKReasonsBottomSheet { viewModel.hideRateOKLimitSheet() }
    if (isDailyRateLimitingSheetVisible) {
        RateLimitDailyPaywallBottomSheet(
            onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
            onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
        )
    }
    if (isHourlyRateLimitingSheetVisible) {
        RateLimitHourlyPaywallBottomSheet(
            onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
            onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
        )
    }

    // Side Quest Sheet
    if (showSideQuestSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSideQuestSheet = false },
            sheetState = sheetStateSideQuest
        ) {
            val acm = viewModel.getAudioCacheManager()
            val referenceCounts by acm.referenceHeardCounts.collectAsStateWithLifecycle()
            val manifest = viewModel.getCachedManifest()
            val sideQuestData = remember(referenceCounts) { buildSideQuestData(acm,manifest) }
            //val refData = remember(referenceCounts) { getReferenceData(acm, referenceCounts) } // if you use this helper

            val entryPoint = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, StatsSheetEntryPoint::class.java) }

            viewModel.incSideQuestStat()

            Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                SideQuestStatsSheet(
                    paragraphCount = viewModel.getAIParagraphCount(),
                    paragraphHeardCount = viewModel.getAIParagraphHeardCount(),
                    conjugations = sideQuestData.conjugations,
                    adjectives = sideQuestData.adjectives,
                    pairs = sideQuestData.pairs,
                    quickRefs = sideQuestData.quickRefs,
                    quizManager = entryPoint.getQuizManager(),
                    xpManager = entryPoint.getXPManager(),
                    onNavigate = { target ->
                        showSideQuestSheet = false
                        navViewModel.requestNavigation(target)
                    },
                    onDismiss = { showSideQuestSheet = false }
                )
            }
        }
    } //: Side Quest
    if (showQuizSheet) {
        val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val questions = QuizDataConverter.readWordPairsJSONForQuiz( context,quizSheetWordPairsFilename )
        viewModel.incQuizSheetStat()

        val pageTitle = "Word Pairs"
        if (questions.isNotEmpty()) {
            ModalBottomSheet(
                onDismissRequest = { showQuizSheet = false },
                sheetState = quizSheetState,
                // ✅ FIX 1: Force the sheet to take up 95% of the screen height
                modifier = Modifier.fillMaxHeight(0.80f),
                // ✅ FIX 2: Ensure it respects system colors
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                // ✅ FIX 3: Container that fills the sheet AND adds bottom padding
                // We use a Box with fillMaxSize so the QuizView's Spacers work correctly.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Add padding for the Android Gesture Bar / Navigation Bar
                        .padding(bottom = 40.dp)
                ) {
                    QuizSheetView(
                        questions = questions,
                        title = pageTitle,//"Quiz: Sounds the Same",
                        onDismiss = { showQuizSheet = false }
                    )
                }
            }
        }
    } //: QuizSheet
}

// Helper (Copy from GroupedSheetScreen if not shared)
private fun getShortTabTitle(original: String): String {
    return when (original) {
        "Kennen vs Wissen" -> "Kennen/Wissen"
        "Fragen vs Bitten" -> "Fragen/Bitten"
        "Bringen vs Holen" -> "Bringen/Holen"
        "Hoeren vs Zuhoeren" -> "Hoeren/Zuhoeren"
        else -> original
    }
}