package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.screens.CategoryHeader
import com.goodstadt.john.language.exams.screens.RateLimitDailyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.VocabRow
import com.goodstadt.john.language.exams.utils.buildSentenceParts
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.ConjugationsUiState
import com.goodstadt.john.language.exams.viewmodels.ConjugationsViewModel
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet

//import com.goodstadt.john.language.exams.viewmodels.PlaybackState

@Composable
fun ConjugationsScreen(viewModel: ConjugationsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()

    when (val state = uiState) {
        is ConjugationsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ConjugationsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = Color.Red)
            }
        }
        is ConjugationsUiState.NotAvailable -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This feature is not available for the current language.")
            }
        }
        is ConjugationsUiState.Success -> {
            SectionedVocabList(
                    categories = state.categories,
                    playbackState = playbackState,
                    googleVoice = state.selectedVoiceName,
                    onRowTapped = { word, sentence ->
                        viewModel.playTrack(word, sentence)
                    }
            )
        }
    }
    if (isRateLimitingSheetVisible){
        RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
    }
    if (isDailyRateLimitingSheetVisible){
//        RateLimitDailyReasonsBottomSheet (onCloseSheet = { viewModel.hideDailyRateLimitSheet() })
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

/**
 * A reusable composable for displaying a sectioned list of vocabulary.
 * This is effectively the list part of our Tab 1 screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionedVocabList(
    categories: List<Category>,
    playbackState: PlaybackState,
    googleVoice:String,
    onRowTapped: (VocabWord, Sentence) -> Unit
) {
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
// ... inside your LazyColumn scope ...

        categories.forEach { category ->
            stickyHeader {
                CategoryHeader(title = category.title)
            }

            // 1. Create a new, flat list where each element is a pairing of a word and one of its sentences.
            val wordSentencePairs = category.words.flatMap { word ->
                // For each word, create a list of pairs, then flatMap will merge all these lists together.
                word.sentences.map { sentence ->
                    Pair(word, sentence)
                }
            }

            // 2. Now use the standard `items` call on this new flat list.
            items(
                    items = wordSentencePairs,
                    key = { (word, sentence) -> "${word.id}-${sentence.sentence}" } // Destructure the pair for the key
            ) { (word, sentence) -> // Destructure the pair for use in the body

                // Your existing logic now works perfectly here
                val displayData = buildSentenceParts(entry = word, sentence = sentence)
                //val googleVoice = viewModel.getCurrentGoogleVoice()
                val uniqueSentenceId = generateUniqueSentenceId(word, sentence, googleVoice)

                val isPlaying = playbackState is PlaybackState.Playing &&
                        playbackState.sentenceId == uniqueSentenceId

                Column(modifier = Modifier.clickable { onRowTapped(word, sentence) }) {
                    VocabRow(
                        entry = word,
                        parts = displayData.parts,
                        sentence = displayData.sentence,
                        isRecalling = false,
                        displayDot = false,
//                            wordCount = 0,
                        isDownloading = false, //TODO: maybe dynamic?
                        cachedAudioWordKeys = setOf()
                    )
                }
            }
        }
    }

}