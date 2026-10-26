package com.goodstadt.john.language.exams.packages.VocabQuiz

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.VocabQuizRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.models.CategoryMasteryStats
import com.goodstadt.john.language.exams.models.DashboardUiState
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import com.goodstadt.john.language.exams.screens.CategoryTab.SectionQuizKeyMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class VocabDashboardViewModel @Inject constructor(
    private val application: Application,
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

            // 0. Cold start = never answered anything. We still load the categories (like iOS) so the
            //    onboarding view can list every quiz instead of a couple of hard-coded suggestions.
            val allWordStates = vocabQuizRepository.getAllStates()
            val isColdStart = allWordStates.isEmpty()

            // 1. Get Exam Data (Structure)
            val examName = examNameOverride ?: userPreferencesRepository.selectedExamNameFlow.first()
            val vocabResult = contentRepository.getFormat0Data(examName)

            val currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()
            val dueWords = if (isColdStart) emptyList() else vocabQuizRepository.getDueWords(limit = 10,currentSkillLevel)

            // Quiz files shipped for this level, so only categories that actually have a quiz are listed
            // (same lookup as VocabSectionQuizViewModel.loadSectionQuiz, so a tap always finds questions).
            val quizFiles = withContext(Dispatchers.IO) {
                application.assets.list("$QUIZ_ASSET_PATH/$currentSkillLevel")?.toList() ?: emptyList()
            }
            fun hasQuiz(title: String): Boolean {
                val key = SectionQuizKeyMap.keyFor(currentSkillLevel, title)
                if (key.isNullOrBlank()) return false
                return quizFiles.any { it.startsWith("WordQuiz${key}1-") && it.endsWith(".json") }
            }

            vocabResult.onFailure {
                Timber.e(it, "VocabDashboard: failed to load vocab for $examName")
                _uiState.value = DashboardUiState(isLoading = false, isColdStart = isColdStart)
            }

            vocabResult.onSuccess { vocabFile ->

                // 4. Aggregate Stats per Category
                val statsList = vocabFile.categories
                    .distinctBy { it.title }
                    .filter { hasQuiz(it.title) }
                    .map { category ->
                    // Prefer the quiz's own rolled-up counts (saved by the repository from the quiz's word
                    // list whenever it's played), so the numbers match exactly what the quiz recorded.
                    val quizKey = SectionQuizKeyMap.keyFor(currentSkillLevel, category.title) ?: ""
                    val quizState = vocabQuizRepository.getCategoryMasteryState(quizKey, currentSkillLevel)
                    if (quizState != null && quizState.total > 0) {
                        return@map CategoryMasteryStats(
                            categoryTitle = category.title,
                            totalWords = quizState.total,
                            newCount = quizState.newCount,
                            learningCount = quizState.struggling + quizState.learning + quizState.review,
                            masteredCount = quizState.mastered
                        )
                    }

                    // Never played: count the vocab category's words.
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
                    isColdStart = isColdStart,
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

private const val QUIZ_ASSET_PATH = "Quizzes/SectionQuiz" // matches VocabSectionQuizViewModel's QUIZ_PATH
