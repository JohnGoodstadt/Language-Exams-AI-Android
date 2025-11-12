package com.goodstadt.john.language.exams.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.goodstadt.john.language.exams.ui.theme.Teal

/**
 * A Composable helper that builds an AnnotatedString, highlighting specific words
 * within a sentence. This is the Compose equivalent of your `styledSentence` for iOS.
 *
 * @param sentence The full sentence to display.
 * @param wordsToHighlight A comma-separated string of words to find and style (e.g., "well, good").
 * @param highlightColor The color to apply to the highlighted words.
 * @return An `AnnotatedString` with the specified words underlined and colored.
 */
@Composable
fun annotatedSentenceByWords(
    sentence: String,
    wordsToHighlight: String,
    highlightColor: Color = Teal // Use your theme's accent color here if you have one
): AnnotatedString {
    // 1. Get a clean list of the individual words to search for.
    val words = wordsToHighlight.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    // 2. Use the `buildAnnotatedString` builder, which is the equivalent of Swift's `AttributedString`.
    return buildAnnotatedString {
        // a) Append the original sentence text.
        append(sentence)

        // b) Loop through each word we need to find and style.
        words.forEach { word ->
            var startIndex = 0
            // c) Loop to find ALL occurrences of the current word in the sentence.
            while (startIndex < sentence.length) {
                // Find the next occurrence, ignoring case.
                val index = sentence.indexOf(word, startIndex, ignoreCase = true)
                if (index == -1) {
                    // If no more occurrences are found, break the inner loop.
                    break
                }

                // d) Apply the style to the found range.
                addStyle(
                    style = SpanStyle(
                        color = highlightColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold // Bolding can help with emphasis
                    ),
                    start = index,
                    end = index + word.length
                )

                // e) Update the start index to continue searching after the word we just found.
                startIndex = index + word.length
            }
        }
    }
}
