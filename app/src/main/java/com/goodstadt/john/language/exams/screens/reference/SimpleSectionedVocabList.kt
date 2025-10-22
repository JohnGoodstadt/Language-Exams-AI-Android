package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        // Loop through the categories to build the sections
        categories.forEach { category ->

            // 1. The Sticky Header for the category title
            stickyHeader {
                // MODIFIED: Removed the clickable modifier and expand/collapse icon.
                // It's now just a simple, non-interactive header row.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface) // Keep background for sticky effect
                        .padding(vertical = 16.dp)
                ) {
                    Text(
                        text = category.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.weight(1f) // Text takes up most of the space
                    )
                }
            }

            // 2. The Content for the category (now always visible)
            // MODIFIED: Removed the 'if (expandedCategories.value.contains(category.title))' check.
            category.words.forEach { word ->

                // The definition sub-header item
                if (word.definition.isNotBlank()) {
                    item(key = "def-${word.id}") {
                        Text(
                            text = word.definition,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 0.dp, bottom = 8.dp)
                        )
                    }
                }

                // The sentence items for this word
                items(
                    items = word.sentences,
                    key = { sentence -> "sent-${word.id}-${sentence.sentence}" }
                ) { sentence ->
                    val displayData = buildSentenceParts(entry = word, sentence = sentence)
                    val uniqueSentenceId = generateUniqueSentenceId(word, sentence, googleVoice)

                    Column(modifier = Modifier.clickable { onRowTapped(word, sentence) }) {
                        HighlightedWordInSentenceRow(
                            entry = word,
                            parts = displayData.parts,
                            sentence = displayData.sentence,
                            isRecalling = false,
                            displayDot = cachedAudioWordKeys.contains(uniqueSentenceId),
                            isDownloading = false
                        )
                    }
                }
            }
        }
    }
}