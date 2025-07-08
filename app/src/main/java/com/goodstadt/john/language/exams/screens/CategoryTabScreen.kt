package com.goodstadt.john.language.exams.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.screens.utils.buildSentenceParts
import com.goodstadt.john.language.exams.ui.theme.Orange
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A screen that displays a horizontal menu and a vertically scrolling,
 * sectioned list of vocabulary words, with programmatic scrolling.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryTabScreen(
    menuItems: List<String>,
    categories: List<Category>,
    categoryIndexMap: Map<String, Int>,
    playbackState: PlaybackState,
    selectedVoiceName: String, // <-- MODIFICATION 1: Add new parameter
    onRowTapped: (word: VocabWord, sentence: Sentence) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal scrolling menu at the top
        LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(menuItems, key = { it }) { title ->
                MenuItemChip(
                        text = title,
                        onClick = {
                            scrollToCategory(
                                    title = title,
                                    coroutineScope = coroutineScope,
                                    lazyListState = lazyListState,
                                    indexMap = categoryIndexMap
                            )
                        }
                )
            }
        }

        // Vertically scrolling list with sticky headers for each category
        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            categories.forEach { category ->
                stickyHeader {
                    CategoryHeader(title = category.title)
                }

                items(category.words, key = { "${it.id}-${it.word}" }) { word ->
                    val sentenceToShow = word.sentences.firstOrNull()

                    Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sentenceToShow?.let { onRowTapped(word, it) }
                                }
                    ) {
                        if (sentenceToShow != null) {
                            // Only proceed if we have a valid voice name from preferences
                            if (selectedVoiceName.isNotEmpty()) { // <-- MODIFICATION 2: Safety check
                                val displayData = buildSentenceParts(entry = word, sentence = sentenceToShow)

                                // <-- MODIFICATION 3: Use the parameter instead of hardcoded value
                                val uniqueSentenceId = generateUniqueSentenceId(word, sentenceToShow, selectedVoiceName)

                                val isPlaying = playbackState is PlaybackState.Playing &&
                                        playbackState.sentenceId == uniqueSentenceId

                                VocabRow(
                                        entry = word,
                                        parts = displayData.parts,
                                        sentence = displayData.sentence,
                                        isRecalling = false,
                                        displayDot = false,
                                        wordCount = 0,
                                        isPlaying = isPlaying
                                )
                            }
                        } else {
                            Text(
                                    text = "Error: No sentence found for '${word.word}'",
                                    color = Color.Red,
                                    modifier = Modifier.padding(vertical = 12.dp)
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
}


// Helper functions (scrollToCategory, CategoryHeader, VocabRow) remain unchanged.
// ...
@Composable
fun CategoryHeader(title: String) {
    Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Orange, // <-- Set the text color to Orange
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp) // <-- Increased vertical padding for more space
    )
}

@Composable
fun VocabRow(
    entry: VocabWord,
    parts: List<String>,
    sentence: String,
    isRecalling: Boolean,
    displayDot: Boolean,
    wordCount: Int,
    isPlaying: Boolean
) {
    val annotatedString = buildAnnotatedString {
        when (parts.size) {
            2 -> {
                append(parts[0])
                withStyle(
                        style = SpanStyle(
                                color = Color.Cyan, // .teal in Compose is often Cyan
                                textDecoration = TextDecoration.Underline
                        )
                ) {
                    append(entry.word)
                }
                append(parts[1])
            }
            3 -> {
                val words = entry.word.split(",").map { it.trim() }
                if (words.size >= 2) {
                    append(parts[0])
                    withStyle(style = SpanStyle(color = Color.Cyan, textDecoration = TextDecoration.Underline)) {
                        append(words[0])
                    }
                    append(parts[1])
                    withStyle(style = SpanStyle(color = Color.Cyan, textDecoration = TextDecoration.Underline)) {
                        append(words[1])
                    }
                    append(parts[2])
                } else {
                    append(sentence)
                }
            }
            else -> {
                append(sentence)
            }
        }
    }

    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = annotatedString, modifier = Modifier.weight(1f))

        if (isPlaying) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            if (displayDot) {
                Text(text = "🔴", fontSize = 12.sp)
                if (wordCount > 0) {
                    Text(text = "$wordCount", fontSize = 10.sp)
                }
            }
            if (isRecalling) {
                Text(text = "🟢", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
    Divider()
}

private fun scrollToCategory(
    title: String,
    coroutineScope: CoroutineScope,
    lazyListState: LazyListState,
    indexMap: Map<String, Int>
) {
    coroutineScope.launch {
        val index = indexMap[title] ?: return@launch
        lazyListState.animateScrollToItem(index = index)
    }
}