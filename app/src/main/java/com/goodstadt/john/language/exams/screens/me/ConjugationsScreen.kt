package com.goodstadt.john.language.exams.screens.me

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.screens.CategoryHeader
import com.goodstadt.john.language.exams.screens.VocabRow
import com.goodstadt.john.language.exams.screens.utils.buildSentenceParts
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.ConjugationsUiState
import com.goodstadt.john.language.exams.viewmodels.ConjugationsViewModel
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
//import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import hilt_aggregated_deps._com_goodstadt_john_language_exams_viewmodels_ConjugationsViewModel_HiltModules_BindsModule

@Composable
fun ConjugationsScreen(viewModel: ConjugationsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

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
                        wordsOnDisk = setOf()
                    )
                }
            }
        }
    }
}