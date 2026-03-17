package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.UsageQuizRepository
import com.goodstadt.john.language.exams.models.UsageLevelSummary
import com.goodstadt.john.language.exams.models.UsageMastery
import com.goodstadt.john.language.exams.models.UsageQuizOverviewItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsageDashboardViewModel @Inject constructor(
    private val usageQuizRepository: UsageQuizRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // UI State
    sealed interface UiState {
        object Loading : UiState
        data class ColdStart(val levelName: String) : UiState
        data class Active(val summary: UsageLevelSummary) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var currentLevelFilter: String = ""

    init {
//        loadData()
//
//        // Listen for updates (e.g. if user goes back and finishes a quiz)
//        viewModelScope.launch {
//            usageQuizRepository.dataUpdateEvents.collect {
//                loadData()
//            }
//        }
        viewModelScope.launch {
            usageQuizRepository.dataUpdateEvents.collect {
                // Refresh whatever level is currently active
                if (currentLevelFilter.isNotEmpty()) {
                    loadData(currentLevelFilter)
                }
            }
        }
    }

    fun loadData(levelName: String) {
        viewModelScope.launch {
            // 1. Get Current Level (e.g. "Elementary")
            // We assume the Enum has a description string matching the UI
          //  val levelName = userPreferencesRepository.selectedSkillLevelFlow.first()
            val levelEnum = mapStringToEnum(levelName) // Helper to get QuizLevelsNew.ELEMENTARY
            val ESOLLevel = levelEnum.ESOL
            // 2. Build the List
            val quizItems = levelEnum.quizzes.map { quizDetail ->
                // Construct ID: "UsageQuiz1A1" (or whatever your naming convention is)
                // Assuming "UsageQuiz" + ID + Level
                // You might need to adjust this key generation to match your UsageQuizViewModel exactly
                val cleanLevel = levelName.replace(" ", "")
                val quizKey = "UsageQuiz${quizDetail.id}${ESOLLevel}-en"

                val stats = usageQuizRepository.getStatsForQuiz(quizKey)

                // Build Question Mastery Map (1..10)
                val masteryMap = mutableMapOf<Int, UsageMastery>()
                if (stats != null) {
                    // Iterate 1 to 10 (assuming 10 questions fixed)
                    for (i in 1..10) {
                        masteryMap[i] = stats.questions[i]?.mastery ?: UsageMastery.New
                    }
                }

                UsageQuizOverviewItem(
                    id = quizDetail.id,
                    title = quizDetail.title,
                    bestScore = stats?.bestScore ?: 0,
                    timesCompleted = stats?.timesCompleted ?: 0,
                    isLocked = false,
                    questionMastery = masteryMap
                )
            }

            // 3. Determine State
            val completedCount = quizItems.count { it.isStarted }

            if (completedCount == 0) {
                _uiState.value = UiState.ColdStart(levelName)
            } else {
                val totalStars = quizItems.sumOf { calculateStars(it.bestScore) }

                val summary = UsageLevelSummary(
                    levelName = levelName,
                    totalQuizzes = quizItems.size,
                    completedQuizzes = completedCount,
                    totalStars = totalStars,
                    items = quizItems
                )
                _uiState.value = UiState.Active(summary)
            }
        }
        usageQuizRepository.debugPrint()
    }

    // Helpers
    private fun mapStringToEnum(level: String): QuizLevels {
        return QuizLevels.entries.find { it.description == level } ?: QuizLevels.ELEMENTARY
    }

    private fun calculateStars(score: Int): Int {
        return when {
            score == 10 -> 3
            score >= 8 -> 2
            score >= 5 -> 1
            else -> 0
        }
    }

    // Debug
    fun debugResetLevel() {
        // Logic to clear specific level stats
//        usageQuizRepository.clearStatsForQuiz(currentLevelFilter)
        usageQuizRepository.clearAll()
    }
    fun getLevelTitle(level: String): String {
        return when (level.lowercase()) {
            "beginner" -> "Start Basic Grammar"
            "elementary" -> "Start Elementary Grammar"
            "inter" -> "Start Intermediate Grammar"
            "advanced" -> "Start Advanced Grammar"
            else -> ""
        }
    }
    fun getLevelSubtitle(level: String): String {
        return when (level.lowercase()) {
            "beginner" -> "Pre-IELTS / Cambridge Starters / ESOL A1"
            "elementary" -> "IELTS 3.0 / TOEFL 31 / KET / ESOL A2"
            "inter" -> "IELTS 4.5 / TOEFL 57 / PET / ESOL B1"
            "advanced" -> "Target Professional: IELTS 6.0 / TOEFL 80 / FCE / ESOL B2"
            else -> ""
        }
    }
}