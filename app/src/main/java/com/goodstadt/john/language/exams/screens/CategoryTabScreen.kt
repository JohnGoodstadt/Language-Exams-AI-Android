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
    categoryIndexMap: Map<String, Int>, // Receive the pre-calculated index map
    playbackState: PlaybackState, // <-- Add playback state
    onRowTapped: (word: VocabWord, sentence: Sentence) -> Unit // <-- Add click handler
) {
    // Create and remember the state for the LazyColumn
    val lazyListState = rememberLazyListState()
    // Create and remember a coroutine scope for launching the scroll action
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Horizontal scrolling menu at the top
        LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(menuItems, key = { it }) { title ->
                MenuItemChip(
                        text = title,
                        onClick = {
                            // When a chip is clicked, find its index and scroll
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

        // 2. Vertically scrolling list with sticky headers for each category
// ... inside CategoryTabScreen.kt ...

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

                // *** NEW, REVISED LOGIC FOR RENDERING ROWS ***
                items(category.words, key = { "${it.id}-${it.word}" }) { word ->
                    // For each word, we must decide which sentence to show.
                    // For now, we will robustly pick the first sentence if it exists.
                    val sentenceToShow = word.sentences.firstOrNull()

                    Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    println("Row for '${word.sentences[0].sentence}' tapped!")
                                    sentenceToShow?.let { onRowTapped(word, it) }
                                }
                    ) {
                        if (sentenceToShow != null) {
                            // 1. Prepare the data by calling our helper function
                            val displayData = buildSentenceParts(entry = word, sentence = sentenceToShow)
                            val uniqueSentenceId = generateUniqueSentenceId(word, sentenceToShow)

                            // 2. Call the new, complex VocabRow composable
                            val isPlaying = playbackState is PlaybackState.Playing &&
                                    playbackState.sentenceId == uniqueSentenceId

                            VocabRow(
                                    entry = word,
                                    parts = displayData.parts,
                                    sentence = displayData.sentence,
                                    // Hardcoding these as per the Swift example for now
                                    isRecalling = false,
                                    displayDot = false,
                                    wordCount = 0,
                                    isPlaying = isPlaying // <-- Pass the playing state down
                            )
                        } else {
                            // A fallback for words that have no sentences
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

// ... (CategoryHeader composable remains the same) ...

// *** DELETE THE OLD, SIMPLE WordRow COMPOSABLE IF IT'S STILL HERE ***
// *** ADD THE NEW, COMPLEX VocabRow COMPOSABLE FROM STEP 2 HERE ***
    }
}

// A helper function to keep the onClick logic clean
private fun scrollToCategory(
    title: String,
    coroutineScope: CoroutineScope,
    lazyListState: LazyListState,
    indexMap: Map<String, Int>
) {
    // Launch a coroutine to call the suspend function `scrollToItem`
    coroutineScope.launch {
        // Find the index from our map
        val index = indexMap[title] ?: return@launch // Do nothing if title not found
        // Command the LazyColumn to scroll to the item at that index
        lazyListState.animateScrollToItem(index = index)
    }
}


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

// ... (at the bottom of CategoryTabScreen.kt, alongside CategoryHeader)

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
    // buildAnnotatedString is the Jetpack Compose equivalent of mixing styled Text
    val annotatedString = buildAnnotatedString {
        when (parts.size) {
            // Case for a single word split (e.g., "part1", "part2")
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
            // Case for a two-word split (e.g., "part1", "part2", "part3")
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
                    // Fallback if the word format is unexpected
                    append(sentence)
                }
            }
            // Fallback for any other case
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

        // Display the dots based on the boolean flags
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

@Composable
fun WordRow(word: VocabWord) {
    Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = word.word, fontWeight = FontWeight.SemiBold)
            Text(text = word.translation, style = MaterialTheme.typography.bodySmall)
        }
    }
    Divider()
}