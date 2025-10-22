package com.goodstadt.john.language.exams.screens.reference

import android.content.res.Configuration
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.screens.HighlightedWordInSentenceRow
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.utils.buildSentenceParts
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.goodstadt.john.language.exams.ui.theme.LanguageExamsAITheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleSectionedVocabList(
    categories: List<Category>,
    playbackState: PlaybackState,
    googleVoice: String,
    cachedAudioWordKeys: Set<String>,
    onRowTapped: (VocabWord, Sentence) -> Unit
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
                                entry = word,
                                parts = displayData.parts,
                                sentence = displayData.sentence,
                                isRecalling = false,
                                displayDot = cachedAudioWordKeys.contains(uniqueSentenceId),
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
            }
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
fun SimpleSectionedVocabListPreviewLight() {
    // ✅ WRAP your content with your custom theme
    // We explicitly pass `darkTheme = false` for this preview
    LanguageExamsAITheme(darkTheme = false) {
        // It's good practice to also add a Surface to provide a background
        Surface {
            SimpleSectionedVocabList(
                // Provide sample/dummy data for the preview
                categories = sampleCategories,
                playbackState = PlaybackState.Idle,
                googleVoice = "",
                cachedAudioWordKeys = setOf(),
                onRowTapped = { _, _ -> }
            )
        }
    }
}

@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SimpleSectionedVocabListPreviewDark() {
    // ✅ WRAP your content with your custom theme
    // We explicitly pass `darkTheme = true` for this preview
    LanguageExamsAITheme(darkTheme = true) {
        Surface {
            SimpleSectionedVocabList(
                categories = sampleCategories,
                playbackState = PlaybackState.Idle,
                googleVoice = "",
                cachedAudioWordKeys = setOf(),
                onRowTapped = { _, _ -> }
            )
        }
    }
}

// Helper with some sample data for your previews to use
val sampleCategories = listOf(
    Category(
        title = "Present Tense",
        words = listOf(
            VocabWord(
                id = 1,
                sortOrder = 1,
                word = "run",
                definition = "To move at a speed faster than a walk.",
                sentences = listOf(
                    Sentence("I run every morning.", "...")
                ),
                translation = "",
                romanisation = "",
                partOfSpeech = "",
                group = ""
            )
        ),
        tabNumber = 1,
        sortOrder = 1
    )
)