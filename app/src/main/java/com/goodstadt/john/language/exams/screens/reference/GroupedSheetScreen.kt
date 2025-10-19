package com.goodstadt.john.language.exams.screens.reference


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.screens.reference.shared.SectionedVocabList
import com.goodstadt.john.language.exams.viewmodels.PlaybackState

@Composable
fun GroupedSheetScreen(
    viewModel: GroupedSheetViewModel = hiltViewModel()
) {
    // 1. Collect the single source of truth from the ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // The main layout is a vertical column
    Column(modifier = Modifier.fillMaxSize()) {

        // 2. The Sub-Tab Picker (the sub-menu)
        // This only shows if there are sub-tabs to display
        if (uiState.subTabs.isNotEmpty()) {
            val options: List<String> = remember(uiState.subTabs) {
                uiState.subTabs.map { it.title }
            }
            val selectedOption: String = uiState.selectedSubTab?.title ?: ""

            // b) Call your reusable composable
            HorizontalLevelPicker(
                options = options,
                selectedOption = selectedOption,
                onOptionSelected = { selectedTitle ->
                    // c) Find the corresponding SubTabDefinition and notify the ViewModel
                    val newSelectedSubTab = uiState.subTabs.firstOrNull { it.title == selectedTitle }
                    if (newSelectedSubTab != null) {
                        viewModel.onSubTabSelected(newSelectedSubTab)
                    }
                }
            )
        }

        // 3. The Content Area, which changes based on the 'contentState'
        when (val contentState = uiState.contentState) {
            is ContentState.Idle -> {
                // The initial state before any content is loaded.
                // You can leave this empty or show a placeholder.
            }
            is ContentState.Loading -> {
                // Show a loading indicator while fetching data from Firestore
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ContentState.Success -> {
                // Data has loaded successfully, so display the reusable vocab list.
                // Your existing SectionedVocabList is perfect for this.
                SectionedVocabList(
                    categories = contentState.categories,
                    // TODO: To enable audio playback, you will need to add PlaybackState
                    // to your GroupedSheetUiState and a playTrack() function to your
                    // GroupedSheetViewModel, then pass them here.
                    // For now, we can use placeholder values.
                    playbackState = PlaybackState.Idle, // Placeholder
                    googleVoice = "", // Placeholder
                    cachedAudioWordKeys = emptySet(), // Placeholder
                    onRowTapped = { word, sentence ->
                         viewModel.playTrack(word, sentence)
                    }
                )
            }
            is ContentState.Error -> {
                // An error occurred during the data fetch
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = contentState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}