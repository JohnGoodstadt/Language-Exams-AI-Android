package com.goodstadt.john.language.exams.screens


// In the file containing your SwipeableVocabRow composable

// --- Core Compose & Foundation ---
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// --- Material 3 (The key imports for the fix) ---
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox // The new component
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState // The new state remember function
import androidx.compose.material3.MaterialTheme

// --- Material Icons ---
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.SwipeToDismissBoxValue

// --- Other necessary UI imports ---
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

// --- Your project-specific classes ---
// You will need to add the imports for your own models and utility functions, for example:
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord


import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.AnnotatedString
import com.goodstadt.john.language.exams.utils.buildSentencePartsSimple

//import com.google.android.material.progressindicator.CircularProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableVocabRow(
    word: VocabWord,
    sentence: Sentence,
    selectedVoiceName: String,
    isDownloading: Boolean,
    recalledWordKeys: Set<String>,
    cachedAudioWordKeys: Set<String>,
    onRowTapped: (VocabWord, Sentence) -> Unit,
    onFocus: () -> Unit,
    onCancel: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isRecalling = recalledWordKeys.contains(word.word)
    val displayDot = cachedAudioWordKeys.contains(word.word)

    // --- CHANGE 1: Use the new state remember function ---
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                // Swiped from right-to-left
                SwipeToDismissBoxValue.EndToStart -> {
                    if (isRecalling) onCancel() else onFocus()
                }
                // ADDED: Swiped from left-to-right
                SwipeToDismissBoxValue.StartToEnd -> {
                    onMore()
                }
                // Default case for settled state
                SwipeToDismissBoxValue.Settled -> {}
            }
            // Return false to prevent the item from being dismissed and to snap it back.
            return@rememberSwipeToDismissBoxState false
        },
        // This helps prevent accidental full swipes
        positionalThreshold = { it * .25f }
    )

    // This effect remains the same, but now operates on the new state object
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    // --- CHANGE 3: Use the new SwipeToDismissBox component ---
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        // The directions logic is now part of the component itself
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = true,
        // --- CHANGE 4: 'background' is renamed to 'backgroundContent' ---
        backgroundContent = {
            val direction = dismissState.dismissDirection

            if (direction == SwipeToDismissBoxValue.StartToEnd) {
                // This is the new background for the "More" action (swipe right)
                MoreSwipeBackground()
            } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                // This is your existing background for "Focus/Cancel" (swipe left)
                FocusCancelSwipeBackground(isRecalling = isRecalling)
            }
        }
    ) { // --- CHANGE 5: 'dismissContent' is now the main content lambda ---
        // This is your actual row content
        val displayData = buildSentencePartsSimple(word = word.word, sentence = sentence.sentence)
       // val uniqueSentenceId = generateUniqueSentenceId(word, sentence, selectedVoiceName)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onRowTapped(word, sentence) }
        ) {
            HighlightedWordInSentenceRow(
                entry = word,
                parts = displayData.parts,
                sentence = displayData.sentence,
                isRecalling = isRecalling,
                displayDot = displayDot,
//                cachedAudioWordKeys ,
                //wordCount = 0,
                isDownloading = isDownloading
            )
        }
    }
}





@Composable
fun annotatedSentence(
    parts: List<String>,
    word: String,
    sentence: String
): AnnotatedString {
    val annotatedString = buildAnnotatedString {
        when (parts.size) {
            2 -> {
                append(parts[0])
                withStyle(
                    style = SpanStyle(
                        color = Color.Cyan, // Use theme color
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(word)
                }
                append(parts[1])
            }

            3 -> {
                val words = word.split(",").map { it.trim() }
                if (words.size >= 2) {
                    append(parts[0])
                    withStyle(
                        style = SpanStyle(
                            color = Color.Cyan,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(words[0])
                    }
                    append(parts[1])
                    withStyle(
                        style = SpanStyle(
                            color = Color.Cyan,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
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
    return annotatedString
}
@Composable
fun FocusCancelSwipeBackground(isRecalling: Boolean, modifier: Modifier = Modifier) {
    // Determine the color, text, and icon based on whether the item is already being recalled
    val color = if (isRecalling) Color(0xFFD32F2F) else Color(0xFF388E3C) // Red and Green
    val text = if (isRecalling) "Cancel" else "Focus"
    val icon = if (isRecalling) Icons.Default.Cancel else Icons.Default.CheckCircle

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd // Align content to the right (the end)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold)
            Icon(imageVector = icon, contentDescription = text, tint = Color.White)
        }
    }
}
@Composable
fun MoreSwipeBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF388E3C) ) // A neutral color for "More"
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart // Aligned to the LEFT
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz, // "More" icon
                contentDescription = "More",
                tint = Color.White
            )
            Text("More", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}