package com.goodstadt.john.language.exams.screens


// In the file containing your SwipeableVocabRow composable

// --- Core Compose & Foundation ---
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
import androidx.compose.material3.SwipeToDismissBoxValue

// --- Other necessary UI imports ---
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// --- Your project-specific classes ---
// You will need to add the imports for your own models and utility functions, for example:
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.screens.utils.buildSentenceParts


import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId // Or wherever this is

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableVocabRow(
    word: VocabWord,
    sentence: Sentence,
    selectedVoiceName: String,
    isPlaying: Boolean,
    recalledWordKeys: Set<String>,
//    isRecalling: Boolean,
    onRowTapped: (VocabWord, Sentence) -> Unit,
    onFocus: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isRecalling = recalledWordKeys.contains(word.word)

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
        val displayData = buildSentenceParts(entry = word, sentence = sentence)
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
                displayDot = false,
                wordCount = 0,
                isPlaying = isPlaying
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