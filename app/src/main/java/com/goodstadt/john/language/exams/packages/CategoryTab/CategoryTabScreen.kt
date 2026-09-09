package com.goodstadt.john.language.exams.packages.CategoryTab

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.goodstadt.john.language.exams.models.SaveReminder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.packages.SavedPractice.SavedInfoSheetContent
import com.goodstadt.john.language.exams.packages.VocabQuiz.VocabQuizScreen
import com.goodstadt.john.language.exams.packages.me.PremiumUpgradeSheet
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.packages.Translate.TranslateSheet
import com.goodstadt.john.language.exams.screens.shared.CacheProgressBar
import com.goodstadt.john.language.exams.screens.shared.LetterInCircle
import com.goodstadt.john.language.exams.screens.shared.HelpInfoSheet
import com.goodstadt.john.language.exams.screens.shared.HighlightedWordInSentenceRow
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.screens.shared.SwipeableVocabRow
import com.goodstadt.john.language.exams.screens.shared.VoiceSettingsBottomSheet
import com.goodstadt.john.language.exams.screens.shared.gamification.VocabGamificationStatsSheet
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.utils.buildSentenceParts
import com.goodstadt.john.language.exams.viewmodels.VocabSectionQuizViewModel
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

private const val HELP_TRIGGER_VOICE_SELECTION_COUNT =
    15 //after 15 plays - show choose voice help screen

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryTabScreen(
    tabIdentifier: String? = null,
    categoryTitle: String? = null,
    selectedVoiceName: String,
    viewModel: CategoryTabViewModel = hiltViewModel()
) {


    val context = LocalContext.current
    // Notification permission (Android 13+) is requested lazily, on first save‑for‑practice below. The
    // reminder still schedules if denied; it just can't post a notification.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored: scheduling proceeds regardless */ }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    // --- Bottom Sheets ---
    var selectedWordForSheet by remember { mutableStateOf<Format0Word?>(null) }
    var selectedCategoryForSheet by remember { mutableStateOf<Category?>(null) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showHelpBottomSheet by remember { mutableStateOf(false) }

    var showGamificationSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showHelpSheet by viewModel.showHelpSheet.collectAsState()
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Freemium: tapping a locked section (header or teaser) opens the Premium upgrade sheet.
    var showUpgradeSheet by remember { mutableStateOf(false) }
    val upgradeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Shown after swipe-left Save: confirms the save + offers practice reminders.
    var showSavedSheet by remember { mutableStateOf(false) }
    var savedWordText by remember { mutableStateOf("") }
    val savedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Legend sheet explaining the section status dots + streak stars (opened by tapping them).
    var showQuizLegendSheet by remember { mutableStateOf(false) }
    val quizLegendSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Global Translate sheet, opened from the "T" button in the stats row.
    var showTranslateSheet by remember { mutableStateOf(false) }

    // Forget the last-played sentence when the user leaves this tab, so the Translate prefill starts blank.
    DisposableEffect(Unit) {
        onDispose { viewModel.clearLastPlayedSentence() }
    }

    // --- Rate Limit Sheets ---
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()
    val currentQuizCategory by viewModel.currentQuizCategory.collectAsStateWithLifecycle()
    // Bumps when quiz progress changes, so each section's status dots recompute.
    val quizStatusVersion by viewModel.quizStatusVersion.collectAsStateWithLifecycle()
    // Which section (if any) is currently playing its sentences in sequence.
    val playingSectionTitle by viewModel.playingSectionTitle.collectAsStateWithLifecycle()
    val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ✅ Watch for celebration trigger
//    val showCelebration by viewModel.showCelebration.collectAsState()
    val bannerTitle by viewModel.celebrationTitle.collectAsState()
    val bannerSubtitle by viewModel.celebrationSubtitle.collectAsState()
    val currentExamName by viewModel.currentExamName.collectAsStateWithLifecycle()


//    val activity = LocalContext.current.findActivity() as ComponentActivity
//    val speakerVm: SpeakerSelectionViewModel = hiltViewModel(activity)
//
//   // val showSpeakerSheet by viewModel.showSpeakerSheet.collectAsState()
//    //val speakerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
//
//    val showSPEAKERSheet by viewModel.showSPEAKERSheet.collectAsState()
//    val SPEAKERSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
//
    var showVoiceSheet by remember { mutableStateOf(false) }
    val hasSeenHelp by viewModel.hasSeenVoiceHelp.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.showSpeakerSheet.collect {


            viewModel.refreshGamificationStatsAndWait()
//            val (heard, _) = viewModel.calculateGrandTotals()
            //val heard = viewModel.totalHeardFlow.value


//            val h = viewModel.checkTotalsNow()
            val heard = viewModel.calculateGrandTotalsNow()

            Timber.i("LaunchedEffect() freshly calculated Exam Total is: $heard")


            Timber.i("heard total is $heard")
            if (heard > HELP_TRIGGER_VOICE_SELECTION_COUNT) {
                Timber.i("Screen has been triggered")
            }
            if (!hasSeenHelp && (heard > HELP_TRIGGER_VOICE_SELECTION_COUNT)) {
                Timber.i("Showing Help")
                viewModel.markVoiceHelpAsSeen() // Save to DataStore
                showVoiceSheet = true    // Show UI
            }
        }
    }


    // --- Lifecycle & Loading ---
    LaunchedEffect(Unit) {
        viewModel.setTestExamGoal()
    }

    LaunchedEffect(key1 = tabIdentifier, key2 = selectedVoiceName, key3 = currentExamName) {
        if (selectedVoiceName.isNotEmpty()) {
            if (selectedVoiceName.isNotEmpty() && currentExamName.isNotEmpty()) {
                if (tabIdentifier != null) {
                    // Determine Int tab number from string identifier if needed
                    val tabNumber = tabIdentifier.filter { it.isDigit() }.toIntOrNull() ?: 1
                    viewModel.loadContentForTab(tabNumber)
                }
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
                viewModel.onResume()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.saveDataOnExit()
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopSectionPlayback() // app minimised/backgrounded -> stop section playback
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopSectionPlayback() // leaving the tab -> stop section playback
        }
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

        // ✅ 1. ROOT CONTAINER MUST BE A BOX (To allow overlapping)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                    var selectedChipTitle by remember(menuItems) {
                        mutableStateOf(
                            menuItems.firstOrNull() ?: ""
                        )
                    }

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
                                cachedCount = state.heardCountOnTab, // Or state.heardSentenceIDs.size
                                totalCount = state.totalWordsOnTab,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 64.dp, vertical = 8.dp)
                            )
                            // Translate: mirrors the stats button on the far left. Opens the global
                            // Translate sheet, pre-filled with the last sentence played on this tab.
                            IconButton(
                                onClick = { showTranslateSheet = true },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 8.dp)
                            ) {
                                LetterInCircle(letter = "T", tint = Color(0xFFFF9800))
                            }
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
                            categories.forEachIndexed { catIdx, category ->
                                // Freemium gate: sections past the free preview (B1/B2 only) are locked.
                                val locked = viewModel.isCategoryLocked(catIdx)

                                stickyHeader {


                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface) // Important: Solid background for sticky behavior
                                            .padding(
                                                horizontal = 16.dp,
                                                vertical = 8.dp
                                            ), // Adjust padding as needed
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {

                                        Text(
                                            text = category.title.removeContentInBracketsAndTrim(),
                                            fontSize = 20.sp, // Match your existing CategoryHeader style
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor, // Or MaterialTheme.colorScheme.primary
                                            modifier = Modifier.weight(1f) // ✅ Pushes the icon to the far right
                                        )

                                        // Play the whole section's sentences in sequence (Play <-> Pause).
                                        val isThisSectionPlaying = playingSectionTitle == category.title
                                        IconButton(onClick = {
                                            // Only the FIRST sentence of each word (the one shown on the row).
                                            val sentences = category.words.mapNotNull { w ->
                                                w.sentences.firstOrNull()?.sentence
                                            }
                                            viewModel.playSection(category, sentences)
                                        }) {
                                            Icon(
                                                imageVector = if (isThisSectionPlaying)
                                                    Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (isThisSectionPlaying)
                                                    "Stop playing section" else "Play section",
                                                tint = orangeLight
                                            )
                                        }

                                        // Whole-quiz status summary: coloured dots for this section's quiz
                                        // (blank if never taken, one green dot if fully mastered).
                                        val quizStatus = remember(quizStatusVersion, category.title) {
                                            viewModel.quizStatusFor(category.title)
                                        }
                                        val quizStars = remember(quizStatusVersion, category.title) {
                                            viewModel.quizStarsFor(category.title)
                                        }
                                        QuizStatusDots(
                                            status = quizStatus,
                                            stars = quizStars,
                                            onClick = { showQuizLegendSheet = true }
                                        )

                                        if (locked) {
                                            // Locked section: a lock replaces the game-console (Quiz) icon.
                                            IconButton(onClick = { showUpgradeSheet = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "Locked - unlock with Premium",
                                                    tint = orangeLight
                                                )
                                            }
                                        } else if (state.isQuizAvailable) {
                                            val isFirstCategory = category == categories.first()
                                            if (isFirstCategory) {
                                                OutlinedButton(
                                                    onClick = {
                                                        viewModel.openQuizForCategory(
                                                            category
                                                        )
                                                    },
                                                    // 1. Set the Border width and color
                                                    border = BorderStroke(1.dp, orangeLight),
                                                    // 2. Set the Text/Icon color
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = orangeLight,
                                                        containerColor = Color.Transparent
                                                    ),
                                                    // 3. Shape (Rounded corners)
                                                    shape = RoundedCornerShape(8.dp),
                                                    // Optional: Adjust padding if it feels too big
                                                    contentPadding = PaddingValues(
                                                        horizontal = 4.dp,
                                                        vertical = 0.dp
                                                    )
                                                ) {
                                                    Text(
                                                        text = "Quiz",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
                                                }
                                            } else {
                                                IconButton(onClick = {
                                                    viewModel.openQuizForCategory(
                                                        category
                                                    )
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Default.SportsEsports,
                                                        contentDescription = "Take Quiz",
                                                        tint = orangeLight
                                                    )
                                                }
                                            }
                                        }else{
                                            if (DEBUG){
                                                println("am i here")
                                            }
                                        }

                                    }


                                }

                                if (locked) {
                                    // Locked section: hide the words behind a single Premium teaser row.
                                    item(key = "locked-teaser-${category.title}") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showUpgradeSheet = true }
                                                .padding(horizontal = 16.dp, vertical = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "🔒", fontSize = 16.sp)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Unlock with Premium to see these words",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else itemsIndexed(
                                    category.words,
                                    // Add 'index' to the key to guarantee uniqueness
                                    key = { index, word -> "${word.id}-${word.word}-$index" }
                                ) { index, wordEntry -> // You now get 'index' and 'wordEntry'

                                    val sentenceEntry = wordEntry.sentences.firstOrNull()

                                    if (sentenceEntry != null) {

//                                    val contentID = FirebaseAudioService.generateContentID(sentenceEntry.sentence)
                                        //val unifiedFilename = FirebaseAudioService.generateUnifiedFilename(sentenceEntry.sentence, selectedVoiceName)
                                        //val isDownloading = state.downloadingSentenceId == unifiedFilename
                                        val isHeard = viewModel.isHeard(sentenceEntry.sentence)
                                        //performmance enhancem,ent - 95% playCount is 0 so only check if isHeard == true
//                                        var playCount = 0
//                                        if (isHeard){
//                                            playCount = viewModel.getPlayCount(sentenceEntry.sentence)
//                                        }
                                        val playCount =
                                            viewModel.getPlayCount(sentenceEntry.sentence)

                                        SwipeableVocabRow(
                                            word = wordEntry,
                                            sentence = sentenceEntry,
                                            isSentenceAlreadyHeard = playCount > 0,
                                            isDownloading = false,//isDownloading,
                                            playCount = playCount,
                                            // Spaced-repetition dot: red -> amber -> green as the sentence
                                            // is heard on 1/2/3 separate occasions (>= gap apart).
                                            masteryLevel = viewModel.getSpacedCount(sentenceEntry.sentence),
                                            recalledWordKeys = state.recalledWordKeys,

                                            // ✅ TAP HANDLER (Delegate to ViewModel)
                                            onRowTapped = { w, s ->
//                                                val sentence = s.sentence
                                                viewModel.handleTap(s.sentence, category)
                                            },

                                            onFocus = {
                                                Timber.i("CategoryTabScreen().onFocus")
                                                viewModel.onFocusClicked(wordEntry)
                                            },
                                            onCancel = {
                                                Timber.i("CategoryTabScreen().onCancel")
                                                viewModel.onCancelClicked(wordEntry)
                                            },
                                            onMore = {
                                                selectedWordForSheet = wordEntry
                                                selectedCategoryForSheet = category
                                                showMoreSheet = true
                                            },
                                            // Swipe LEFT -> Save (toggle) the whole word into the level-aware practice list.
                                            isSaved = viewModel.isWordSaved(wordEntry),
                                            onSave = {
                                                val nowSaved = viewModel.onSaveWord(
                                                    word = wordEntry,
                                                    categoryTitle = category.title
                                                )
                                                if (nowSaved) {
                                                    savedWordText = wordEntry.word
                                                    showSavedSheet = true
                                                }
                                            }
                                        )
                                    } else {
                                        Text(
                                            "Error: No sentence found",
                                            color = Color.Red,
                                            modifier = Modifier.padding(12.dp)
                                        )
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


            } //: Column
            // ✅ Overlay on top
//            if (showCelebration) {
//                viewModel.playSuccessSound()
//                Box(
//                    modifier = Modifier
//                        .align(Alignment.TopCenter) // This is now valid!
//                        .zIndex(10f) // Ensures it floats above the list headers
//                ) {
//                    AchievementBanner(
//                        isVisible = showCelebration,
//                        title = bannerTitle,       // ✅ Pass Dynamic Title
//                        subtitle = bannerSubtitle, // ✅ Pass Dynamic Subtitle
//                        onDismiss = {
//                            Timber.i("User did dismiss")
//                        }
//                    )
//                }
//            }


            // --- Sheets & Overlays ---
            // Freemium content lock: shown when the user taps a locked section header or teaser.
            if (showUpgradeSheet) {
//                ModalBottomSheet(
//                    onDismissRequest = { showUpgradeSheet = false },
//                    sheetState = upgradeSheetState,
//                    containerColor = MaterialTheme.colorScheme.surface,
//                    contentColor = MaterialTheme.colorScheme.onSurface
//                ) {
                    PremiumUpgradeSheet(onDismiss = { showUpgradeSheet = false })
//                }
            }
            // Post-Save info + reminder choices.
            if (showSavedSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSavedSheet = false },
                    sheetState = savedSheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    SavedInfoSheetContent(
                        wordText = savedWordText,
                        onReminderSelected = { reminder ->
                            viewModel.scheduleReminder(reminder, savedWordText)
                            // First real use of notifications: ask now (Android 13+) when the user actually
                            // chooses a reminder, so it can appear. "Don't remind me" schedules nothing.
                            if (reminder != SaveReminder.NONE &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onDismiss = { showSavedSheet = false }
                    )
                }
            }
            if (isRateLimitingSheetVisible) {
                RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
            }
            if (isDailyRateLimitingSheetVisible) {
                if (context is ComponentActivity) {
                    RateLimitDailyPaywallBottomSheet(
                        onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                        onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
                    )
                }
            }
            if (isHourlyRateLimitingSheetVisible) {
                if (context is ComponentActivity) {
                    RateLimitHourlyPaywallBottomSheet(
                        onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                        onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
                    )

                }
            }

            // Sentence Detail Sheet
            if (showMoreSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showMoreSheet = false },
                    sheetState = bottomSheetState
                ) {
                    selectedWordForSheet?.let { word ->
                        selectedCategoryForSheet?.let { category ->
                            val playCount = viewModel.getPlayCount(word)
                            SentencesBottomSheetContent(
                                word = word,
                                playCount,
                                onBottomSheetRowTapped = { w, sentence ->
                                    // Redirect tap from bottom sheet to main VM logic
//                                    viewModel.handleSentenceTap(sentence.sentence, category)
                                    viewModel.handleTap(sentence.sentence, category)
                                }
                            )
                        }
                    }
                }
            } //: showMoreSheet
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
                // val (heard, total) = viewModel.calculateGrandTotals()

                val heard by viewModel.totalHeardFlow.collectAsState() // or collectAsStateWithLifecycle()
                val total by viewModel.totalCountFlow.collectAsState()

                //  val context = LocalContext.current
                val entryPoint = remember(context) {
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        StatsSheetEntryPoint::class.java
                    )
                }
                Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                    VocabGamificationStatsSheet(
                        grandTotalWords = total,
                        grandTotalMastered = heard,
                        categoryProgress = allProgress,//, // Pass the list
                        skillLevel = viewModel.getCurrentSkillLevel(),
                        xpManager = entryPoint.getXPManager(),
                        quizManager = entryPoint.getQuizManager(),
                        onDismiss = { showGamificationSheet = false }

                    )
                }
//            }
            }
        } //: Gamificatinon sheet

        if (showHelpSheet) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissHelpSheet() },
                sheetState = helpSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                // 3. Set Height to 3/4
                Box(modifier = Modifier.fillMaxHeight(0.90f)) {
                    HelpInfoSheet(
                        onDismiss = { viewModel.dismissHelpSheet() }
                    )
                }
            }
        }

        if (showVoiceSheet) {
            VoiceSettingsBottomSheet(
                sentence = viewModel.getLatestSentence(),
                onDismiss = { showVoiceSheet = false }
            )
        }
        if (currentQuizCategory != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.closeQuizSheet()
                },
                sheetState = quizSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                // Wrapper to initialize the specific quiz
                SectionQuizContainer(categoryTitle = currentQuizCategory!!.title)
            }
        }

        // Global Translate sheet, pre-filled with the last sentence played on this tab (blank if none).
        if (showTranslateSheet) {
            TranslateSheet(
                onDismiss = { showTranslateSheet = false },
                initialText = viewModel.getLatestSentence()
            )
        }

        // Legend explaining the status dots + streak stars (opened by tapping them on a section row).
        if (showQuizLegendSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQuizLegendSheet = false },
                sheetState = quizLegendSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                QuizStatusLegendSheet()
            }
        }

    } //: Box
}

/**
 * Explains the section-row quiz indicators: the gold streak stars and the coloured mastery dots. Rendered
 * as a title followed by one row per symbol (the symbol on the left, its meaning on the right).
 */
@Composable
private fun QuizStatusLegendSheet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "What the marks mean",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        // --- Stars (the achievement) ---
        LegendRow(
            leading = {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(22.dp)
                )
            },
            title = "Gold star — a flawless run",
            body = "You completed the whole section with no errors: every question right, first tap. " +
                "Each star must be earned on a separate day (at least a day apart)."
        )
        LegendRow(
            leading = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            title = "Three stars — section mastered",
            body = "Three flawless runs on three separate days masters the section."
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = "If your last run had a mistake, coloured dots show its make-up instead of stars:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // --- Dots (mastery breakdown) ---
        LegendRow(leadingDot = Color(0xFF4CAF50), title = "Green — Mastered",
            body = "Answered correctly, first try, across 3 spaced sessions.")
        LegendRow(leadingDot = Color(0xFF2196F3), title = "Blue — Review",
            body = "Right first time — building towards mastery.")
        LegendRow(leadingDot = Color(0xFFFF9800), title = "Orange — Learning",
            body = "Got it right, but it took more than one try.")
        LegendRow(leadingDot = Color.Red, title = "Red — Struggling",
            body = "Answered incorrectly — worth another look.")
        LegendRow(leadingDot = Color.Gray, title = "Grey — New / not finished",
            body = "Not attempted yet, or the section isn't complete.")
    }
}

/** One legend line: a leading symbol (a coloured dot, or a custom [leading] composable) then title + body. */
@Composable
private fun LegendRow(
    title: String,
    body: String,
    leadingDot: Color? = null,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .padding(top = 3.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            when {
                leading != null -> leading()
                leadingDot != null -> Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(leadingDot)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

@Composable
fun SentencesBottomSheetContent(
    // 1. The composable takes the selected word as its input
    word: Format0Word,
    playCount: Int,
    onBottomSheetRowTapped: (Format0Word, Sentence) -> Unit,
    modifier: Modifier = Modifier
) {
    // Use a Column with vertical scroll in case sentences are long
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        val playCountText = if (playCount == 0) {
            "Not heard"
        } else if (playCount == 1) {
            "1 play"
        } else {
            "${playCount} plays"
        }
        // 2. Display the main word prominently
        Text(
            text = word.word,
            style = MaterialTheme.typography.headlineLarge
        )
        if (word.definition.isNotEmpty()) {
            Text(
                text = word.definition,
                style = MaterialTheme.typography.titleSmall
            )
        }
        if (word.IPA.isNotEmpty()) {
            Text(
                text = word.IPA,
                style = MaterialTheme.typography.titleSmall
            )
        }
        if (word.pronounce.isNotEmpty()) {
            Text(
                text = word.pronounce,
                style = MaterialTheme.typography.titleSmall
            )
        }
        if (playCountText.isNotEmpty()) {
            Text(
                text = playCountText,
                style = MaterialTheme.typography.titleSmall
            )
        }
        HorizontalDivider()

        // 3. Loop through and display each sentence
        word.sentences.forEachIndexed { index, sentence ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val displayData = buildSentenceParts(entry = word, sentence = sentence)
                val playCountForThisRow = if (index == 0) playCount else 0

                Column(
                    modifier = Modifier.clickable {
                        onBottomSheetRowTapped(word, sentence)
                    }
                ) {
                    HighlightedWordInSentenceRow(
                        word = word.word,
                        parts = displayData.parts,
                        sentence = displayData.sentence,
                        isRecalling = false,
                        displayDot = false,
                        playCount = playCountForThisRow,  // ← clean and clear
                        isDownloading = false
                    )
                }
            }
        }

        // Add some space at the bottom for better scrolling
        Spacer(Modifier.height(32.dp))
    }
}
// --- Helpers ---

// --- Helper Composables for this Screen ---

@Composable
fun CategoryHeader(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = accentColor,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp)
    )
}

// Helper extension for strings (placeholder)
fun String.removeContentInBracketsAndTrim(): String = this.replace(Regex("\\(.*?\\)"), "").trim()

/**
 * Whole-quiz status summary for a section, shown as small coloured dots before the Quiz button:
 *   - never taken (all words New / no status)        -> nothing,
 *   - fully mastered (every word Mastered)           -> one green dot,
 *   - otherwise one dot per present state            -> red (struggling), orange (learning),
 *     blue (review), green (some mastered), grey (still some New / not finished).
 * Colours match the quiz's own mastery dots.
 */
@Composable
private fun QuizStatusDots(
    status: com.goodstadt.john.language.exams.models.CategoryMasteryState?,
    stars: Int,
    onClick: () -> Unit = {}
) {
    val red = Color.Red
    val orange = Color(0xFFFF9800)
    val blue = Color(0xFF2196F3)
    val green = Color(0xFF4CAF50)
    val grey = Color.Gray

    // Status dots (blank if never taken).
    val dots: List<Color> = when {
        status == null || status.total == 0 || status.newCount == status.total -> emptyList()
        status.mastered == status.total -> listOf(green) // fully mastered
        else -> buildList {
            if (status.struggling > 0) add(red)
            if (status.learning > 0) add(orange)
            if (status.review > 0) add(blue)
            if (status.mastered > 0) add(green)
            if (status.newCount > 0) add(grey)
        }
    }

    val starCount = stars.coerceIn(0, 3)
    if (dots.isEmpty() && starCount == 0) return // never taken -> nothing to show

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() } // tap the dots/stars to explain them
            .padding(end = 6.dp, start = 2.dp, top = 4.dp, bottom = 4.dp)
    ) {
        if (starCount > 0) {
            // Current flawless streak: show ONLY gold stars (a completed no-error run).
            repeat(starCount) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "streak star",
                    tint = Color(0xFFFFC107), // gold/yellow
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            // Latest go had errors (or in progress): show ONLY the mastery status dots.
            dots.forEach { c ->
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(c)
                )
            }
        }
    }
}

//✅ 4. HELPER COMPOSABLE
// This ensures we get a fresh ViewModel and trigger the load
@Composable
fun SectionQuizContainer(
    categoryTitle: String,
    viewModel: VocabSectionQuizViewModel = hiltViewModel(),
    onInteraction: (Boolean) -> Unit = {}
) {
    // Trigger load when this view appears
    LaunchedEffect(categoryTitle) {
        viewModel.loadSectionQuiz(categoryTitle)
    }

    // 2. ✅ Observe Dirtiness
    // Whenever the internal VM state changes to dirty, notify the parent
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    LaunchedEffect(isDirty) {
        onInteraction(isDirty)
    }

    // When the quiz bottom sheet closes (this container leaves composition), record this go as a dated
    // attempt and (DEBUG only) log the whole-quiz status plus every attempt, to validate and inform a
    // future dashboard UI.
    DisposableEffect(Unit) {
        onDispose { viewModel.recordAndLogCategoryAttempt() }
    }
    // Render the existing screen
    Box(modifier = Modifier.fillMaxHeight(0.9f)) {
        VocabQuizScreen(viewModel = viewModel)
    }
}