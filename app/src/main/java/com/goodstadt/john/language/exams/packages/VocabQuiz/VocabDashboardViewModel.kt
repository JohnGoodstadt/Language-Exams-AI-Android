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
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.BannerManager
import javax.inject.Inject


@HiltViewModel
class VocabDashboardViewModel @Inject constructor(
    private val application: Application,
    private val vocabQuizRepository: VocabQuizRepository,
    private val contentRepository: ContentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val xpManager: XPManager,          // XP total (fly-in) + daily streak (Due Today card)
    private val bannerManager: BannerManager,  // level / daily goal / all caught up banners
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
                    // Per-category "N of 3 days" (same key as the category-tab stars), null until played.
                    val dayProgress = vocabQuizRepository.categoryDayProgress(quizKey, currentSkillLevel)
                        .takeIf { it.hasAttempts }
                        ?: vocabQuizRepository.categoryDayProgress(category.title, currentSkillLevel).takeIf { it.hasAttempts }
                    if (quizState != null && quizState.total > 0) {
                        return@map CategoryMasteryStats(
                            categoryTitle = category.title,
                            totalWords = quizState.total,
                            newCount = quizState.newCount,
                            learningCount = quizState.struggling + quizState.learning + quizState.review,
                            masteredCount = quizState.mastered,
                            day1Count = quizState.day1,
                            day2Count = quizState.day2,
                            dayProgress = dayProgress,
                            quizKey = quizKey
                        )
                    }

                    // Never played: count the vocab category's words.
                    var new = 0
                    var learning = 0
                    var mastered = 0
                    var day1 = 0
                    var day2 = 0

                    category.words.forEach { word ->
                        val state = allWordStates[word.word]
                        if (state == null) {
                            new++
                        } else {
                            when (state.masteryLevel) {
                                WordMasteryLevel.New -> new++
                                WordMasteryLevel.Mastered -> mastered++
                                else -> {          // Struggling, Learning, Review
                                    learning++
                                    if (state.correctStreak == 1) day1++
                                    if (state.correctStreak == 2) day2++
                                }
                            }
                        }
                    }

                    CategoryMasteryStats(
                        categoryTitle = category.title,
                        totalWords = category.words.size,
                        newCount = new,
                        learningCount = learning,
                        masteredCount = mastered,
                        day1Count = day1,
                        day2Count = day2,
                        dayProgress = dayProgress,
                        quizKey = quizKey
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
                    categoryStats = statsList,
                    level = currentSkillLevel
                )
            }
        }
    }

    // ---------------- Gamification (mirrors iOS VocabDashboardScreen) ----------------

    fun sectionSummary(stat: CategoryMasteryStats, now: Long): String? =
        vocabQuizRepository.sectionSummary(stat.attemptNames, _uiState.value.level, now)
    fun isSectionMastered(stat: CategoryMasteryStats): Boolean =
        vocabQuizRepository.isSectionMastered(stat.attemptNames, _uiState.value.level)
    fun isSectionReady(stat: CategoryMasteryStats, now: Long): Boolean =
        vocabQuizRepository.isSectionReady(stat.attemptNames, _uiState.value.level, now)
    fun sectionDueCount(stat: CategoryMasteryStats, now: Long): Int =
        vocabQuizRepository.sectionDueCount(stat.attemptNames, _uiState.value.level, now)
    fun sectionsCompletedToday(): Int = vocabQuizRepository.sectionsCompletedToday()
    fun isLevelMastered(): Boolean = vocabQuizRepository.isLevelMastered(
        _uiState.value.categoryStats.map { it.attemptNames }, _uiState.value.level)
    fun totalXp(): Int = xpManager.state.value.levels.values.sumOf { it.xp }
    fun currentStreak(): Int = xpManager.state.value.currentStreak
    fun showBanner(title: String, subtitle: String) = bannerManager.showBanner(title, subtitle)

    /** State of a section as its quiz opens, compared when the quiz closes (row pulse, New best, XP...). */
    data class SectionSnapshot(
        val title: String, val names: List<String>, val goes: Int, val days: Int, val best: Int,
        val xp: Int, val levelMastered: Boolean, val goalMet: Boolean,
    )

    /** What changed while the quiz sheet was open: drives the row pulse and the bigger moments. */
    data class SectionOutcome(
        val title: String, val tier: com.goodstadt.john.language.exams.packages.UsageQuiz.RowPulseTier,
        val newBest: Boolean, val xpEarned: Int, val levelMastered: Boolean, val dailyGoalDone: Boolean,
    )

    fun snapshotSection(title: String): SectionSnapshot {
        val level = _uiState.value.level
        val names = _uiState.value.categoryStats.firstOrNull { it.categoryTitle == title }?.attemptNames ?: listOf(title)
        return SectionSnapshot(
            title, names,
            vocabQuizRepository.sectionCompletedCount(names, level),
            vocabQuizRepository.sectionPerfectDays(names, level).size,
            vocabQuizRepository.sectionBestFirstTry(names, level),
            totalXp(), isLevelMastered(),
            vocabQuizRepository.sectionsCompletedToday() >= VocabQuizRepository.DAILY_GOAL,
        )
    }

    /** Null unless a go was FINISHED while the sheet was open. */
    fun outcomeSince(snap: SectionSnapshot): SectionOutcome? {
        val level = _uiState.value.level
        if (vocabQuizRepository.sectionCompletedCount(snap.names, level) <= snap.goes) return null
        val daysNow = vocabQuizRepository.sectionPerfectDays(snap.names, level).size
        val last = vocabQuizRepository.sectionLastGo(snap.names, level)
        val tier = when {
            snap.days < 3 && daysNow >= 3 -> com.goodstadt.john.language.exams.packages.UsageQuiz.RowPulseTier.MASTERED
            last?.flawless == true -> com.goodstadt.john.language.exams.packages.UsageQuiz.RowPulseTier.PERFECT
            else -> com.goodstadt.john.language.exams.packages.UsageQuiz.RowPulseTier.FINISHED
        }
        return SectionOutcome(
            snap.title, tier,
            newBest = snap.goes > 0 && ((last?.firstTryCorrect ?: last?.correct ?: 0) > snap.best),
            xpEarned = totalXp() - snap.xp,
            levelMastered = !snap.levelMastered && isLevelMastered(),
            dailyGoalDone = !snap.goalMet && sectionsCompletedToday() >= VocabQuizRepository.DAILY_GOAL,
        )
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
