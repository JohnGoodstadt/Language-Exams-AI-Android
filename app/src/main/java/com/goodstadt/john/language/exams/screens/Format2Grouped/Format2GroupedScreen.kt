package com.goodstadt.john.language.exams.screens.Format2Grouped

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.models.Format2Level
import com.goodstadt.john.language.exams.screens.CategoryTab.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.screens.Format2.Format2Row
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.reference.NavigationViewModel
import com.goodstadt.john.language.exams.screens.shared.QuizSheetView
import com.goodstadt.john.language.exams.screens.reference.shared.ScrollableHorizontalLevelPicker
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestStatsSheet
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import com.goodstadt.john.language.exams.utils.QuizDataConverter
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Format2GroupedScreen(
    viewModel: Format2GroupedViewModel = hiltViewModel()
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
//            val selectedOption = if (isSmallScreen) getShortTabTitle(rawSelectedTitle) else rawSelectedTitle
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
            uiState.currentFormat2File?.let { file ->


                // ✅ USE THE STATELESS COMPONENT
                Format2Content(
                    title = file.title,
                    description = file.description,
                    levels = file.data,

                    // Logic Delegates
                    isHeard = { sentence ->

                        viewModel.isHeard(sentence)

                    },
                    getPlayCount = { 0 }, // Optional if you want to implement count logic
                    onPlayTrack = { sentence -> viewModel.handleSentenceTap(sentence) },
                    onShowSideQuestSheet = { showSideQuestSheet = true },
                    onShowQuizSheet = {
                        showQuizSheet = true
                    }
                )
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Format2Content(
    title: String,
    description: String,
    levels: List<Format2Level>,
    // Callbacks for logic
    isHeard: (String) -> Boolean,
    getPlayCount: (String) -> Int,
    onPlayTrack: (String) -> Unit,
    onShowSideQuestSheet: () -> Unit,
    onShowQuizSheet: () -> Unit,
    modifier: Modifier = Modifier
) {

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // --- 1. Top-Level Title and Description ---
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = orangeLight
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onShowQuizSheet) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "Stats",
                            tint = Color(0xFFFF9800)
                        )
                    }
                    // Side Quest Icon
                    IconButton(onClick = onShowSideQuestSheet) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = "Stats",
                            tint = Color(0xFFFF9800)
                        )
                    }
                }
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }
        }

        // --- 2. Loop through each 'level' ---
        levels.forEach { level ->
            stickyHeader {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (level.description.isNotBlank()) {
                        Text(
                            text = level.description,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            items(
                items = level.wordsAndSentences,
                key = { entry -> "${entry.word}-${entry.definition}" }
            ) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    entry.sentences.forEach { item ->

                        // Use callbacks
                        val isSentenceHeard = isHeard(item.sentence)
                        val count = getPlayCount(item.sentence)

                        Format2Row(
                            word = entry.word,
                            sentence = item.sentence,
                            isHeard = isSentenceHeard,
                            playCount = count,
                            onTapped = {
                                onPlayTrack(item.sentence)
                            },
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
            }
        }
    }
}