package com.goodstadt.john.language.exams.screens // Or your correct package

// --- All Necessary Imports ---
//import android.media.session.PlaybackState
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.screens.utils.buildSentenceParts
import com.goodstadt.john.language.exams.ui.theme.Orange
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.CategoryTabViewModel
import com.goodstadt.john.language.exams.viewmodels.PlaybackState

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.material3.LinearProgressIndicator
/**
 * A self-contained screen that displays vocabulary for a specific tab.
 * It manages its own state and logic via the CategoryTabViewModel.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryTabScreen(
    tabIdentifier: String,
    selectedVoiceName: String,
    viewModel: CategoryTabViewModel = hiltViewModel()
) {
    // This effect ensures the ViewModel loads data for this specific tab, once.
    LaunchedEffect(key1 = tabIdentifier, key2 = selectedVoiceName) {
        viewModel.loadContentForTab(tabIdentifier, selectedVoiceName)
    }

    val uiState by viewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Derive UI-specific lists from the state. `remember` ensures this
    // calculation only re-runs when the categories list changes.
    val categories = uiState.categories
    val menuItems = remember(categories) { categories.map { it.title } }
    val categoryIndexMap = remember(categories) {
        var currentIndex = 0
        val map = mutableMapOf<String, Int>()
        categories.forEach { category ->
            map[category.title] = currentIndex
            currentIndex += 1 + category.words.size
        }
        map
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            CacheProgressBar(
                cachedCount = uiState.cachedAudioCount,
                totalCount = uiState.totalWordsInTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp, vertical = 8.dp)
            )
            // Horizontal scrolling menu
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(menuItems, key = { it }) { title ->
                    MenuItemChip(
                        text = title,
                        onClick = {
                            scrollToCategory(
                                title = title,
                                coroutineScope = coroutineScope,
                                lazyListState = lazyListState,
                                indexMap = categoryIndexMap
                            )
                        }
                    )
                }
            }

            // Vertically scrolling list with vocabulary
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                categories.forEach { category ->
                    stickyHeader {
                        CategoryHeader(title = category.title)
                    }

                    items(category.words, key = { "${it.id}-${it.word}" }) { word ->
                        val sentenceToShow = word.sentences.firstOrNull()

                        if (sentenceToShow != null) {
                            val uniqueSentenceId = generateUniqueSentenceId(word, sentenceToShow, selectedVoiceName)
                            val isDownloading = uiState.downloadingSentenceId == uniqueSentenceId

                            SwipeableVocabRow(
                                word = word,
                                sentence = sentenceToShow,
                                selectedVoiceName = selectedVoiceName,
                                isDownloading = isDownloading,
//                                isPlaying = (uiState.playbackState as? PlaybackState.Playing)?.sentenceId == generateUniqueSentenceId(word, sentenceToShow, selectedVoiceName),
                                recalledWordKeys = uiState.recalledWordKeys,
                                wordsOnDisk = uiState.wordsOnDisk, // Pass the new state
                                onRowTapped = { w, s -> viewModel.onRowTapped(w, s) }, // Call ViewModel's function
                                onFocus = { viewModel.onFocusClicked(word) },
                                onCancel = { viewModel.onCancelClicked(word) }
                            )
                        } else {
                            Text(
                                text = "Error: No sentence found for '${word.word}'",
                                color = Color.Red,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
}


// --- Helper Composables for this Screen ---

@Composable
fun CategoryHeader(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Orange,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp)
    )
}

private fun scrollToCategory(
    title: String,
    coroutineScope: CoroutineScope,
    lazyListState: LazyListState,
    indexMap: Map<String, Int>
) {
    coroutineScope.launch {
        val index = indexMap[title] ?: return@launch
        lazyListState.animateScrollToItem(index = index)
    }
}


@Composable
fun CacheProgressBar(
    cachedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    // This 'if' check replaces SwiftUI's .opacity() modifier.
    // The entire composable will not be part of the UI if the count is zero.
    if (cachedCount > 0) {
        // Calculate progress as a float between 0.0 and 1.0
        val progress = if (totalCount > 0) {
            cachedCount.toFloat() / totalCount.toFloat()
        } else {
            0f // Avoid division by zero
        }

        Row(
            modifier = modifier.height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$cachedCount",
                style = MaterialTheme.typography.labelSmall
            )
            // Use a weight modifier to make the progress bar fill the available space
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            Text(
                text = "$totalCount",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
