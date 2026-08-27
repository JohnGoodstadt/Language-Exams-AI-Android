package com.goodstadt.john.language.exams.screens.shared


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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

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

// --- Your project-specific classes ---
// You will need to add the imports for your own models and utility functions, for example:
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.Format0Word


import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodstadt.john.language.exams.utils.buildSentencePartsSimple
import timber.log.Timber

//import com.google.android.material.progressindicator.CircularProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableVocabRow(
    word: Format0Word,
    sentence: Sentence,
    isSentenceAlreadyHeard: Boolean, // ✅ Driven by HistorySyncManager
    isDownloading: Boolean = false,
    playCount:Int = 0,
    // Spaced-repetition stage (0 none, 1 red, 2 amber, 3 green). When non-null it drives the row's dot.
    masteryLevel: Int? = null,
    recalledWordKeys: Set<String>,
    onRowTapped: (Format0Word, Sentence) -> Unit,
    onFocus: () -> Unit,
    onCancel: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isRecalling = recalledWordKeys.contains(word.word)

    // ✅ FIX: Capture the latest values in a State holder.
    // This allows the 'rememberSwipeToDismissBoxState' lambda to read the
    // CURRENT value, not the value from when the row was first drawn.
    val currentIsRecalling by rememberUpdatedState(isRecalling)
    val currentOnFocus by rememberUpdatedState(onFocus)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val currentOnMore by rememberUpdatedState(onMore)
    // --- CHANGE 1: Use the new state remember function ---

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                // Swiped from right-to-left
                SwipeToDismissBoxValue.EndToStart -> {
                    Timber.e("isRecalling $isRecalling currentIsRecalling $currentIsRecalling")
                    if (currentIsRecalling) {
                        currentOnCancel()
                    } else {
                        currentOnFocus()
                    }
                }
                // ADDED: Swiped from left-to-right
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentOnMore()
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
        enableDismissFromEndToStart = false, //TODO: temp disable
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {

            HighlightedWordInSentenceRow(
                word = word.word,
                parts = displayData.parts,
                sentence = displayData.sentence,
                isRecalling = isRecalling,
                displayDot = isSentenceAlreadyHeard,
                playCount = playCount,
                isDownloading = isDownloading,
                masteryLevel = masteryLevel
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