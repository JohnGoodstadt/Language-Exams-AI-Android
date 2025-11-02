package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesList
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A Composable screen that displays data in the "Format1" structure.
 * This is the direct equivalent of the SwiftUI `Format1SheetView`.
 *
 * @param data The list of sections to display, typically from a ViewModel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Format1Screen(
    data: List<HeaderWordsSentencesList>,
    modifier: Modifier = Modifier
) {
    // LazyColumn is the efficient Composable for displaying scrollable lists.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp), // Adds space between sections
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Loop through each section in the data
        data.forEach { section ->

            // 1. Create a sticky header for the section title.
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface) // Important for sticky headers
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 2. Add the items for the current section.
            items(
                items = section.wordsAndSentences,
                key = { it.word + it.sentence } // Provide a stable and unique key
            ) { item ->
                // This Column represents a single row in the list.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.sentence,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (item.definition.isNotBlank()) {
                        Text(
                            text = item.definition,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant // A less prominent color
                        )
                    }
                }

                // Add a divider for visual separation, but not after the very last item in the list
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}