package com.goodstadt.john.language.exams.screens.reference.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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

    val expandedCategories = remember {
        mutableStateOf(setOf(categories.firstOrNull()?.title).filterNotNull().toSet())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {

        categories.forEach { category ->
            stickyHeader {
                val isExpanded = expandedCategories.value.contains(category.title)
//                CategoryHeader(title = category.title)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface) // Important for sticky headers
                        .clickable {
                            // The toggle logic is the same
                            val currentSet = expandedCategories.value.toMutableSet()
                            if (isExpanded) {
                                currentSet.remove(category.title)
                            } else {
                                currentSet.add(category.title)
                            }
                            expandedCategories.value = currentSet
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // This is your original, simple CategoryHeader content
                    Text(
                        text = category.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.weight(1f) // Text takes up most of the space
                    )

                    // Add the expand/collapse icon
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            } //: StickyHeader

            if (expandedCategories.value.contains(category.title)) {
                // 1. Create a new, flat list where each element is a pairing of a word and one of its sentences.
//                val wordSentencePairs = category.words.flatMap { word ->
//                    // For each word, create a list of pairs, then flatMap will merge all these lists together.
//                    word.sentences.map { sentence ->
//                        Pair(word, sentence)
//                    }
//                }
                category.words.forEach { word ->

                    if (word.definition.isNotBlank()) {
                    item(key = "def-${word.id}") { // A unique key for this item
                        Text(
                            text = word.definition,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, // A less prominent color
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }


                // 2. Now use the standard `items` call on this new flat list.
                    items(
                        items = word.sentences,
                        key = { sentence -> "sent-${word.id}-${sentence.sentence}" } // A unique key for each sentence
                    ) { sentence ->
                        // Your existing sentence row logic can be placed here.
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

}