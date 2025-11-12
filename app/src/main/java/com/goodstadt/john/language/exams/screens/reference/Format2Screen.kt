package com.goodstadt.john.language.exams.screens.reference


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.models.Format2Level
import com.goodstadt.john.language.exams.utils.annotatedSentenceByWords

/**
 * A "dumb" Composable screen that displays data in the "Format2" structure.
 * It receives its state from a parent composable.
 *
 * @param title The main title for the screen, from the root Format2File object.
 * @param description The main subtitle for the screen.
 * @param levels The list of sections (`Format2Level`) to display.
 * @param onRowTapped A callback for when a sentence row is tapped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Format2Screen(
    viewModel: Format2ViewModel = hiltViewModel(),
    title: String,
    description: String,
    levels: List<Format2Level>,
//    onRowTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // LazyColumn is the efficient Composable for displaying the main scrollable list.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp), // This adds space BETWEEN the main gray boxes
        contentPadding = PaddingValues(vertical = 32.dp)
    ) {
        // --- 1. Top-Level Title and Description ---
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
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
        levels.forEach { level ->

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
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // b) Add the items (the word entries) for the current level
            items(
                items = level.wordsAndSentences,
                key = { entry -> "${entry.word}-${entry.definition}" } // unique within a level
            ) { entry ->
                // This Column represents a single row block for a Format2Entry
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),

                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Loop through the sentences for this entry
                    entry.sentences.forEachIndexed { index, item ->
                        // Your Format2RowView or a similar composable would go here.
                        // For now, let's build it directly.
                        Format2Row(
                            word = entry.word,
                            sentence = item.sentence,
//                            onTapped = { onRowTapped(sentence.sentence) },
                            onTapped = { viewModel.playTrack(item.sentence)},

                            modifier = Modifier.padding(start = 16 .dp)
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
            }
        }
    }
}

/**
 * A helper composable for displaying a single row within the Format2Screen.
 */
@Composable
private fun Format2Row(
    word: String,
    sentence: String,
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
        Text(text = styledSentence)
    }
}