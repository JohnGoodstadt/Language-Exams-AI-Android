package com.goodstadt.john.language.exams.packages.SavedPractice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.screens.shared.HighlightedWordInSentenceRow
import com.goodstadt.john.language.exams.utils.buildSentenceParts

/**
 * "Saved" sub-tab on the Me tab: lists the vocab entries the user swiped to Save on the vocab tabs, for
 * practice. Each entry shows the word heading, definition, pronunciation and its sentences (tap to play),
 * with a Remove button. Level-aware (only the current level's saved words).
 */
@Composable
fun SavedPracticeScreen(viewModel: SavedPracticeViewModel = hiltViewModel()) {
    val saved by viewModel.saved.collectAsState()

    // DEBUG-only: dump the outstanding practice reminders to logcat each time the screen is shown.
    LaunchedEffect(saved) { viewModel.logOutstandingReminders() }

    if (saved.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No saved words yet.\n\nSwipe a word LEFT on the Vocab tabs and tap Save to add it " +
                    "here for practice.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Reminders that have come due float to the top (like a triggered Mail reminder); everything else stays
    // newest-first. Evaluated against the clock at composition, so opening the screen re-orders correctly.
    val now = System.currentTimeMillis()
    val ordered = remember(saved, now) {
        val (triggered, rest) = saved.partition { it.reminderAt in 1..now }
        triggered.sortedByDescending { it.reminderAt } + rest
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(ordered, key = { it.id }) { entry ->
            entry.word?.let { word ->
                SavedWordCard(
                    word = word,
                    isReminderDue = entry.reminderAt in 1..now,
                    onPlay = { sentence -> viewModel.play(entry, sentence) },
                    onRemove = { viewModel.remove(entry) }
                )
            }
            HorizontalDivider()
        }
    }
}

/**
 * The saved-entry card: word heading, definition, pronunciation, its sentences, then a Remove button.
 * When [isReminderDue] the word line carries a grey "REMIND ME" label on the right (cleared once the user
 * plays a sentence).
 */
@Composable
private fun SavedWordCard(
    word: Format0Word,
    isReminderDue: Boolean,
    onPlay: (String) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = word.word,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Cyan, // matches the highlighted word in the sentences
                modifier = Modifier.weight(1f)
            )
            if (isReminderDue) {
                Text(
                    text = "REMIND ME",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // grey
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        if (word.definition.isNotEmpty()) {
            Text(
                text = word.definition,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (word.pronounce.isNotEmpty()) {
            Text(
                text = word.pronounce,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (word.IPA.isNotEmpty()) {
            Text(
                text = word.IPA,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(4.dp))

        // The sentences in the entry (usually 3); tap a row to play it.
        word.sentences.forEach { sentence ->
            val displayData = buildSentenceParts(entry = word, sentence = sentence)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(sentence.sentence) }
                    .padding(vertical = 6.dp)
            ) {
                HighlightedWordInSentenceRow(
                    word = word.word,
                    parts = displayData.parts,
                    sentence = displayData.sentence,
                    isRecalling = false,
                    displayDot = false,
                    playCount = 0,
                    isDownloading = false
                )
            }
        }

        // Remove from the saved list (also cancels any pending reminder).
        OutlinedButton(
            onClick = onRemove,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Text(text = "Remove")
        }
    }
}
