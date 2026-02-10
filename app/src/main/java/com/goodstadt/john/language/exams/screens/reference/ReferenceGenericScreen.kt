package com.goodstadt.john.language.exams.screens.reference

//package com.yourpackage.ui.reference.generic // Or your preferred package

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.screens.shared.AchievementBanner
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestStatsSheet
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import com.goodstadt.john.language.exams.utils.QuizDataConverter
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

// ... other necessary imports∫

// 1. RENAMED: PrepositionsScreen -> ReferenceGenericScreen
// 2. MODIFIED: The ViewModel type is now our new generic one
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceGenericScreen(viewModel: ReferenceGenericViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()
    val navViewModel: NavigationViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
    val selectedVoiceName by viewModel.currentVoiceName.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    var showSideQuestSheet by remember { mutableStateOf(false) }
    val sheetStateSideQuest = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showQuizSheet by remember { mutableStateOf(false) }

    val showCelebration by viewModel.showCelebration.collectAsStateWithLifecycle()
    val bannerTitle by viewModel.celebrationTitle.collectAsStateWithLifecycle()
    val bannerSubtitle by viewModel.celebrationSubtitle.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // ✅ Ensure red dots are correct when coming back to app
                viewModel.saveDataOnExit()
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }



    // 3. MODIFIED: The when statement now checks for ReferenceGenericUiState types
    when (val state = uiState) {
        is GenericVocabUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is GenericVocabUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = Color.Red)
            }
        }
//        is GenericVocabUiState.NotAvailable -> {
//            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                Text("This feature is not available for the current language.")
//            }
//        }
        is GenericVocabUiState.Success -> {
            Box(modifier = Modifier.fillMaxSize()) {


                SimpleSectionedVocabList(
                    data = state.categories,
                    isHeard = { sentence -> viewModel.isHeard(sentence) },
                    playCount = { sentence -> viewModel.playCount(sentence) },
                    listState = lazyListState,
                    contentPadding = PaddingValues(bottom = 80.dp),

                    // ACTIONS
                    onRowTapped = { _, sentence, _ ->
                        viewModel.handleTap(sentence.sentence)
                    },
                    onSideQuestTapped = {
                        showSideQuestSheet = true
                    },
                    onQuizSheetTapped =  {

                        showQuizSheet = true
                    },
                )
                if (showSideQuestSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showSideQuestSheet = false },
                        sheetState = sheetStateSideQuest,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        // 1. Get Managers
                        val audioCache = viewModel.getAudioCacheManager()

                        val referenceCounts by audioCache.referenceHeardCounts.collectAsStateWithLifecycle()

                        val entryPoint = remember(context) {
                            EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                StatsSheetEntryPoint::class.java
                            )
                        }

                        // 3. Build the Data
                        // We use 'remember(referenceCounts)' so it rebuilds whenever the counts change
                        val manifest = viewModel.getCachedManifest()
                        val sideQuestData = remember(referenceCounts) {
                            buildSideQuestData(audioCache, manifest)
                        }

                        viewModel.incSideQuestStat()

                        Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                            // Note: You might need to pass data in here if SideQuestStatsSheet
                            // doesn't pull everything from Hilt automatically yet.
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
                } //: SideShow
                if (showQuizSheet) {
                    val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                    val questions = QuizDataConverter.readPrepositionsQuizQuestions( context,"QuizSheetPrepositions-en")
                    viewModel.incQuizSheetStat()

                    val pageTitle = "Prepositions"
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
                                    .padding(bottom = 40.dp)
                            ) {
                                QuizSheetView(
                                    questions = questions,
                                    title = pageTitle,
                                    onDismiss = { showQuizSheet = false }
                                )
                            }
                        }
                    }else{
                        //TODO: show toast? or fault?
                    }
                } //: QuizSheet
                if (showCelebration) {
                    viewModel.playSuccessSound()
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter) // This is now valid!
                            .zIndex(10f) // Ensures it floats above the list headers
                    ) {
                        AchievementBanner(
                            isVisible = showCelebration,
                            title = bannerTitle,       // ✅ Pass Dynamic Title
                            subtitle = bannerSubtitle, // ✅ Pass Dynamic Subtitle
                            onDismiss = {
                                Timber.i("User did dismiss")
                            }
                        )
                    }
                }
            }
        }//: Success
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


}