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
import androidx.compose.material3.Divider
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
import com.goodstadt.john.language.exams.screens.utils.buildSentencePartsSimple

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
    modifier: Modifier = Modifier
) {

    val isRecalling = recalledWordKeys.contains(word.word)
    val displayDot = cachedAudioWordKeys.contains(word.word)

    // --- CHANGE 1: Use the new state remember function ---
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            // --- CHANGE 2: Use the new enum value ---
            if (it == SwipeToDismissBoxValue.EndToStart) { // Swiped from right-to-left
                if (isRecalling) {
                    onCancel()
                } else {
                    onFocus()
                }
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
        enableDismissFromStartToEnd = false,
        // --- CHANGE 4: 'background' is renamed to 'backgroundContent' ---
        backgroundContent = {
            // This is the view that is revealed behind the row.
            // The SwipeBackground composable you created earlier works here perfectly.
            SwipeBackground(isRecalling = isRecalling)
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
            VocabRow(
                entry = word,
                parts = displayData.parts,
                sentence = displayData.sentence,
                isRecalling = isRecalling,
                displayDot = displayDot,
                cachedAudioWordKeys ,
                //wordCount = 0,
                isDownloading = isDownloading
            )
        }
    }
}
@Composable
fun SwipeBackground(isRecalling: Boolean, modifier: Modifier = Modifier) {
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
fun VocabRow(
    entry: VocabWord,
    parts: List<String>,
    sentence: String,
    isRecalling: Boolean,
    displayDot: Boolean,
    cachedAudioWordKeys: Set<String>,
    isDownloading:Boolean
) {
    // This logic builds the styled text with underlined words.
    val annotatedString = annotatedSentence(parts, entry.word, sentence)

    // This Row lays out the text and the status indicators.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The main text content
        Text(text = annotatedString, modifier = Modifier.weight(1f))

        // Status indicators on the right
        if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            // Check if the red dot should be displayed
            val displayRedDot = cachedAudioWordKeys.contains(entry.word)

            if (displayRedDot) {
                Text(text = "🔴", fontSize = 12.sp)
            }
            // Check if the green dot should be displayed
            if (isRecalling) {
                Text(text = "🟢", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
    HorizontalDivider()
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
