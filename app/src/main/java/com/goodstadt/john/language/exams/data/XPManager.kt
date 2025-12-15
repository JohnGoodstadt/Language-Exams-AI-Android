package com.goodstadt.john.language.exams.managers

import android.content.Context
import android.os.Bundle
import android.util.Log
//import com.goodstadt.john.language.exams.models.XpActionType
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

// MARK: - Data Models

data class XpState(
    var currentLevel: String = "A1",
    var levels: MutableMap<String, LevelXpState> = mutableMapOf(),

    // Gamification
    var currentStreak: Int = 0,
    var longestStreak: Int = 0,
    var lastStudyDateId: String? = null,
    var gems: Int = 0,
    var streakFreezeCount: Int = 0,
    var earnedBadges: MutableList<String> = mutableListOf(),

    // Goals
    var targetExamDate: Long? = null,
    var targetExamLevel: String? = null,
    var datePrecision: String = "none" // "exact", "month", "duration"
)

data class LevelXpState(
    var xp: Int = 0,
    var learnerLevel: Int = 1,
    var progressToNextLevel: Double = 0.0,
    var lastUpdatedAt: Long = 0
)

enum class XpActionType {
    HearNewSentence,
    ReplaySentence,
    MasterWord,
    CompleteSection,
    CompletedSheet,
    CompleteDailyGoal,
    CompleteQuiz,
    PerfectQuiz,
    GenerateParagraph
}

data class DailyStats(
    val dateId: String, // "2025-12-15"
    var xpGained: Int = 0,
    var actionCount: Int = 0
)


@Singleton
class XPManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioCacheManager: AudioCacheManager // Needed for Badge checks (Total Heard)
) {

    companion object {
        private const val TAG = "XPManager"
        private const val FILE_NAME = "xp_state_v1.json"
    }

    private val gson = Gson()
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Reactive State
    private val _state = MutableStateFlow(initialState())
    val state = _state.asStateFlow()

    // Internal State
    private var sessionActionCount = 0
    private var recentListenTimestamps: MutableList<LocalDateTime> = mutableListOf()

    // Runtime flag for UI alerts (reset on app restart)
    var justConsumedFreeze: Boolean = false

    private var dailyStats: MutableMap<String, DailyStats> = mutableMapOf()
    private val DAILY_STATS_KEY = "xp_daily_stats_v1"
    // Config
    private val xpPerAction = mapOf(
        XpActionType.HearNewSentence to 3,
        XpActionType.ReplaySentence to 1,
        XpActionType.CompleteQuiz to 10,
        XpActionType.PerfectQuiz to 5,
        XpActionType.GenerateParagraph to 3
    )

    private val streakFreezeCost = 200

    init {
        loadFromDisk()
    }

    // MARK: - Public API

    fun registerAction(action: XpActionType, count: Int = 1) {
        scope.launch {
            val currentState = _state.value.copy() // Snapshot
            val level = currentState.currentLevel
            val baseXp = xpPerAction[action] ?: 0
            val delta = baseXp * count

            // 1. Update Session Analytics
            sessionActionCount += 1

            // 2. Apply XP to Level
            applyXp(currentState, delta, level, action)

            // 3. Update Streaks
            updateStreakLogic(currentState)

            // 4. Check Badges
            checkBadges(currentState, action)

            // 5. Update Gems (10% of XP)
            val earnedGems = max(1, delta / 5)
            currentState.gems += earnedGems

            val todayId = LocalDate.now().toString()
            val stats = dailyStats.getOrPut(todayId) { DailyStats(todayId) }
            stats.xpGained += delta
            stats.actionCount += 1

            // 6. Save & Emit
            saveToDisk(currentState)
            _state.value = currentState
        }
    }

    fun buyStreakFreeze(): Boolean {
        val currentState = _state.value.copy()
        if (currentState.gems >= streakFreezeCost) {
            currentState.gems -= streakFreezeCost
            currentState.streakFreezeCount += 1
            saveToDisk(currentState)
            _state.value = currentState
            return true
        }
        return false
    }

    // MARK: - Internal Logic

    private fun applyXp(state: XpState, delta: Int, level: String, source: XpActionType) {
        val levelState = state.levels.getOrPut(level) { LevelXpState() }

        levelState.xp += delta
        levelState.lastUpdatedAt = System.currentTimeMillis()

        // Calculate Learner Level (Simple formula or thresholds)
        // Using Config Thresholds logic here (Simplified for brevity)
        val xp = levelState.xp
        val newLevel = calculateLevel(xp) // Implement your threshold logic here
        levelState.learnerLevel = newLevel
        // levelState.progressToNextLevel = ... calc logic ...

        // Analytics
        val params = Bundle().apply {
            putInt("amount", delta)
            putString("source", source.name)
            putString("esol_level", level)
            putInt("current_learner_level", newLevel)
        }
        analytics.logEvent("xp_awarded", params)
    }

    private fun updateStreakLogic(state: XpState) {
        val today = LocalDate.now().toString()
        if (state.lastStudyDateId == today) return

        val yesterday = LocalDate.now().minusDays(1).toString()

        if (state.lastStudyDateId == yesterday) {
            // Streak continues
            state.currentStreak += 1
        } else {
            // Missed a day
            if (state.lastStudyDateId != null) {
                if (state.streakFreezeCount > 0) {
                    state.streakFreezeCount -= 1
                    justConsumedFreeze = true
                    logFreezeUsage(state.streakFreezeCount, state.currentStreak)
                } else {
                    state.currentStreak = 1 // Reset
                }
            } else {
                state.currentStreak = 1 // First ever
            }
        }

        if (state.currentStreak > state.longestStreak) {
            state.longestStreak = state.currentStreak
        }
        state.lastStudyDateId = today
    }

    private fun checkBadges(state: XpState, action: XpActionType) {
        val newBadges = mutableListOf<String>()
        val currentBadges = state.earnedBadges.toSet()

        // 1. Streak Badges
        if (state.currentStreak >= 3 && !currentBadges.contains("Warm Up")) newBadges.add("Warm Up")
        if (state.currentStreak >= 7 && !currentBadges.contains("Dedicated")) newBadges.add("Dedicated")
        if (state.currentStreak >= 30 && !currentBadges.contains("Unstoppable")) newBadges.add("Unstoppable")

        // 2. Sprint Badge (10 in 10 mins)
        if (action == XpActionType.HearNewSentence) {
            val now = LocalDateTime.now()
            recentListenTimestamps.add(now)
            // Remove older than 10 mins
            recentListenTimestamps.removeIf { it.isBefore(now.minusMinutes(10)) }

            if (recentListenTimestamps.size >= 10) {
                // Allow duplicates for Sprint
                state.earnedBadges.add("Sprint")
                recentListenTimestamps.clear() // Reset buffer
                Log.d(TAG, "🏆 Badge Earned: Sprint")
            }
        }

        // Add unique badges
        newBadges.forEach {
            state.earnedBadges.add(it)
            Log.d(TAG, "🏆 Badge Earned: $it")
        }
    }

    // MARK: - Analytics Functions

    fun logSessionDensity() {
        if (sessionActionCount > 0) {
            val params = Bundle().apply {
                putInt("action_count", sessionActionCount)
                putString("user_level", _state.value.currentLevel)
            }
            analytics.logEvent("session_end_density", params)
            Log.d(TAG, "📊 Analytics: Session Density = $sessionActionCount")
            sessionActionCount = 0
        }
    }

    private fun logFreezeUsage(remaining: Int, currentStreak: Int) {
        val params = Bundle().apply {
            putInt("remaining_freezes", remaining)
            putInt("current_streak", currentStreak)
        }
        analytics.logEvent("freeze_consumed", params)
    }

    fun logPurchaseStart(source: String) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_NAME, "premium_upgrade") // Standard param
            putString("source", source) // limit_daily_wall, etc
        }
        analytics.logEvent(FirebaseAnalytics.Event.BEGIN_CHECKOUT, params)
    }

    // MARK: - Persistence

    private fun saveToDisk(state: XpState) {
        try {
            val jsonString = gson.toJson(state)
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save XP state", e)
        }
    }

    private fun loadFromDisk() {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                val jsonString = file.readText()
                val loadedState = gson.fromJson(jsonString, XpState::class.java)
                _state.value = loadedState
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load XP state", e)
        }
    }

    // MARK: - Admin

    fun hardReset(activeLevel: String = "A1") {
        scope.launch {
            val freshState = initialState().copy(currentLevel = activeLevel)
            saveToDisk(freshState)
            _state.value = freshState
            Log.w(TAG, "🚨 HARD RESET COMPLETE")
        }
    }

    private fun initialState(): XpState {
        val levels = mutableMapOf<String, LevelXpState>()
        listOf("A1", "A2", "B1", "B2").forEach { levels[it] = LevelXpState() }
        return XpState(levels = levels)
    }

    // Helper placeholder
    private fun calculateLevel(xp: Int): Int {
        // Simple logic for example: Level = sqrt(xp/10)
        return kotlin.math.sqrt(xp / 10.0).toInt().coerceAtLeast(1)
    }
// MARK: - Goal Setting API

// MARK: - Goal Setting API

    fun setExactGoal(timestamp: Long, level: String) {
        val newState = _state.value.copy(
            targetExamDate = timestamp,
            targetExamLevel = level,
            datePrecision = "exact"
        )
        updateAndSave(newState)
    }

    fun setDurationGoal(months: Int, level: String) {
        // Calculate future date
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.MONTH, months)

        val newState = _state.value.copy(
            targetExamDate = calendar.timeInMillis,
            targetExamLevel = level,
            datePrecision = "duration"
        )
        updateAndSave(newState)
    }

    fun clearExamGoal() {
        val newState = _state.value.copy(
            targetExamDate = null,
            targetExamLevel = null,
            datePrecision = "none"
        )
        updateAndSave(newState)
    }

    // MARK: - Goal Calculation Helpers

    fun getFormattedCountdown(): String {
        val target = _state.value.targetExamDate ?: return "No Date Set"
        val now = System.currentTimeMillis()

        // Calculate diff in days
        val diff = target - now
        val days = (diff / (1000 * 60 * 60 * 24)).toInt()

        if (days < 0) return "Exam Passed"

        return when (_state.value.datePrecision) {
            "exact" -> "$days Days Left"
            "month" -> "~ ${max(1, days / 30)} Months Left"
            "duration" -> "Approx $days Days Left"
            else -> "$days Days Left"
        }
    }

    data class ExamPace(val remaining: Int, val dailyRate: Int)

    fun getExamPace(totalWords: Int, currentWordsMastered: Int): ExamPace? {
        val target = _state.value.targetExamDate ?: return null
        val now = System.currentTimeMillis()

        val diff = target - now
        val daysLeft = (diff / (1000 * 60 * 60 * 24)).toInt()

        if (daysLeft <= 0) return null // Date passed

        val remainingWords = max(0, totalWords - currentWordsMastered)

        // Calculate rate (rounding up)
        val rate = kotlin.math.ceil(remainingWords.toDouble() / daysLeft.toDouble()).toInt()

        return ExamPace(remainingWords, rate)
    }

    // MARK: - Internal Helper

    private fun updateAndSave(newState: XpState) {
        _state.value = newState
        scope.launch {
            saveToDisk(newState)
        }
    }
    fun getDailyStatsList(daysBack: Int): List<DailyStats> {
        val list = mutableListOf<DailyStats>()
        val today = LocalDate.now()

        // Loop backwards (Today, Yesterday, ...)
        for (i in (0 until daysBack).reversed()) {
            val date = today.minusDays(i.toLong())
            val id = date.toString() // "2025-12-15"

            // Return actual stats or an empty object for that day
            val stats = dailyStats[id] ?: DailyStats(id)
            list.add(stats)
        }
        return list
    }
    // MARK: - Debugging

    fun debugPrintAllStats() {
        val s = _state.value
        val tag = "XPManager"

        Timber.tag(tag).d("\n===== 🕹️ XP MANAGER STATE REPORT =====")

        // 1. General & Gamification
        Timber.tag(tag).d("👤 Current Focus:   ${s.currentLevel}")
        Timber.tag(tag).d("🏆 GAMIFICATION")
        Timber.tag(tag).d("   🔥 Current Streak: ${s.currentStreak}")
        Timber.tag(tag).d("   ⚡ Longest Streak: ${s.longestStreak}")
        Timber.tag(tag).d("   💎 Gems:           ${s.gems}")
        Timber.tag(tag).d("   ❄️ Freezes:        ${s.streakFreezeCount}")

        // 2. Badges
        if (s.earnedBadges.isEmpty()) {
            Timber.tag(tag).d("🏅 Badges: None yet")
        } else {
            Timber.tag(tag).d("🏅 Badges Unlocked (${s.earnedBadges.size}):")
            s.earnedBadges.forEach { badge ->
                Timber.tag(tag).d("   • $badge")
            }
        }

        // 3. Goal
        if (s.targetExamLevel != null) {
            Timber.tag(tag).d("🎯 Goal: ${s.targetExamLevel} (${getFormattedCountdown()})")
        } else {
            Timber.tag(tag).d("🎯 Goal: Not set")
        }

        // 4. Level Breakdown
        Timber.tag(tag).d("📚 LEVEL DETAILS")
        val sortedLevels = s.levels.toSortedMap()

        if (sortedLevels.isEmpty()) {
            Timber.tag(tag).d("   (No level data recorded)")
        } else {
            sortedLevels.forEach { (levelName, data) ->
                val activeMarker = if (levelName == s.currentLevel) "👈" else ""
                val xpStr = data.xp.toString().padEnd(5)
                Timber.tag(tag).d("   [$levelName] XP: $xpStr | Lvl: ${data.learnerLevel} $activeMarker")
            }
        }

        Timber.tag(tag).d("========================================\n")
    }
}

