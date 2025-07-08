package com.goodstadt.john.language.exams.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
//import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.viewmodels.ParagraphViewModel

@Composable
fun ParagraphScreen(
    viewModel: ParagraphViewModel = hiltViewModel()
) {
    // Collect state from the ViewModel in a lifecycle-aware manner
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // This block replaces `onAppear` to trigger the initial data load.
    // `Unit` as a key means it runs only once when the composable enters the screen.
    LaunchedEffect(Unit) {
        viewModel.generateNewParagraph()
    }

    Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
                text = "Listen to this sentence.",
                style = MaterialTheme.typography.titleLarge
        )

        Text(
                text = "It's made up of words from the other pages. Listen carefully.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
        )

        Button(onClick = { viewModel.generateNewParagraph() }) {
            Text("Generate")
        }

        AnimatedVisibility(visible = uiState.isLoading) {
            CircularProgressIndicator()
        }

        Divider(modifier = Modifier.padding(top = 10.dp))

        // Sentence with highlighted words
        AttributedHighlightedText(
                paragraph = uiState.sentence,
                highlightedWords = uiState.highlightedWords,
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .clickable { viewModel.speakSentence() }
        )

        // English translation
        Text(
                text = uiState.translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Error display
        uiState.error?.let {
            Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = { viewModel.speakSentence() }) {
            Text("Play")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}