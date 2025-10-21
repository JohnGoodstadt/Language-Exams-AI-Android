package com.goodstadt.john.language.exams.screens // Or your correct package

// --- All Necessary Imports ---
//import android.media.session.PlaybackState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.CategoryTabViewModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.categories
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.utils.buildSentenceParts
import com.goodstadt.john.language.exams.viewmodels.UiEvent
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import removeContentInBracketsAndTrim
import timber.log.Timber

/**
 * A self-contained screen that displays vocabulary for a specific tab.
 * It manages its own state and logic via the CategoryTabViewModel.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryTabScreen(
    tabIdentifier: String? = null,
    categoryTitle: String? = null,
    selectedVoiceName: String,
    viewModel: CategoryTabViewModel = hiltViewModel()//,
//    mainViewModel: MainViewModel = hiltViewModel()
) {

//    val globalUiState by mainViewModel.uiState.collectAsState()
//    val isPremium = globalUiState.isPremiumUser
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
//bottom sheet
    var selectedWordForSheet by remember { mutableStateOf<VocabWord?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState()

    LaunchedEffect(key1 = tabIdentifier, key2 = categoryTitle, key3 = selectedVoiceName) {
        //Timber.e("CategoryTabScreen.LaunchEffect $tabIdentifier $categoryTitle $selectedVoiceName")
        if (selectedVoiceName.isNotEmpty()) {
            if (tabIdentifier != null) {
                viewModel.loadContentForTab(tabIdentifier, selectedVoiceName)
            } else if (categoryTitle != null) {
                viewModel.loadContentForCategory(categoryTitle, selectedVoiceName)
            }
        }else{
            Timber.i("CategoryTabScreen.LaunchEffect selectedVoiceName IS NULL !")
        }
    }
// 2) When data is ready, kick off the background recalculation (no UI updates)
    LaunchedEffect(categories, selectedVoiceName) {
        if (selectedVoiceName.isNotEmpty() && categories.isNotEmpty()) {
            viewModel.recalcProgress(selectedVoiceName) // suspend call
            // If you also want to upload here, you can call another VM method after this.
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {// We refresh the cache state every time the screen enters the RESUMED state. to get accurate stats
                viewModel.refreshCacheState(selectedVoiceName)
                viewModel.connectToBilling()
            }else if (event == Lifecycle.Event.ON_PAUSE) {//reliable signal that the user is leaving the screen.
                viewModel.saveDataOnExit()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // This is called when the composable leaves the screen
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    // a) Show the snackbar and wait for its result
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        // Optional: make it stay longer on screen since it has an action
                        duration = SnackbarDuration.Short
                    )

                    // b) Check if the user tapped the action button
                    if (result == SnackbarResult.ActionPerformed) {/*just close*/ }
                }
            }
        }
    }


    val uiState by viewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Derive UI-specific lists from the state. `remember` ensures this
    // calculation only re-runs when the categories list changes.
    val categories = uiState.categories
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

    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()

    Scaffold(

        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                // Apply the error colors ONLY to the Snackbar component itself.
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    ) { innerPadding ->
        // This is the main content area of your screen.
        // It will have the correct, non-red background.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // IMPORTANT: Apply the padding from the Scaffold
        ) {
            //
            // --- ALL OF YOUR SCREEN'S UI GOES HERE ---
            // e.g., Your LazyRow with MenuItemChip,
            // your LazyColumn with vocab words, etc.
            //
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else {
                var selectedChipTitle by remember(menuItems) {
                    mutableStateOf(menuItems.firstOrNull() ?: "")
                }
                Column(modifier = Modifier.fillMaxSize()) {

                    // Horizontal scrolling menu
                    if (tabIdentifier != null){
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(menuItems, key = { it }) { title ->
                                // --- 3. PASS THE `isSelected` STATE DOWN AND UPDATE IT ON CLICK ---
                                MenuItemChip(
                                    text = title,

                                    isSelected = (title == selectedChipTitle), // Calculate if this chip is selected
                                    onClick = {
                                        // First, update our state to the newly clicked title
                                        selectedChipTitle = title

                                        // Then, perform the original scroll action
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

                    CacheProgressBar(
                        cachedCount = uiState.cachedAudioCount,
                        totalCount = uiState.totalWordsInTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 64.dp, vertical = 8.dp)
                    )

                    // Vertically scrolling list with vocabulary
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = lazyListState,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        categories.forEach { category ->
                            stickyHeader {
                                CategoryHeader(title = category.title.removeContentInBracketsAndTrim())
                            }

                            items(category.words, key = { "${it.id}-${it.word}" }) { word ->
                                val sentenceToShow = word.sentences.firstOrNull()

                                if (sentenceToShow != null) {
                                    val uniqueSentenceId = generateUniqueSentenceId(word, sentenceToShow, selectedVoiceName)
                                    val isDownloading = uiState.downloadingSentenceId == uniqueSentenceId

                                    SwipeableVocabRow(
                                        word = word,
                                        sentence = sentenceToShow,
                                        selectedVoiceName = selectedVoiceName,
                                        isDownloading = isDownloading,
                                        recalledWordKeys = uiState.recalledWordKeys,
                                        cachedAudioWordKeys = uiState.cachedAudioWordKeys, // Pass the new state
                                        onRowTapped = { w, s -> viewModel.onRowTapped(w, s) }, // Call ViewModel's function
                                        onFocus = { viewModel.onFocusClicked(word) },
                                        onCancel = { viewModel.onCancelClicked(word) },
                                        onMore = {
                                            // This is where you will execute your logic, like showing a bottom sheet.
                                            // For now, it can be a log or a call to a new ViewModel function.
                                            println("More button swiped for word: ${word.word}")
                                            // Example: viewModel.onMoreClicked(word)

                                            selectedWordForSheet = word
                                            showBottomSheet = true
                                        }
                                    )
                                } else {
                                    Text(
                                        text = "Error: No sentence found for '${word.word}'",
                                        color = Color.Red,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                    HorizontalDivider()
                                }
                            }
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
                            onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                            onCloseSheet = { viewModel.hideHourlyRateLimitSheet() }
                        )
                    }
                }
                if (showBottomSheet) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            // This is called when the user swipes the sheet down or taps the scrim
                            showBottomSheet = false
                        },
                        sheetState = bottomSheetState
                    ) {
                        // The content of the sheet. We only compose it if we have a word.
                        selectedWordForSheet?.let { word ->
                            SentencesBottomSheetContent(
                                word = word,
                                onBottomSheetRowTapped = { word, sentence ->
                                    Timber.i("${word.word}")
                                    viewModel.onRowTapped(word, sentence) //redirect
                                }
                            )


                        }
                    }
                }
            }
        }
    }




}


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
fun CacheProgressBar(
    cachedCount: Int,
    totalCount: Int,
    displayIfZero:Boolean = false,
    displayLowNumber:Boolean = true,
    modifier: Modifier = Modifier
) {
    // This 'if' check replaces SwiftUI's .opacity() modifier.
    // The entire composable will not be part of the UI if the count is zero.
    if (!displayIfZero || cachedCount > 0) {
        // Calculate progress as a float between 0.0 and 1.0
        val progress = if (totalCount > 0) {
            cachedCount.toFloat() / totalCount.toFloat()
        } else {
            0f // Avoid division by zero
        }

        Row(
            modifier = modifier.height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (displayLowNumber && cachedCount > 0) {
                Text(
                    text = "$cachedCount",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            // Use a weight modifier to make the progress bar fill the available space
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp) ,
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant


            )
            Text(
                text = "$totalCount",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}


@Composable
fun SentencesBottomSheetContent(
    // 1. The composable takes the selected word as its input
    word: VocabWord,
    onBottomSheetRowTapped: (VocabWord, Sentence) -> Unit,
    modifier: Modifier = Modifier
) {
    // Use a Column with vertical scroll in case sentences are long
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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

        HorizontalDivider()

        // 3. Loop through and display each sentence
        word.sentences.forEach { sentence ->
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                val displayData = buildSentenceParts(entry = word, sentence = sentence)
               // val uniqueSentenceId = generateUniqueSentenceId(word, sentence, googleVoice)
                Column(modifier = Modifier.clickable { onBottomSheetRowTapped(word, sentence) }) {
                    HighlightedWordInSentenceRow(
                        entry = word,
                        parts = displayData.parts,
                        sentence = displayData.sentence,
                        isRecalling = false,
                        displayDot = false,//achedAudioWordKeys.contains(uniqueSentenceId),
                        isDownloading = false//, //TODO: maybe dynamic?
                    )
                }
            }
        }

        // Add some space at the bottom for better scrolling
        Spacer(Modifier.height(32.dp))
    }
}