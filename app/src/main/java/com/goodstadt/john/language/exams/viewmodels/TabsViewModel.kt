// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/viewmodels/TabsViewModel.kt
package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.models.VocabFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Represents the state of our data loading
sealed interface VocabDataUiState {
    object Loading : VocabDataUiState
    data class Success(val vocabFile: VocabFile) : VocabDataUiState
    data class Error(val message: String) : VocabDataUiState
}

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val vocabRepository: VocabRepository // Hilt injects our singleton repository
) : ViewModel() {

    // A StateFlow to hold the UI state for the vocab data
    private val _vocabUiState = MutableStateFlow<VocabDataUiState>(VocabDataUiState.Loading)
    val vocabUiState = _vocabUiState.asStateFlow()

    private val _tab1MenuItems = MutableStateFlow(listOf("Personal", "Home", "Shopping"))
    val tab1MenuItems = _tab1MenuItems.asStateFlow()

    private val _tab2MenuItems = MutableStateFlow(listOf("Travel", "Leisure", "Weather"))
    val tab2MenuItems = _tab2MenuItems.asStateFlow()

    private val _tab3MenuItems = MutableStateFlow(listOf("Past Tense", "Patterns", "Verbs"))
    val tab3MenuItems = _tab3MenuItems.asStateFlow()

    private val _tab5MenuItems = MutableStateFlow(listOf("Settings", "Search", "Quiz"))
    val tab5MenuItems = _tab5MenuItems.asStateFlow()

    init {
        // Load the data as soon as the ViewModel is created
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _vocabUiState.value = VocabDataUiState.Loading
            val result = vocabRepository.getVocabData()
            result.onSuccess { vocabFile ->
                _vocabUiState.value = VocabDataUiState.Success(vocabFile)
                // You could now, for example, update the tab titles from the loaded data
                // _tab1MenuItems.value = vocabFile.tabtitles
            }.onFailure { error ->
                _vocabUiState.value = VocabDataUiState.Error(error.localizedMessage ?: "Unknown error")
            }
        }
    }
}