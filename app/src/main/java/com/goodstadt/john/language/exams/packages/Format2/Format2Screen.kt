package com.goodstadt.john.language.exams.screens.Format2


import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.font.FontWeight
import com.goodstadt.john.language.exams.packages.me.PremiumUpgradeSheet
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.config.LanguageConfig.quizSheetWordPairsFilename
import com.goodstadt.john.language.exams.models.Format2Level
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.packages.CategoryTab.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.packages.reference.NavigationViewModel
import com.goodstadt.john.language.exams.screens.shared.QuizSheetView
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestStatsSheet
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import com.goodstadt.john.language.exams.utils.QuizDataConverter
import com.goodstadt.john.language.exams.utils.annotatedSentenceByWords
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import dagger.hilt.android.EntryPointAccessors

/**
 * A "dumb" Composable screen that displays data in the "Format2" structure.
 * It receives its state from a parent composable.
 *
 * @param title The main title for the screen, from the root Format2File object.
 * @param description The main subtitle for the screen.
 * @param levels The list of sections (`Format2Level`) to display.
 * @param onRowTapped A callback for when a sentence row is tapped.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Format2Screen(
    viewModel: Format2ViewModel = hiltViewModel(),
    title: String,
    description: String,
    levels: List<Format2Level>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()
    var showSideQuestSheet by remember { mutableStateOf(false) }
    val sheetStateSideQuest = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showQuizSheet by remember { mutableStateOf(false) }
    // Freemium: tapping a locked teaser entry opens the Premium upgrade sheet.
    var showUpgradeSheet by remember { mutableStateOf(false) }
    val upgradeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navViewModel: NavigationViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
  //  val uiState by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // ✅ Ensure red dots are correct when coming back to app
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp), // This adds space BETWEEN the main gray boxes
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
                    IconButton(onClick = {
                        showQuizSheet = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "Stats",
                            tint = Color(0xFFFF9800)
                        )
                    }
                    // Side Quest Icon
                    IconButton(onClick = { showSideQuestSheet = true }) {
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

        // --- 2. Loop through each 'level' to create the sections ---
        levels.forEachIndexed { levelIdx, level ->

            // Running 0-based entry index across ALL levels, for the freemium preview gate.
            val levelBase = levels.take(levelIdx).sumOf { it.wordsAndSentences.size }

            // a) Create a sticky header for the level's information
            stickyHeader {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface) // Important for sticky headers
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (level.description.isNotBlank()) {
                        Text(
                            text = level.description,
                            style = MaterialTheme.typography.titleMedium,
//                            color = orangeLight
                        )
                    }
                }
            }

            // b) Add the items (the word entries) for the current level
            itemsIndexed(
                items = level.wordsAndSentences,
                key = { _, entry -> "${entry.word}-${entry.definition}" } // unique within a level
            ) { localIdx, entry ->
                // Freemium gate: entries past the free preview become title-only teasers.
                val entryIndex = levelBase + localIdx
                val locked = viewModel.isReferenceRowLocked(entryIndex)

                // This Column represents a single row block for a Format2Entry
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(
                            // Locked entries are tappable to the paywall; unlocked ones let each row play.
                            if (locked) Modifier.clickable { showUpgradeSheet = true } else Modifier
                        ),

                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (locked) {
                        // Title only: the word plus a lock hint; hide the example sentences behind Premium.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.word,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Unlock with Premium to see examples",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Text(text = "🔒", fontSize = 14.sp)
                        }
                    } else {
                        // Loop through the sentences for this entry
                        entry.sentences.forEachIndexed { index, item ->
                            val isHeard = viewModel.isHeard(item.sentence)
                            val playCount = viewModel.getPlayCount(item.sentence)

                            Format2Row(
                                word = entry.word,
                                sentence = item.sentence,
                                isHeard,
                                playCount,
                                onTapped = {
                                    viewModel.handleTap(item.sentence)
                                },

                                modifier = Modifier.padding(start = 16 .dp)
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
            }
        }
    }
    if (isRateLimitingSheetVisible){
        RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
    }
    if (isDailyRateLimitingSheetVisible){
        if (context is androidx.activity.ComponentActivity) {
            RateLimitDailyPaywallBottomSheet(
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
            )
        }
    }
    if (isHourlyRateLimitingSheetVisible){
        if (context is androidx.activity.ComponentActivity) {
            RateLimitHourlyPaywallBottomSheet(
                onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
            )
        }
    }
    // Freemium content lock: shown when the user taps a locked teaser entry.
    if (showUpgradeSheet) {
//        ModalBottomSheet(
//            onDismissRequest = { showUpgradeSheet = false },
//            sheetState = upgradeSheetState,
//            containerColor = MaterialTheme.colorScheme.surface,
//            contentColor = MaterialTheme.colorScheme.onSurface
//        ) {
            PremiumUpgradeSheet(onDismiss = { showUpgradeSheet = false })
//        }
    }
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
//            val refData = remember(referenceCounts) {
//                getReferenceData(audioCache, referenceCounts)
//            }

            // 4. Get Hilt Entry Point for QuizManager
            val entryPoint = remember(context) {
                EntryPointAccessors.fromApplication(context.applicationContext, StatsSheetEntryPoint::class.java)
            }

            // 3. Build the Data
            // We use 'remember(referenceCounts)' so it rebuilds whenever the counts change
            val manifest = viewModel.getCachedManifest()
            val sideQuestData = remember(referenceCounts) {
                buildSideQuestData(audioCache,manifest)
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
    }
    if (showQuizSheet) {
        val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val questions = QuizDataConverter.readWordPairsJSONForQuiz( context,quizSheetWordPairsFilename)
        viewModel.incQuizSheetStat()

        val pageTitle = "10 Questions"
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

/**
 * A helper composable for displaying a single row within the Format2Screen.
 */
@Composable
fun Format2Row(
    word: String,
    sentence: String,
    isHeard:Boolean,
    playCount:Int,
    onTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Here you would implement your logic for highlighting the `word` within the `sentence`
    // using AnnotatedString, similar to your iOS `Format2RowView`.
    val styledSentence = annotatedSentenceByWords(
        sentence = sentence,
        wordsToHighlight = word
    )

    // For now, a simple layout:
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTapped)
            .padding(vertical = 4.dp, horizontal = 16.dp)

    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {


            Text(text = styledSentence, modifier = Modifier.weight(1f))

            if (false && playCount > 1) {
                Text(text = "$playCount ", fontSize = 12.sp)
            }
            // ✅ THE RED DOT
            if (isHeard) {
                Text(text = "🔴", fontSize = 12.sp)
            }
        }
    }
}