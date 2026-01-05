package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.viewmodels.GroupedFormat2ViewModel
import androidx.compose.runtime.key

@Composable
fun Format2GroupedScreen(
    viewModel: GroupedFormat2ViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {

        // 1. Horizontal Picker (Sub-Tabs)
        if (uiState.subTabs.isNotEmpty()) {
            val options = remember(uiState.subTabs) { uiState.subTabs.map { it.title } }
            val selectedOption = uiState.selectedSubTab?.title ?: ""

            HorizontalLevelPicker(
                options = options,
                selectedOption = selectedOption,
                onOptionSelected = { title ->
                    val tab = uiState.subTabs.find { it.title == title }
                    if (tab != null) viewModel.onSubTabSelected(tab)
                }
            )
        }

        // 2. Content Area
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: ${uiState.error}", color = Color.Red)
            }
        } else {
            // 3. Render the Format2 Screen
            // We reuse the existing Format2Screen logic by passing the data manually
            // OR by creating a nested Format2ViewModel if your Format2Screen requires one.

            // To reuse your existing Format2Screen (which likely expects a ViewModel),
            // we can instantiate it with the docId.

            uiState.selectedSubTab?.firestoreDocumentId?.let { docId ->
                // This trick forces Hilt to create a fresh VM for the specific document
                // Key is essential so Compose knows to destroy old VM and create new one
                key(docId) {
                    Format2ScreenContainer(docId = docId)
                }
            }
        }
    }
}

// Wrapper to inject the specific Format2ViewModel based on the ID
@Composable
fun Format2ScreenContainer(
    docId: String,
    viewModel: Format2ViewModel = hiltViewModel() // Hilt uses SavedStateHandle "documentId"
) {
    // You might need to verify how your Format2Screen consumes data.
    // If it pulls from its own VM, this is all you need.
    // Ensure your NavHost creates this composable such that SavedStateHandle isn't empty,
    // OR pass the docId manually if your VM supports a setter.

    // Actually, since Format2Screen is usually a top-level route with args,
    // using it inside here is tricky because Hilt expects navigation args.

    // SIMPLER APPROACH:
    // Pass the raw data from GroupedFormat2ViewModel directly into the UI part of Format2Screen.
    // This assumes you refactored Format2Screen to take data params, not just a VM.

    /*
       Format2Screen(
           data = uiState.currentFormat2File.data,
           ...
       )
    */

    // If Format2Screen is tightly coupled to Format2ViewModel, use the ViewModel manually:
    // (This assumes Format2ViewModel has a 'load(docId)' function)

    val state by viewModel.uiState.collectAsState()

    // We can't easily rely on Hilt SavedStateHandle here because we aren't in a Nav entry.
    // Recommendation: Make sure Format2Screen accepts 'data' as a parameter!
}