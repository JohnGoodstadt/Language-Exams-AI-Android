package com.goodstadt.john.language.exams.packages.reference

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
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.screens.shared.HighlightedWordInSentenceRow
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.utils.buildSentenceParts
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleSectionedVocabListOriginal(
    categories: List<Category>,
    playbackState: PlaybackState,
    googleVoice: String,
    cachedAudioWordKeys: Set<String>,
    onRowTapped: (Format0Word, Sentence) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Add some vertical padding between the main items
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        categories.forEach { category ->

            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = accentColor,
                        modifier = Modifier.weight(1f).padding( bottom = 0.dp, top = 32.dp) // Text takes up most of the space
                    )
                }
            }

            // MODIFIED: We now use `items` for the words, creating one block per word.
            items(
                items = category.words,
                key = { word -> "word-block-${word.id}" }
            ) { word ->
                // This Column acts as a container for the definition and the sentence box.
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. The Definition Sub-Header (remains the same)
                    if (word.definition.isNotBlank()) {
                        Text(
                            text = word.definition,
                            style = MaterialTheme.typography.bodyMedium,
//                            fontStyle = FontStyle.no,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 0.dp, bottom = 24.dp)
                        )
                    }

                    val backgroundColor = if (isSystemInDarkTheme()) {
                        // Use the specific dark gray for Dark Mode
                        Color(red = 28, green = 28, blue = 30)
                    } else {
                        // Use a theme-appropriate light gray for Light Mode
//                        MaterialTheme.colorScheme.surfaceVariant
                        MaterialTheme.colorScheme.surfaceContainerHigh

                    }

                    // 2. ✅ THE FIX: The Grouped Sentences Box
                    // This Column is the visual container for the sentences.
                    Column(
                        modifier = Modifier
                            // a) Apply rounded corners
                            .clip(RoundedCornerShape(12.dp))
                            // b) Set the background color. `surfaceVariant` is a perfect
                            //    semantic color for "slightly lighter/darker than the background".
//                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .background(backgroundColor)
                    ) {
                        // 3. Loop through the sentences INSIDE the styled Column
                        word.sentences.forEachIndexed { index, sentence ->
                            val displayData = buildSentenceParts(entry = word, sentence = sentence)
                            val uniqueSentenceId = generateUniqueSentenceId(word, sentence, googleVoice)

                            // Your existing row composable goes here
                            HighlightedWordInSentenceRow(
                                word = word.word,
                                parts = displayData.parts,
                                sentence = displayData.sentence,
                                isRecalling = false,
                                displayDot = cachedAudioWordKeys.contains(uniqueSentenceId),
                                playCount = 0,
                                isDownloading = false,
                                modifier = Modifier
                                    .clickable { onRowTapped(word, sentence) }
                                    // Add some padding inside the box
                                    .padding(horizontal = 16.dp)
                            )

                            // 4. Add a divider between items, but not after the last one
//                            if (index < word.sentences.lastIndex) {
//                                HorizontalDivider(
//                                    modifier = Modifier.padding(horizontal = 16.dp),
//                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
//                                )
//                            }
                        }
                    }
                }
            }//: items
        }
    }
}

