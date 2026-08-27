package com.goodstadt.john.language.exams.screens.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    // Optional spaced-repetition stage: when non-null it drives the dot (0 = none, 1 = red, 2 = amber,
    // 3 = green) INSTEAD of playCount. null (default) keeps the existing playCount-based dot for every
    // other screen.
    masteryLevel: Int? = null,
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

            if (masteryLevel != null) {
                // Spaced-repetition dot: 0 none, 1 red, 2 amber, 3 green (independent of raw playCount).
                // Drawn (not emoji) so red/amber/green look consistently glossy - the 🟠/🟢 emoji render
                // flat on Android's emoji font.
                val masteryColor = when (masteryLevel) {
                    1 -> Color(0xFFE53935) // red
                    2 -> Color(0xFFFFB300) // amber
                    3 -> Color(0xFF43A047) // green
                    else -> null
                }
                if (masteryColor != null) {
                    ShinyDot(color = masteryColor, modifier = Modifier.padding(start = 4.dp))
                }
            } else if (playCount == 0) {
                // do nothing  // 95% of cases exit here
            } else if (playCount in 1..9) {
                Text(text = "🔴", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
//                Text(text = "$playCount", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            } else if (playCount >= 10) {
                Text(text = "⭐", fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            }



        }
    }
    //HorizontalDivider()
}

/**
 * A small glossy coloured dot: a filled circle with an offset translucent-white highlight, so red, amber
 * and green all read as "shiny" and consistent - unlike the 🟠/🟢 emoji, which render flat on Android.
 */
@Composable
private fun ShinyDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.TopStart
    ) {
        // Highlight - a small pale circle towards the top-left gives the glossy sphere look.
        Box(
            modifier = Modifier
                .padding(start = 2.5.dp, top = 2.5.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.7f))
        )
    }
}