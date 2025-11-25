package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesList
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.screens.HighlightedWordInSentenceRow
import com.goodstadt.john.language.exams.screens.RateLimitDailyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyReasonsBottomSheet
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.utils.buildSentencePartsSimple
import com.goodstadt.john.language.exams.viewmodels.ConjugationsViewModel
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet

/**
 * A Composable screen that displays data in the "Format1" structure.
 * This is the direct equivalent of the SwiftUI `Format1SheetView`.
 *
 * @param data The list of sections to display, typically from a ViewModel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Format1Screen(
    viewModel: Format1ViewModel = hiltViewModel(),
    data: List<HeaderWordsSentencesList>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()
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
                        color = orangeLight
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface) // Important for sticky headers
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = section.description,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable {
                            viewModel.playTrack(item.sentence)
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp),

                ) {
                    Text(
                        text = item.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Cyan
                        //modifier = Modifier.padding(vertical = 16.dp)
                    )
                    val displayData = buildSentencePartsSimple(word = item.word, sentence = item.sentence)
//                    Text(
//                        text = item.sentence,
//                        style = MaterialTheme.typography.bodyMedium
//                    )
                    HighlightedWordInSentenceRow(
                        word = item.word,
                        parts = displayData.parts,
                        sentence = displayData.sentence,
                        isRecalling = false,
                        displayDot = false,//achedAudioWordKeys.contains(uniqueSentenceId),
                        isDownloading = false//, //TODO: maybe dynamic?
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
    if (isRateLimitingSheetVisible){
        RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
    }
    if (isDailyRateLimitingSheetVisible){
        if (context is androidx.activity.ComponentActivity) {
            RateLimitDailyReasonsBottomSheet(
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
            )
        }
    }
    if (isHourlyRateLimitingSheetVisible){
        if (context is androidx.activity.ComponentActivity) {
            RateLimitHourlyReasonsBottomSheet(
                onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
            )
        }
    }
}