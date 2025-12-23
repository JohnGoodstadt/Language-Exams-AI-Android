package com.goodstadt.john.language.exams.screens.reference


import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.screens.RateLimitDailyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.screens.reference.shared.SectionedVocabList
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestStatsSheet
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
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
    val navViewModel: NavigationViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
    // The main layout is a vertical column
    Column(modifier = Modifier.fillMaxSize()) {

        // 2. The Sub-Tab Picker (the sub-menu)
        // This only shows if there are sub-tabs to display
        if (uiState.subTabs.isNotEmpty()) {
            val options: List<String> = remember(uiState.subTabs) {
                uiState.subTabs.map { it.title }
            }
            val selectedOption: String = uiState.selectedSubTab?.title ?: ""

            // b) Call your reusable composable
            HorizontalLevelPicker(
                options = options,
                selectedOption = selectedOption,
                onOptionSelected = { selectedTitle ->
                    // c) Find the corresponding SubTabDefinition and notify the ViewModel
                    val newSelectedSubTab = uiState.subTabs.firstOrNull { it.title == selectedTitle }
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

                    // ACTIONS
                    onRowTapped = { word, sentence, category ->
                        viewModel.handleTap(sentence.sentence)
                    },
                    onSideQuestTapped = {
                        showSideQuestSheet = true
                    }
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
            if (context is androidx.activity.ComponentActivity) {
                RateLimitDailyReasonsBottomSheet(
                    onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                    onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
                )
            }
        }
        if (isHourlyRateLimitingSheetVisible){
            if (context is androidx.activity.ComponentActivity) {
                RateLimitHourlyReasonsBottomSheet(
                    onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                    onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
                )
            }
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
                val refData = remember(referenceCounts) { getReferenceData(acm, referenceCounts) }

                val sideQuestData = remember(referenceCounts) {
                    buildSideQuestData(acm)
                }

                Timber.i("$sideQuestData")

                val entryPoint = remember(key1 = context) {
                    EntryPointAccessors.fromApplication(context.applicationContext, StatsSheetEntryPoint::class.java)
                }

                Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                    SideQuestStatsSheet(
                        paragraphCount = viewModel.getAIParagraphCount(),
                        paragraphHeardCount = viewModel.getAIParagraphHeardCount(),
                        conjugations = sideQuestData.conjugations,
                        adjectives = sideQuestData.adjectives,
                        quickRefs = sideQuestData.quickRefs,
                        quizManager = entryPoint.getQuizManager(),
                        onNavigate = { target ->
                            showSideQuestSheet = false // Close sheet first
                            navViewModel.requestNavigation(target) // Send signal to Parent
                        },
                        onDismiss = { showSideQuestSheet = false }
                    )
                }
            }
        }
    }
}