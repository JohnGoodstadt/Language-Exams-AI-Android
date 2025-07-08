package com.goodstadt.john.language.exams.screens

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
fun AttributedHighlightedText(
    paragraph: String,
    highlightedWords: Set<String>,
    modifier: Modifier = Modifier,
    highlightColor: Color = MaterialTheme.colorScheme.primary // Using theme color
) {
    // remember will re-execute the block only if paragraph or highlightedWords change
    val annotatedString = remember(paragraph, highlightedWords) {
        buildAnnotatedString {
            // Append the original string
            append(paragraph)

            // Find and style each highlighted word
            for (word in highlightedWords) {
                var startIndex = 0
                while (startIndex < paragraph.length) {
                    val index = paragraph.indexOf(word, startIndex, ignoreCase = true)
                    if (index == -1) break

                    // Apply a style to the found range
                    addStyle(
                            style = SpanStyle(color = highlightColor),
                            start = index,
                            end = index + word.length
                    )
                    startIndex = index + word.length
                }
            }
        }
    }

    Text(
            text = annotatedString,
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            // Allows the clickable modifier to show a ripple effect
            color = LocalContentColor.current
    )
}