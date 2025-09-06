package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Data class for a single tile (replaces Swift 'ProgressMap' struct)
data class ProgressMapItem(
    val id: String, // Using category title as a unique ID
    val title: String,
    val totalWords: Int,
    val completedWords: Int
)

// UI State for the entire screen
data class ProgressMapUiState(
    val isLoading: Boolean = true,
    val progressItems: List<ProgressMapItem> = emptyList(),
    val totalWords: Int = 0,
    val completedWords: Int = 0
)

@HiltViewModel
class ProgressMapViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressMapUiState())
    val uiState = _uiState.asStateFlow()

    fun loadProgressData() {
       //if (!_uiState.value.isLoading && _uiState.value.progressItems.isNotEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val currentExamFile = userPreferencesRepository.selectedFileNameFlow.first()
            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val vocabDataResult = vocabRepository.getVocabData(currentExamFile)

            vocabDataResult.onSuccess { vocabFile ->
                val progressData = vocabFile.categories.map { category ->
                    val completedCount = vocabRepository.getCompletedWordCountForCategory(category, currentVoiceName)
                    ProgressMapItem(
                        id = category.title,
                        title = category.title,
                        totalWords = category.words.size,
                        completedWords = completedCount
                    )
                }



                if (DEBUG){//for screen shot marketing
                    val totalSize = 150
                    val totalCompleted = 205

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            progressItems = progressData,
                            totalWords = totalSize,
                            completedWords = totalCompleted
                        )
                    }
                }else{
                    val totalSize = progressData.sumOf { it.totalWords }
                    val totalCompleted = progressData.sumOf { it.completedWords }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            progressItems = progressData,
                            totalWords = totalSize,
                            completedWords = totalCompleted
                        )
                    }
                }
            }
            vocabDataResult.onFailure {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}