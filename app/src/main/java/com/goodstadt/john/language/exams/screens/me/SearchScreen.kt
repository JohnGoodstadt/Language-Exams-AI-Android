package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.screens.RateLimitDailyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.shared.HighlightedWordInSentenceRow
import com.goodstadt.john.language.exams.utils.buildSentenceParts
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import com.goodstadt.john.language.exams.viewmodels.SearchViewModel
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.SearchUiState
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import timber.log.Timber


@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
//    val searchResults by viewModel.searchResults.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Input text field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                label = { Text("Search for a word...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
//            colors = MaterialTheme {  }
            )

            Text(
                text = "All your exam words.",
                fontSize = 12.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            when (val state = uiState) {
                is SearchUiState.Loading -> {
                    Timber.e("Loading")
                }

                is SearchUiState.Success -> {
                    // List of results
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                        items(state.results, key = { "${it.word.id}-${it.word.word}" }) { result ->
                            val displayData =
                                buildSentenceParts(result.word, result.word.sentences.first())
                            val categoryTitle = result.categoryTitle
                            val categoryTabNumber = result.categoryTabNumber
                            val googleVoice = "en-GB-Neural2-C"
                            val uniqueSentenceId = generateUniqueSentenceId(
                                result.word,
                                result.word.sentences.first(),
                                googleVoice
                            )

                            val isHeard = viewModel.isHeard(displayData.sentence)
                            val playCount = viewModel.getPlayCount(displayData.sentence)

                            val isPlaying = playbackState is PlaybackState.Playing &&
                                    (playbackState as PlaybackState.Playing).sentenceId == uniqueSentenceId

                            // We can reuse the VocabRow from the other screen
                            Column(modifier = Modifier.clickable {
                                viewModel.handleTap(
                                    displayData.sentence,
                                    categoryTitle,
                                    categoryTabNumber
                                )
                            })
                            {
                                HighlightedWordInSentenceRow(
                                    word = result.word.word,
                                    parts = displayData.parts,
                                    sentence = displayData.sentence,
                                    isRecalling = false,
                                    displayDot = isHeard,
                                    playCount = playCount,
                                    isDownloading = false//isPlaying
                                )
                            }
                        }
                    }
                }

                is SearchUiState.Error -> {

                    Timber.e("Error in search")
                }
            }


            if (isRateLimitingSheetVisible) {
                RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
            }
            if (isDailyRateLimitingSheetVisible) {
//        RateLimitDailyReasonsBottomSheet (onCloseSheet = { viewModel.hideDailyRateLimitSheet() })
                if (context is androidx.activity.ComponentActivity) {
                    RateLimitDailyReasonsBottomSheet(
                        onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                        onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
                    )
                }
            }
            if (isHourlyRateLimitingSheetVisible) {
//        RateLimitHourlyReasonsBottomSheet(onCloseSheet = { viewModel.hideHourlyRateLimitSheet() })
                if (context is androidx.activity.ComponentActivity) {
                    RateLimitHourlyReasonsBottomSheet(
                        onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                        onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
                    )
                }

            }
        }
    }//: Scaffold

}