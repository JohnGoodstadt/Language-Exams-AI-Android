package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.screens.VocabRow
import com.goodstadt.john.language.exams.screens.utils.buildSentenceParts
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import com.goodstadt.john.language.exams.viewmodels.SearchViewModel
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Input text field
        OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                label = { Text("Search for a word...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
        )

        // List of results
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(searchResults, key = { "${it.word.id}-${it.word.word}" }) { result ->
                val displayData = buildSentenceParts(result.word, result.word.sentences.first())
                val googleVoice = "en-GB-Neural2-C"
                val uniqueSentenceId = generateUniqueSentenceId(result.word, result.word.sentences.first(),googleVoice)

                val isPlaying = playbackState is PlaybackState.Playing &&
                        (playbackState as PlaybackState.Playing).sentenceId == uniqueSentenceId

                // We can reuse the VocabRow from the other screen
                Column(modifier = Modifier.clickable { viewModel.playTrack(result) }) {
                    VocabRow(
                            entry = result.word,
                            parts = displayData.parts,
                            sentence = displayData.sentence,
                            isRecalling = false,
                            displayDot = false,
                            wordsOnDisk = setOf(),
                            isDownloading = false//isPlaying
                    )
                }
            }
        }
    }
}