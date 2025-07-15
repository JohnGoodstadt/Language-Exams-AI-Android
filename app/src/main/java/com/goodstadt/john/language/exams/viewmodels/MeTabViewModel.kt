package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// This will hold the title of the category selected for the detail view
data class MeTabUiState(
    val selectedCategoryTitle: String? = null
)

@HiltViewModel
class MeTabViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MeTabUiState())
    val uiState = _uiState.asStateFlow()

    fun onTileTapped(categoryTitle: String) {
        // When a tile is tapped, we update the state to show the sheet
        _uiState.update { it.copy(selectedCategoryTitle = categoryTitle) }
    }

    fun onSheetDismissed() {
        // When the sheet is dismissed, we clear the selected title
        _uiState.update { it.copy(selectedCategoryTitle = null) }
    }
}