package com.goodstadt.john.language.exams.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.models.VocabWord

@Composable
fun HighlightedWordInSentenceRow(
    entry: VocabWord,
    parts: List<String>,
    sentence: String,
    isRecalling: Boolean,
    displayDot: Boolean,
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
//            val displayRedDot = cachedAudioWordKeys.contains(entry.word)
            val displayRedDot = displayDot

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