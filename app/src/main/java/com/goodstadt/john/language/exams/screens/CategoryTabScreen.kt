package com.goodstadt.john.language.exams.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.screens.shared.VocabGamificationStatsSheet
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.viewmodels.CategoryTabUiState
import com.goodstadt.john.language.exams.viewmodels.CategoryTabViewModel
import com.goodstadt.john.language.exams.viewmodels.UiEvent
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StatsSheetEntryPoint {
    fun getXPManager(): XPManager
    fun getQuizManager(): QuizHistoryManager
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryTabScreen(
    tabIdentifier: String? = null,
    categoryTitle: String? = null,
    selectedVoiceName: String,
    viewModel: CategoryTabViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    // --- Bottom Sheets ---
    var selectedWordForSheet by remember { mutableStateOf<Format0Word?>(null) }
    var selectedCategoryForSheet by remember { mutableStateOf<Category?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showGamificationSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // --- Rate Limit Sheets ---
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()

    // --- Lifecycle & Loading ---
    LaunchedEffect(Unit) {
        viewModel.setTestExamGoal()
    }

    LaunchedEffect(key1 = tabIdentifier, key2 = categoryTitle, key3 = selectedVoiceName) {
        if (selectedVoiceName.isNotEmpty()) {
            if (tabIdentifier != null) {
                // Determine Int tab number from string identifier if needed
                val tabNum = tabIdentifier.toIntOrNull() ?: 1
                viewModel.loadContentForTab(tabNum)
            } else if (categoryTitle != null) {
                // viewModel.loadContentForCategory(categoryTitle) // If you have this
            }
        } else {
            Timber.i("CategoryTabScreen: selectedVoiceName IS NULL!")
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCacheState(selectedVoiceName)
                viewModel.connectToBilling()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.saveDataOnExit()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- UI Events (Snackbar) ---
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // --- UI Setup ---
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Safely extract data from State
    val categories = (uiState as? CategoryTabUiState.Success)?.categories ?: emptyList()

    // Menu Logic
    val menuItems = remember(categories) { categories.map { it.title } }
    val categoryIndexMap = remember(categories) {
        var currentIndex = 0
        val map = mutableMapOf<String, Int>()
        categories.forEach { category ->
            map[category.title] = currentIndex
            currentIndex += 1 + category.words.size
        }
        map
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // --- Loading State ---
            if (uiState is CategoryTabUiState.Loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            }

            // --- Success State ---
            else if (uiState is CategoryTabUiState.Success) {
                val state = uiState as CategoryTabUiState.Success
                var selectedChipTitle by remember(menuItems) { mutableStateOf(menuItems.firstOrNull() ?: "") }

                Column(modifier = Modifier.fillMaxSize()) {

                    // 1. Horizontal Menu
                    if (tabIdentifier != null) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(menuItems, key = { it }) { title ->
                                MenuItemChip(
                                    text = title,
                                    isSelected = (title == selectedChipTitle),
                                    onClick = {
                                        selectedChipTitle = title
                                        scrollToCategory(
                                            title = title,
                                            coroutineScope = coroutineScope,
                                            lazyListState = lazyListState,
                                            indexMap = categoryIndexMap
                                        )
//                                        scrollToCategory(title, coroutineScope, lazyListState, categoryIndexMap)
                                    }
                                )
                            }
                        }
                    }

                    // 2. Stats Bar & Gamification Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        CacheProgressBar(
                            cachedCount = state.cachedAudioCount, // Or state.heardSentenceIDs.size
                            totalCount = state.totalWordsInTab,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 64.dp, vertical = 8.dp)
                        )
                        IconButton(
                            onClick = { showGamificationSheet = true },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = "Stats",
                                tint = Color(0xFFFF9800)
                            )
                        }
                    }

                    // 3. Main Vocabulary List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = lazyListState,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        categories.forEach { category ->
                            stickyHeader {
                                CategoryHeader(title = category.title.removeContentInBracketsAndTrim())
                            }

                            items(category.words, key = { "${it.id}-${it.word}" }) { wordEntry ->
                                val sentenceToShow = wordEntry.sentences.firstOrNull()

                                if (sentenceToShow != null) {

                                    // ✅ RED DOT LOGIC (History Based)
                                    // 1. Generate Voice-Agnostic ID
                                    val contentID = FirebaseAudioService.generateContentID(sentenceToShow.sentence)
                                    // 2. Check Set provided by ViewModel
                                   // val isSentenceAlreadyHeard = state.heardSentenceIDs.contains(contentID)
                                    val isSentenceAlreadyHeard = true
                                    // Visual cue for download/playing
                                    val unifiedFilename = FirebaseAudioService.generateUnifiedFilename(sentenceToShow.sentence, selectedVoiceName)
                                    val isDownloading = state.downloadingSentenceId == unifiedFilename

                                    SwipeableVocabRow(
                                        word = wordEntry,
                                        sentence = sentenceToShow,
                                        isSentenceAlreadyHeard = true,//sSentenceAlreadyHeard, // Pass correct boolean
                                        isDownloading = isDownloading,
                                        recalledWordKeys = state.recalledWordKeys,

                                        // ✅ TAP HANDLER (Delegate to ViewModel)
                                        onRowTapped = { w, s ->
                                            // Extract sentence string
                                            val text = s.sentence
                                            viewModel.handleSentenceTap(text, category)
                                        },

                                        onFocus = { viewModel.onFocusClicked(wordEntry) },
                                        onCancel = { viewModel.onCancelClicked(wordEntry) },
                                        onMore = {
                                            selectedWordForSheet = wordEntry
                                            selectedCategoryForSheet = category
                                            showBottomSheet = true
                                        }
                                    )
                                } else {
                                    Text("Error: No sentence found", color = Color.Red, modifier = Modifier.padding(12.dp))
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            } // End Success

            // --- Error State ---
            else if (uiState is CategoryTabUiState.Error) {
                val errorMsg = (uiState as CategoryTabUiState.Error).message
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)
                }
            }

            // --- Sheets & Overlays ---
            if (isRateLimitingSheetVisible) {
                RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
            }
            if (isDailyRateLimitingSheetVisible) {
                if (context is ComponentActivity) {
                    RateLimitDailyReasonsBottomSheet(
                        onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                        onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
                    )
                }
            }
            if (isHourlyRateLimitingSheetVisible) {
                if (context is ComponentActivity) {
                    RateLimitHourlyReasonsBottomSheet(
                        onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                        onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
                    )
                }
            }

            // Sentence Detail Sheet
            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = bottomSheetState
                ) {
                    selectedWordForSheet?.let { word ->
                        selectedCategoryForSheet?.let { category ->
                            SentencesBottomSheetContent(
                                word = word,
                                onBottomSheetRowTapped = { w, sentence ->
                                    // Redirect tap from bottom sheet to main VM logic
                                    viewModel.handleSentenceTap(sentence.sentence, category)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Gamification Stats Sheet ---
    if (showGamificationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGamificationSheet = false },
            sheetState = sheetState,
//            containerColor = MaterialTheme.colorScheme.surface,
//            contentColor = MaterialTheme.colorScheme.onSurface
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            // Get data from AudioCacheManager (Totals) and ViewModel (Progress)
//            val acm = AudioCacheManager.shared // Or via Hilt EntryPoint

            // This ensures we get the latest numbers for the graph
            val allProgress = viewModel.buildCategoryProgress()
            // Filter for this tab if needed, or show all

            // Note: You might need a helper in ViewModel to sum specific tab totals
            val (heard, total) = viewModel.calculateGrandTotals()

            val context = LocalContext.current
            val entryPoint = remember(context) {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    StatsSheetEntryPoint::class.java
                )
            }
            Box(modifier = Modifier.fillMaxHeight(0.85f)) {
//                com.goodstadt.john.language.exams.ui.theme.LanguageExamsAITheme{
                VocabGamificationStatsSheet(
                    grandTotalWords = total,
                    grandTotalMastered = heard,
                    categoryProgress = allProgress,//, // Pass the list
                    xpManager = entryPoint.getXPManager(),
                    quizManager = entryPoint.getQuizManager(),
                    onDismiss = { showGamificationSheet = false }

                )
            }
//            }
        }
    }
}
private fun scrollToCategory(
    title: String,
    coroutineScope: CoroutineScope,
    lazyListState: LazyListState,
    indexMap: Map<String, Int>
) {
    coroutineScope.launch {
        val index = indexMap[title] ?: return@launch
        lazyListState.animateScrollToItem(index = index)
    }
}
// --- Helpers ---

// Helper extension for strings (placeholder)
fun String.removeContentInBracketsAndTrim(): String = this.replace(Regex("\\(.*?\\)"), "").trim()