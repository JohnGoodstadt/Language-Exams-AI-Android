package com.goodstadt.john.language.exams.screens.reference

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.models.Format2Level
import com.goodstadt.john.language.exams.screens.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestStatsSheet
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import com.goodstadt.john.language.exams.viewmodels.Format2GroupedViewModel
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.draw.clip
import com.goodstadt.john.language.exams.screens.reference.shared.MissingView
import com.goodstadt.john.language.exams.screens.reference.shared.ScrollableHorizontalLevelPicker
import com.goodstadt.john.language.exams.utils.QuizDataConverter
import com.goodstadt.john.language.exams.viewmodels.Format3GroupedViewModel
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
    if (isDailyRateLimitingSheetVisible) { /* ... RateLimitDailyReasons ... */ }
    if (isHourlyRateLimitingSheetVisible) { /* ... RateLimitHourlyReasons ... */ }

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

        val questions = QuizDataConverter.readWordPairsJSONForQuiz( context,"QuizSheetWordPairs-en.json" )
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
        "Good vs Well" -> "Good/Well"
        "Say vs Tell" -> "Say/Tell"
        "Speak vs Talk" -> "Speak/Talk"
        "Hear vs Listen" -> "Hear/Listen"

        else -> original
    }
}