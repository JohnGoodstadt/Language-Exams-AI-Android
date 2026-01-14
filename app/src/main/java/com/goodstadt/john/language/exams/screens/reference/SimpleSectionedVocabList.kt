package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.screens.shared.HighlightedWordInSentenceRow
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.utils.buildSentenceParts
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import removeContentInBracketsAndTrim

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleSectionedVocabList(
    data: List<Category>,
    isHeard: (String) -> Boolean,
    playCount: (String) -> Int,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),

    // Actions
    onRowTapped: (Format0Word, Sentence, Category) -> Unit,
    onSideQuestTapped: () -> Unit,
    onQuizSheetTapped: () -> Unit
) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        data.forEach { category ->

            // 1. STICKY HEADER
            stickyHeader {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = category.title.removeContentInBracketsAndTrim(),
                            style = MaterialTheme.typography.titleLarge,
                            color = orangeLight,
                            modifier = Modifier.weight(1f)
                        )

                        // Stats / Side Quest Icon
                        if (category == data[0]) {
                            IconButton(onClick = onQuizSheetTapped) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = "Quiz",
                                    tint = Color(0xFFFF9800)
                                )
                            }
                            IconButton(onClick = onSideQuestTapped) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = "Stats",
                                    tint = Color(0xFFFF9800)
                                )
                            }
                        }
                    }
//                    HorizontalDivider()
                }
            }

            itemsIndexed(
                items = category.words,
                key = { index, word -> "word-block-${word.id}-$index" }
            ) { index, word ->
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
                            modifier = Modifier.padding(top = 0.dp, bottom = 16.dp, start = 12.dp)
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(backgroundColor)
                    ) {
                        // 3. Loop through the sentences INSIDE the styled Column
                        word.sentences.forEachIndexed { index, sentence ->
                            val displayData = buildSentenceParts(entry = word, sentence = sentence)
                           // val uniqueSentenceId = generateUniqueSentenceId(word, sentence, googleVoice)

                            val isSentenceAlreadyHeard = isHeard(displayData.sentence)
                            val playCount = playCount(displayData.sentence)

//                            val playCount = viewModel.getPlayCount(item.sentence)
                            // Your existing row composable goes here
                            HighlightedWordInSentenceRow(
                                word = word.word,
                                parts = displayData.parts,
                                sentence = displayData.sentence,
                                isRecalling = false,
                                displayDot = isSentenceAlreadyHeard,
                                playCount = playCount,
                                isDownloading = false,
                                modifier = Modifier
                                    .clickable {
                                        onRowTapped(word, sentence,category)
                                    }
                                    // Add some padding inside the box
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )

                        }
                    }
                }
            }//: items
        }
    }
}