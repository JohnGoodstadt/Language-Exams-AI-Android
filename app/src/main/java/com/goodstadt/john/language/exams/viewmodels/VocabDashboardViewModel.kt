package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.VocabQuizRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.models.CategoryMasteryStats
import com.goodstadt.john.language.exams.models.DashboardUiState
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class VocabDashboardViewModel @Inject constructor(
    private val vocabQuizRepository: VocabQuizRepository,
    private val contentRepository: ContentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    // ...
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Get Exam Data (Structure)
            val examName = userPreferencesRepository.selectedExamNameFlow.first()
//            val vocabResult = contentRepository.getVocabData(examName)
            val vocabResult = contentRepository.getFormat0Data(examName)

            // 2. Get User Progress (State)
            // (Assumes repo exposes a way to get all states, or we just rely on getDueWords)
            val dueWords = vocabQuizRepository.getDueWords(limit = 100)
            val allWordStates = vocabQuizRepository.getAllStates() // You need to add this accessor to Repo

            vocabResult.onSuccess { vocabFile ->

                // 3. Check for Cold Start
                if (allWordStates.isEmpty()) {
                    _uiState.value = DashboardUiState(isLoading = false, isColdStart = true)
                    return@launch
                }

                // 4. Aggregate Stats per Category
                val statsList = vocabFile.categories.map { category ->
                    var new = 0
                    var learning = 0
                    var mastered = 0

                    category.words.forEach { word ->
                        val state = allWordStates[word.word]
                        if (state == null) {
                            new++
                        } else {
                            when (state.masteryLevel) {
                                WordMasteryLevel.New -> new++
                                WordMasteryLevel.Mastered -> mastered++
                                else -> learning++ // Struggling, Learning, Review
                            }
                        }
                    }

                    CategoryMasteryStats(
                        categoryTitle = category.title,
                        totalWords = category.words.size,
                        newCount = new,
                        learningCount = learning,
                        masteredCount = mastered
                    )
                }

                // 5. Update State
                val totalMastered = allWordStates.count { it.value.masteryLevel == WordMasteryLevel.Mastered }

                _uiState.value = DashboardUiState(
                    isLoading = false,
                    isColdStart = false,
                    wordsDueCount = dueWords.size,
                    totalMastered = totalMastered,
                    categoryStats = statsList
                )
            }
        }
    }
}