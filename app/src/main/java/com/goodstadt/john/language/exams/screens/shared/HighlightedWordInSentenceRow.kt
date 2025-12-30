package com.goodstadt.john.language.exams.screens.shared

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HighlightedWordInSentenceRow(
    word: String,
    parts: List<String>,
    sentence: String,
    isRecalling: Boolean,
    displayDot: Boolean,
    playCount: Int,
    isDownloading:Boolean,
    modifier: Modifier = Modifier
) {
    // This logic builds the styled text with underlined words.
    val annotatedString = annotatedSentence(parts, word, sentence)

    // This Row lays out the text and the status indicators.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp), //let parent handle this
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The main text content
        Text(text = annotatedString, modifier = Modifier.weight(1f),lineHeight = 20.sp )

        // Status indicators on the right
        if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            // Check if the red dot should be displayed
//            val displayRedDot = displayDot

            if (isRecalling) {
                Text(text = "🟢", fontSize = 12.sp)
            }

            if (playCount == 0) {
                // do nothing  // 95% of cases exit here
            } else if (playCount in 1..9) {
                Text(text = "🔴", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
//                Text(text = "$playCount", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            } else if (playCount >= 10) {
                Text(text = "🟡", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }



        }
    }
    //HorizontalDivider()
}