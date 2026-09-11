package com.goodstadt.john.language.exams.packages.MyProgress
import com.goodstadt.john.language.exams.packages.ReadinessAudit.ReadinessAuditLevels
import com.goodstadt.john.language.exams.packages.ReadinessAudit.AuditStats


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.data.ReadinessAuditRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.AuditEngine
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpState
import com.goodstadt.john.language.exams.models.AppUIManifest
import com.goodstadt.john.language.exams.screens.Format1.SideQuestData
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import com.goodstadt.john.language.exams.utils.CategoryProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyProgressUiState(
    val xpState: XpState,
    val mainQuestProgress: List<CategoryProgress>,
    val sideQuestData: SideQuestData?,
    val aiCount: Int = 0,
    val aiHeard: Int = 0
)

@HiltViewModel
class MyProgressViewModel @Inject constructor (
    private val xpManager: XPManager,
    private val audioCacheManager: AudioCacheManager,
    private val appConfigRepository: AppConfigRepository,
    val quizManager: QuizHistoryManager,
    private val auditRepository: ReadinessAuditRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val vocabQuizRepository: com.goodstadt.john.language.exams.data.repository.VocabQuizRepository,
) : ViewModel() {

    val currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow

    // Per-level Confidence & Readiness for the CURRENTLY SELECTED level (A1/A2/B1/B2). Blends this
    // level's audit band (A2/B1/B2 only; A1 has none) with how many of its section (Vocab) quizzes
    // have been mastered with 3 clean strikes - so A1 can reach 98/98 by finishing all A1 vocab, while
    // the exam-relevant B1/B2 numbers only climb once that level's work is demonstrated. Recomputes when
    // the selected level changes, an audit score changes, or a section quiz's mastery changes.
    val auditStats: StateFlow<AuditStats> =
        combine(
            currentSkillLevel,
            auditRepository.categoryScores,
            vocabQuizRepository.dataUpdateEvents.onStart { emit(Unit) }
        ) { level, categoryScores, _ ->
            // Quiz evidence at this level: the audit's per-band answers + any Usage/Grammar answers.
            // categoryScores is keyed "category|level"; sum every entry whose level matches.
            var quizAnswered = 0
            var quizCorrect = 0
            categoryScores.forEach { (key, score) ->
                if (key.substringAfterLast('|').equals(level, ignoreCase = true)) {
                    quizAnswered += score.correct + score.incorrect + score.dontKnow
                    quizCorrect += score.correct
                }
            }

            // Section (Vocab) categories at this level mastered with 3 clean strikes (flawless streak = 3).
            val masteredSections = vocabQuizRepository.getAllCategoryMasteryStates()
                .filter { it.level.equals(level, ignoreCase = true) }
                .count { vocabQuizRepository.flawlessStreakStars(it.category, it.level) >= 3 }

            val report = AuditEngine.calculateForLevel(
                AuditEngine.LevelEvidence(
                    quizAnswered = quizAnswered,
                    quizCorrect = quizCorrect,
                    masteredSections = masteredSections,
                    expectedSections = AuditEngine.EXPECTED_SECTIONS_PER_LEVEL
                )
            )
            AuditStats(confidence = report.confidence, readiness = report.readiness)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AuditStats(0, 0)
        )

    // Which audit version the learner is on, and whether the New Audit button should be live.
    // Enabled only once the baseline is done AND a higher version's questions still exist.
    val auditVersion: StateFlow<Int> = auditRepository.auditVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val newAuditEnabled: StateFlow<Boolean> =
        combine(
            auditRepository.auditScores.map { it.containsKey(1) }, // baseline complete
            auditRepository.auditVersion
        ) { baselineDone, version ->
            baselineDone && version < ReadinessAuditRepository.MAX_AUDIT_VERSION
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** New Audit: advance to the next version (persisted so the button re-locks). No score reset. */
    fun advanceAuditVersion() {
        viewModelScope.launch {
            val next = (auditVersion.value + 1).coerceAtMost(ReadinessAuditRepository.MAX_AUDIT_VERSION)
            auditRepository.saveAuditVersion(next)
        }
    }

    // CEFR band the baseline audit placed the learner into ("A2"/"B1"/"B2"), or null until a
    // baseline quiz is completed. Drives the summary verdict in the dashboard header.
    val baselinePlacementLevel: StateFlow<String?> = auditRepository.baselineLevel
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // True once the baseline quiz has been completed (part 1 score on record), regardless of
    // how well it went. Distinct from "A2 mastered" - drives the header's take-vs-retake copy.
    val baselineComplete: StateFlow<Boolean> = auditRepository.auditScores
        .map { it.containsKey(1) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val unlockedAuditLevels: StateFlow<Set<ReadinessAuditLevels>> =
        combine(auditRepository.auditScores, auditRepository.baselineUnlockCeiling) { scores, ceiling ->
            val unlocked = mutableSetOf(ReadinessAuditLevels.BASELINE) // Part 1 always open

            // Baseline band mastery unlocks the level tests up to the ceiling part index
            // (A2 mastered -> A2 test, A2+B1 -> A2 & B1 tests, clean sweep -> all).
            ReadinessAuditLevels.entries.forEach { level ->
                if (level.ordinal + 1 <= ceiling) unlocked.add(level)
            }

            // Completing a level test unlocks the next level (progression).
            if (scores.containsKey(2)) unlocked.add(ReadinessAuditLevels.UPPER)
            if (scores.containsKey(3)) unlocked.add(ReadinessAuditLevels.ADVANCED)

            unlocked
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = setOf(ReadinessAuditLevels.BASELINE)
        )


    // 3. Helper function for the "New Audit" button
    fun resetAudit() {
        viewModelScope.launch {
            auditRepository.resetAll()
        }
    }
    fun getCurrentSkillLevel() : String {
        return "B1"
    }

    // Combine flows from Managers into one UI State
    val uiState: StateFlow<MyProgressUiState?> = combine(
        xpManager.state,
        audioCacheManager.referenceHeardCounts,
        // We also trigger on total updates to ensure Main Quest bars move
        audioCacheManager.totalExamWordsHeardOverall
    ) { xpState, refHeardMap, _ ->

        // 1. Build Main Quest Data (Tabs 1-3)
        // We filter for tabs 1-3 to separate them from reference
        val allCats = audioCacheManager.getAllCategoryProgress()
        val mainQuest = allCats
            .filter { it.tabNumber in 1..3 }
            .sortedWith(compareBy({ it.tabNumber }, { it.sortOrder }))

        // 2. Build Side Quest Data (Reference)
//        val sideQuest = getReferenceData(audioCacheManager, refHeardMap)

        val manifest = getCachedManifest()
        val sideQuestData =    buildSideQuestData(audioCacheManager,manifest)


        MyProgressUiState(
            xpState = xpState,
            mainQuestProgress = mainQuest,
            sideQuestData = sideQuestData,
            aiCount = audioCacheManager.getAIParagraphCount(),
            aiHeard = audioCacheManager.getAIParagraphHeardCount()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Admin Actions
    fun hardReset() {
        xpManager.hardReset()
        quizManager.clearHistory()
        // historyManager.clearAll() // If exposed
    }

    fun getAudioCacheManager(): AudioCacheManager = audioCacheManager
    fun getXpManager(): XPManager = xpManager
    fun calculateGrandTotals(): Pair<Int, Int> {
        val heard = audioCacheManager.totalExamWordsHeardOverall.value
        val total = audioCacheManager.totalExamWordCount.value
        return Pair(heard, total)
    }
    private fun getCachedManifest(): AppUIManifest? {
        // This assumes you have a getter in your repository
        return appConfigRepository.getAppUiManifest()
    }

//    fun resetAudit() {
//        Timber.e("resetAudit TODO")
//    }
}