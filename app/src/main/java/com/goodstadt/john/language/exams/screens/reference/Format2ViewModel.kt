package com.goodstadt.john.language.exams.screens.reference


import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.ContentRepository
import com.goodstadt.john.language.exams.models.Format2File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// 1. Define the UI State for this specific screen.
// A sealed interface is the best practice for representing distinct states.

sealed interface Format2UiState {
    object Loading : Format2UiState
    data class Success(val format2File: Format2File) : Format2UiState
    data class Error(val message: String) : Format2UiState
}


@HiltViewModel
class Format2ViewModel @Inject constructor(
//    private val appConfigRepository: AppConfigRep ository,
//    private val examSheetRepository: ExamSheetRepository,
    private val vocabRepository: ContentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<Format2UiState>(Format2UiState.Loading)
    val uiState = _uiState.asStateFlow()

    // 3. Get the documentId from the navigation arguments via SavedStateHandle.
    //    The key "documentId" MUST match the argument name in your NavHost route.
    private val documentId: String = savedStateHandle.get<String>("documentId")!!

    init {
        // 4. Trigger the data loading process as soon as the ViewModel is created.
        loadData()
    }
    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = Format2UiState.Loading

            // ✅ SIMPLIFIED: All the complex logic is gone.
            // We just make one simple, type-safe call to our orchestrator.
            val result = vocabRepository.getFormat2Data(documentId)

            result.onSuccess { format2File ->
                _uiState.value = Format2UiState.Success(format2File)
            }
            result.onFailure { error ->
                _uiState.value = Format2UiState.Error(error.localizedMessage ?: "Failed to load content")
            }
        }
    }
}