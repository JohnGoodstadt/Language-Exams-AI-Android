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
        loadDashboard()

        // 2. ✅ NEW: Listen for updates from the Repository
        viewModelScope.launch {
            vocabQuizRepository.dataUpdateEvents.collect {
                // When repo says "I saved new data", we reload the stats
                Timber.d("VocabDashboard: Repository updated, refreshing UI...")
                loadDashboard()
            }
        }
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
            val dueWords = vocabQuizRepository.getDueWords(limit = 10)
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
    fun onStartSmartReview(onProceed: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Ask Repo for the words that are due
            // (Limit matches whatever your Quiz logic will use, e.g., 20 or 50)
            val dueWords = vocabQuizRepository.getDueWords(limit = 10)

            // 2. Log them
            Timber.tag("SmartReview").i("\n📋 ===== SMART REVIEW PREVIEW =====")
            if (dueWords.isEmpty()) {
                Timber.tag("SmartReview").i("   (No words strictly due. Quiz will likely fill with randoms.)")
            } else {
                Timber.tag("SmartReview").i("   Found ${dueWords.size} words due for review:")
                dueWords.forEachIndexed { index, word ->
                    Timber.tag("SmartReview").i("   ${index + 1}. $word")
                }
            }
            Timber.tag("SmartReview").i("===================================\n")

            // 3. Continue to Navigation (Main Thread)
            withContext(Dispatchers.Main) {
                onProceed()
            }
        }
    }

    fun openQuizForCategory(title: String) {
        _currentQuizTitle.value = title
    }

    fun closeQuizSheet() {
        _currentQuizTitle.value = null
        vocabQuizRepository.updateEvents()
    }
    fun openSmartReview() {
        _showSmartReviewSheet.value = true
    }

    // 3. Action: Close the sheet
    fun closeSmartReview() {
        _showSmartReviewSheet.value = false
        // Optional: Reload dashboard stats when closing quiz to show updated progress
//        loadDashboard()
        vocabQuizRepository.updateEvents()
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