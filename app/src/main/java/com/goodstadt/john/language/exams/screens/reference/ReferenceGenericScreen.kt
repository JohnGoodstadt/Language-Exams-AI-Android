package com.goodstadt.john.language.exams.screens.reference

//package com.yourpackage.ui.reference.generic // Or your preferred package

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.screens.RateLimitDailyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.screens.shared.SideQuestStatsSheet
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import dagger.hilt.android.EntryPointAccessors

// ... other necessary imports

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

    val selectedVoiceName by viewModel.currentVoiceName.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    var showSideQuestSheet by remember { mutableStateOf(false) }
    val sheetStateSideQuest = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            // The existing SectionedVocabList works perfectly, as it just needs a list of categories
//            SectionedVocabList(
            SimpleSectionedVocabList(
                data = state.categories,
                selectedVoiceName = "selectedVoiceName",

                // The Direct Check (History)
                isHeard = { sentence -> viewModel.isHeard(sentence) },
                playCount = { sentence -> viewModel.playCount(sentence) },
                // Reference screens usually don't use "Focus/Recalling", so empty
                recalledWordKeys = emptySet(),

                playbackState = state.playbackState,

                // Generic VM doesn't usually track specific download IDs, passed null
                downloadingSentenceId = null,

                listState = lazyListState,
                contentPadding = PaddingValues(bottom = 80.dp),

                // ACTIONS
                onRowTapped = { word, sentence, category ->
                    viewModel.handleTap(sentence.sentence)
                },
                onFocus = { /* No-op for generic reference */ },
                onCancel = { /* No-op for generic reference */ },
                onMore = { word, category ->
                    // Add bottom sheet logic here if you want word details
                },
                onSideQuestTapped = {
                    showSideQuestSheet = true
                }
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

                    // 2. Collect Latest Stats (Reactive)
                    // This ensures the sheet has data even if it wasn't pre-calculated
                    val referenceCounts by audioCache.referenceHeardCounts.collectAsStateWithLifecycle()

                    // 3. Build the Data Models using the Helper
                    val refData = remember(referenceCounts) {
                        getReferenceData(audioCache, referenceCounts)
                    }

                    // 4. Get Hilt Entry Point for QuizManager
                    val entryPoint = remember(context) {
                        EntryPointAccessors.fromApplication(context.applicationContext, StatsSheetEntryPoint::class.java)
                    }

                    // 3. Build the Data
                    // We use 'remember(referenceCounts)' so it rebuilds whenever the counts change
                    val sideQuestData = remember(referenceCounts) {
                        buildSideQuestData(audioCache)
                    }

                    Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                        // Note: You might need to pass data in here if SideQuestStatsSheet
                        // doesn't pull everything from Hilt automatically yet.
                        SideQuestStatsSheet(
                            paragraphCount = 0, // Mock or fetch from VM
                            paragraphHeardCount = 0, // Mock or fetch from VM
                            conjugations = sideQuestData.conjugations,
                            adjectives = sideQuestData.adjectives,
                            quickRefs = sideQuestData.quickRefs,
                            quizManager = entryPoint.getQuizManager(),
                            onDismiss = { showSideQuestSheet = false }
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
            RateLimitDailyReasonsBottomSheet(
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
            )
        }
    }
    if (isHourlyRateLimitingSheetVisible){
        if (context is ComponentActivity) {
            RateLimitHourlyReasonsBottomSheet(
                onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {//reliable signal that the user is leaving the screen.
                viewModel.saveDataOnExit()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // This is called when the composable leaves the screen
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}