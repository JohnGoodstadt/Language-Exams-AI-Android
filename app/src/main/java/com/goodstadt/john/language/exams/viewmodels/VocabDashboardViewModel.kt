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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
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

    private val _currentQuizTitle = MutableStateFlow<String?>(null)
    val currentQuizTitle = _currentQuizTitle.asStateFlow()

    private val _showSmartReviewSheet = MutableStateFlow(false)
    val showSmartReviewSheet = _showSmartReviewSheet.asStateFlow()

    init {
       // loadDashboard()

        // 1. LISTEN FOR EXAM CHANGES (Settings -> Dashboard)
        // This acts as BOTH the "Initial Load" (it emits immediately)
        // AND the "Settings Changed" listener.
        viewModelScope.launch {
            userPreferencesRepository.selectedExamNameFlow.distinctUntilChanged().collect { newExamName ->
                Timber.d("VocabDashboard: Exam changed to $newExamName. Reloading.")
                loadDashboard(examNameOverride = newExamName)
            }
        }

        // 2. LISTEN FOR QUIZ RESULTS (Quiz -> Dashboard)
        viewModelScope.launch {
            vocabQuizRepository.dataUpdateEvents.collect {
                // When repo says "I saved new data", we reload.
                // Note: We don't pass an override here; loadDashboard will fetch the current name.
                Timber.d("VocabDashboard: Data updated. Refreshing.")
                loadDashboard()
            }
        }
    }

    fun loadDashboard(examNameOverride: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 0. Fast cold-start check — no network needed for onboarding view
            val allWordStates = vocabQuizRepository.getAllStates()
            if (allWordStates.isEmpty()) {
                _uiState.value = DashboardUiState(isLoading = false, isColdStart = true)
                return@launch
            }

            // 1. Get Exam Data (Structure) — only needed for active dashboard
            val examName = examNameOverride ?: userPreferencesRepository.selectedExamNameFlow.first()
            val vocabResult = contentRepository.getFormat0Data(examName)

            val currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()
            val dueWords = vocabQuizRepository.getDueWords(limit = 10,currentSkillLevel)

            vocabResult.onSuccess { vocabFile ->

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
                    wordsDueList = dueWords,
                    totalMastered = totalMastered,
                    categoryStats = statsList
                )
            }
        }
    }

    fun openQuizForCategory(title: String) {
        _currentQuizTitle.value = title
    }

    fun closeQuizSheet(isDirty:Boolean) {
        _currentQuizTitle.value = null
        if(isDirty) {
            vocabQuizRepository.updateEvents()
        }
    }
    fun openSmartReview() {
//        vocabQuizRepository.debugPrintStatus()
        vocabQuizRepository.debugPrintAllWordStates()
        _showSmartReviewSheet.value = true
    }

    // 3. Action: Close the sheet
    fun closeSmartReview(isDirty:Boolean) {
        _showSmartReviewSheet.value = false
        if(isDirty) {
            vocabQuizRepository.updateEvents()
        }
    }
    fun debugResetVocabProgress() {
        viewModelScope.launch {
            // 1. Wipe Data
            vocabQuizRepository.debugClearAllProgress()

            // 2. Wait a tiny bit for IO
            // (Optional, but ensures file is gone before we reload)
            kotlinx.coroutines.delay(100)

            // 3. Reload Dashboard
            // This will see empty data and switch 'isColdStart' to true
            loadDashboard()
        }
    }
}