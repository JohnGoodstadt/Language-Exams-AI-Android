package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.RefreshTrigger
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// This will hold the title of the category selected for the detail view
data class MeTabUiState(
    val selectedCategoryTitle: String? = null,
    val currentVoiceName:String = ""

)

@HiltViewModel
class MeTabViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val refreshTrigger: RefreshTrigger
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeTabUiState())
    val uiState = _uiState.asStateFlow()


    init {
        viewModelScope.launch {
            userPreferencesRepository.selectedVoiceNameFlow.collect { voiceName ->
                _uiState.update { it.copy(currentVoiceName = voiceName) }
            }
        }
    }
    fun onTileTapped(categoryTitle: String) {
        // When a tile is tapped, we update the state to show the sheet
        _uiState.update { it.copy(selectedCategoryTitle = categoryTitle) }
    }

    fun onSheetDismissed() {

        _uiState.update { it.copy(selectedCategoryTitle = null) }
        Timber.d("MeTabViewModel", "Bottom sheet dismissed. Triggering a progress map refresh.")
        refreshTrigger.triggerProgressMapRefresh()
    }
}