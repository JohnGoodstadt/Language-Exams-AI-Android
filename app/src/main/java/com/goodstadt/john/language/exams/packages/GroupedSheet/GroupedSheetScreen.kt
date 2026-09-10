package com.goodstadt.john.language.exams.screens.GroupedSheet


import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.packages.CategoryTab.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.packages.me.PremiumUpgradeSheet
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.packages.reference.NavigationViewModel
import com.goodstadt.john.language.exams.data.ReferenceQuizSheetMapping
import com.goodstadt.john.language.exams.packages.GrammarQuiz.GrammarQuizScreen
import com.goodstadt.john.language.exams.packages.Translate.TranslateSheet
import com.goodstadt.john.language.exams.screens.shared.QuizSheetView
import com.goodstadt.john.language.exams.packages.reference.SimpleSectionedVocabList
import com.goodstadt.john.language.exams.packages.reference.shared.ScrollableHorizontalLevelPicker
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestStatsSheet
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import com.goodstadt.john.language.exams.utils.QuizDataConverter
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupedSheetScreen(
    viewModel: GroupedSheetViewModel = hiltViewModel()
) {
    // 1. Collect the single source of truth from the ViewModel
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()
    val lazyListState = rememberLazyListState()
    var showSideQuestSheet by remember { mutableStateOf(false) }
    val sheetStateSideQuest = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showQuizSheet by remember { mutableStateOf(false) }
    val sheetStateQuiz = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Translate ("T") sheet, pre-filled with the last sentence played on this screen.
    var showTranslateSheet by remember { mutableStateOf(false) }

    // Forget the last-played sentence when leaving, so the Translate prefill starts blank next visit.
    DisposableEffect(Unit) { onDispose { viewModel.clearLastPlayedSentence() } }

    // Freemium: tapping a locked teaser word opens the Premium upgrade sheet.
    var showUpgradeSheet by remember { mutableStateOf(false) }
    val upgradeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)



    val navViewModel: NavigationViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
    // The main layout is a vertical column
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 4.dp)) {

        // 2. The Sub-Tab Picker (the sub-menu)
        // This only shows if there are sub-tabs to display
        if (uiState.subTabs.isNotEmpty()) {

            val configuration = LocalConfiguration.current
            val isSmallScreen = configuration.screenWidthDp < 380 // 360dp is the breakpoint for old phones

            val options: List<String> = remember(uiState.subTabs, isSmallScreen) {
                uiState.subTabs.map { tab ->
                    if (isSmallScreen) getShortTabTitle(tab.title) else tab.title
                }
            }

            // 3. Determine Selected Option (Must match the display version)
            val rawSelectedTitle = uiState.selectedSubTab?.title ?: ""
            val selectedOption = if (isSmallScreen) getShortTabTitle(rawSelectedTitle) else rawSelectedTitle




            ScrollableHorizontalLevelPicker(
                options = options,
                selectedOption = selectedOption,
                onOptionSelected = { selectedDisplayTitle ->
                    // 4. Reverse Lookup
                    // We need to find the original tab object based on the Display Title we just clicked
                    val newSelectedSubTab = uiState.subTabs.firstOrNull { tab ->
                        val displayTitle = if (isSmallScreen) getShortTabTitle(tab.title) else tab.title
                        displayTitle == selectedDisplayTitle
                    }

                    if (newSelectedSubTab != null) {
                        viewModel.onSubTabSelected(newSelectedSubTab)
                    }
                }
            )
        }

        // 3. The Content Area, which changes based on the 'contentState'
        when (val contentState = uiState.contentState) {
            is ContentState.Idle -> {
                // The initial state before any content is loaded.
                // You can leave this empty or show a placeholder.
            }
            is ContentState.Loading -> {
                // Show a loading indicator while fetching data from Firestore
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ContentState.Success -> {

                SimpleSectionedVocabList(
                    data = contentState.categories,
                    isHeard = { sentence -> viewModel.isHeard(sentence) },
                    playCount = { sentence -> viewModel.getPlayCount(sentence) },
                    listState = lazyListState,
                    contentPadding = PaddingValues(bottom = 80.dp),

                    // Freemium: lock words past the free preview; tapping a locked word opens the paywall.
                    isWordLocked = { index -> viewModel.isReferenceRowLocked(index) },
                    onLockedTapped = { showUpgradeSheet = true },

                    // ACTIONS
                    onRowTapped = { word, sentence, category ->
                        viewModel.handleTap(sentence.sentence)
                    },
                    onSideQuestTapped = {
                        showQuizSheet = false
                        showSideQuestSheet = true

                    },
                    onQuizSheetTapped = {
                        showSideQuestSheet = false
                        showQuizSheet = true
                    },
                    // Translate ("T") to the left of the Quiz button.
                    onTranslateTapped = { showTranslateSheet = true }
                )
            }
            is ContentState.Error -> {
                // An error occurred during the data fetch
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = contentState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        if (isRateLimitingSheetVisible){
            RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
        }
        if (isDailyRateLimitingSheetVisible){
            if (context is ComponentActivity) {
                RateLimitDailyPaywallBottomSheet(
                    onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                    onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
                )
            }
        }
        if (isHourlyRateLimitingSheetVisible){
            if (context is ComponentActivity) {
                RateLimitHourlyPaywallBottomSheet(
                    onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                    onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
                )
            }
        }
        // Freemium content lock: shown when the user taps a locked teaser word.
        if (showUpgradeSheet) {
//            ModalBottomSheet(
//                onDismissRequest = { showUpgradeSheet = false },
//                sheetState = upgradeSheetState,
//                containerColor = MaterialTheme.colorScheme.surface,
//                contentColor = MaterialTheme.colorScheme.onSurface
//            ) {
                PremiumUpgradeSheet(onDismiss = { showUpgradeSheet = false })
//            }
        }
        if (showSideQuestSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSideQuestSheet = false },
                sheetState = sheetStateSideQuest,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val acm = viewModel.getAudioCacheManager()

                // Collect latest stats
                val referenceCounts by acm.referenceHeardCounts.collectAsStateWithLifecycle()
               // val refData = remember(referenceCounts) { getReferenceData(acm, referenceCounts) }

                val manifest = viewModel.getCachedManifest()
                val sideQuestData = remember(referenceCounts) {
                    buildSideQuestData(acm,manifest)
                }

                Timber.i("$sideQuestData")

                val entryPoint = remember(key1 = context) {
                    EntryPointAccessors.fromApplication(context.applicationContext, StatsSheetEntryPoint::class.java)
                }

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
                            showSideQuestSheet = false // Close sheet first
                            navViewModel.requestNavigation(target) // Send signal to Parent
                        },
                        onDismiss = { showSideQuestSheet = false }
                    )
                }
            }
        }
        if (showQuizSheet) {
            val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val pageTitle = uiState.title

            // If this reference group ships a fileFormat-7 quiz JSON (e.g. Adjectives), reuse the full
            // Grammar quiz screen (marking, mastery, TTS, filters) driven by that JSON instead of the
            // in-app generated quiz. Both the language-independent key and the level come from the picked
            // sub-tab's doc id (GermanA1Adjectives -> key "Adjectives", level "A1"); the display title
            // (pageTitle) may be localised ("Adjektive"), so it must NOT be used to pick the quiz.
            val docId = uiState.selectedSubTab?.firestoreDocumentId
            val quizGroupKey = ReferenceQuizSheetMapping.keyFromDocId(docId)
            val quizLevel = ReferenceQuizSheetMapping.levelFromDocId(docId)
            val useReferenceQuiz = quizGroupKey != null && quizLevel != null &&
                ReferenceQuizSheetMapping.hasQuiz(quizGroupKey)

            LaunchedEffect(Unit) { viewModel.incQuizSheetStat() }

            ModalBottomSheet(
                onDismissRequest = { showQuizSheet = false },
                sheetState = quizSheetState,
                modifier = Modifier.fillMaxHeight(if (useReferenceQuiz) 0.92f else 0.80f),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Add padding for the Android Gesture Bar / Navigation Bar
                        .padding(bottom = 40.dp)
                ) {
                    if (useReferenceQuiz) {
                        // Same quiz experience as Focus -> GrammarQuizScreen, pointed at the Reference JSON.
                        GrammarQuizScreen(
                            category = pageTitle,           // localised display title ("Adjektive")
                            level = quizLevel!!,
                            referenceGroupKey = quizGroupKey!! // language-independent key ("Adjectives")
                        )
                    } else {
                        // Legacy in-app quiz: built from the loaded categories (words that carry BOTH
                        // lockedClause and weakenedClause). Empty until content is Success / if none qualify.
                        val categories = (uiState.contentState as? ContentState.Success)?.categories ?: emptyList()
                        val questions = remember(uiState.contentState) {
                            QuizDataConverter.generateAdjectivesQuiz(categories, limit = 10)
                        }
                        if (questions.isNotEmpty()) {
                            QuizSheetView(
                                questions = questions,
                                title = "Quiz: $pageTitle",
                                onDismiss = { showQuizSheet = false }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No quiz is available for this sheet yet.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } //: show sheet

        // Translate sheet, pre-filled with the last sentence played here (blank if none).
        if (showTranslateSheet) {
            TranslateSheet(
                onDismiss = { showTranslateSheet = false },
                initialText = viewModel.getLatestSentence()
            )
        }
    } //: Column

}
// Helper to shorten names for small screens (Huawei LMN-LX9 etc)
private fun getShortTabTitle(original: String): String {
    return when (original) {
        "Conjugations" -> "Conj."
        "Intermediate" -> "Inter."
        "Advanced" -> "Adv."
        "Adjectives" -> "Adj."
        "Prepositions" -> "Preps"
        "Sounds the Same" -> "Sounds"
        "Good vs Well" -> "Good/Well"
        // Add other long titles here
        else -> original
    }
}