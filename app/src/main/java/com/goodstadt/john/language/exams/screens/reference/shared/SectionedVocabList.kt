package com.goodstadt.john.language.exams.screens.reference.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.screens.CategoryHeader
import com.goodstadt.john.language.exams.screens.VocabRow
import com.goodstadt.john.language.exams.utils.buildSentenceParts
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.PlaybackState

/*
**
* A reusable composable for displaying a sectioned list of vocabulary.
* This is effectively the list part of our Tab 1 screen.
*/
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionedVocabList(
    categories: List<Category>,
    playbackState: PlaybackState,
    googleVoice:String,
    cachedAudioWordKeys:Set<String>,
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
//                val googleVoice = viewModel.getCurrentGoogleVoice()
                val uniqueSentenceId = generateUniqueSentenceId(word, sentence, googleVoice)

//                val isPlaying = playbackState is PlaybackState.Playing &&
//                        playbackState.sentenceId == uniqueSentenceId

                Column(modifier = Modifier.clickable { onRowTapped(word, sentence) }) {
                    VocabRow(
                        entry = word,
                        parts = displayData.parts,
                        sentence = displayData.sentence,
                        isRecalling = false,
                        displayDot = cachedAudioWordKeys.contains(uniqueSentenceId),
                        isDownloading = false//, //TODO: maybe dynamic?
                    )
                }
            }
        }
    }

}