package com.goodstadt.john.language.exams.screens.reference


import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.examsheets.ExamSheetRepository
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// 1. Define the UI State for this specific screen.
// A sealed interface is the best practice for representing distinct states.
sealed interface Format1UiState {
    object Loading : Format1UiState
    data class Success(val data: List<HeaderWordsSentencesList>) : Format1UiState
    data class Error(val message: String) : Format1UiState
}

@HiltViewModel
class Format1ViewModel @Inject constructor(
    private val appConfigRepository: AppConfigRepository,
    private val examSheetRepository: ExamSheetRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<Format1UiState>(Format1UiState.Loading)
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
            // The state is already Loading by default.

            try {
                // --- VERSION CHECKING LOGIC ---
                // This is identical to your other ViewModels and is crucial for data freshness.
                val remoteVersions = appConfigRepository.getRemoteSheetVersions()
                val remoteVersion = remoteVersions[documentId] ?: 1
                val localVersion = appConfigRepository.getLocalVersion(documentId)
                val forceRefresh = remoteVersion > localVersion

                // --- DATA FETCH ---
                // 5. Call the new, type-safe repository function for Format1 data.
                val result = examSheetRepository.getFormat1Sheet(
                    name = documentId,
                    forceRefresh = forceRefresh
                )

                result.onSuccess { format1File ->
                    // 6. On success, update the state with the displayable data.
                    _uiState.value = Format1UiState.Success(format1File.data)

                    // Update the local version number if we did a forced refresh.
                    if (forceRefresh) {
                        appConfigRepository.updateLocalVersion(documentId, remoteVersion)
                    }
                }

                result.onFailure { error ->
                    // 7. On failure, update the state with an error message.
                    _uiState.value = Format1UiState.Error(error.localizedMessage ?: "Failed to load content")
                }

            } catch (e: Exception) {
                _uiState.value = Format1UiState.Error(e.localizedMessage ?: "An unexpected error occurred.")
            }
        }
    }
}