package com.goodstadt.john.language.exams.managers

import android.content.Context
import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.models.HistoryData
import com.goodstadt.john.language.exams.models.SpacedPlay
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **HistorySyncManager**
 *
 * A Singleton repository acting as the authoritative source for the user's learning progress.
 * Implements an "Offline-First" architecture to track which content the user has consumed, ensuring data persistence across app restarts and device switches.
 *
 * **Key Responsibilities:**
 * - **Progress Tracking:** Records play counts for specific content items using Voice-Agnostic Content IDs (SHA-256 hashes of text).
 * - **Synchronization:** Manages the bidirectional sync between Local Storage and Firestore, resolving conflicts using a "Max Value Wins" strategy.
 * - **Cost Optimization:** Buffers write operations in memory and performs batched updates to Firestore only when the application backgrounds, minimizing database write costs.
 *
 * **Key Methods (Inputs/Outputs):**
 * - `isHeard(level, contentID) -> Boolean`: The primary query used by the UI to determine if a "Red Dot" or "Checkmark" should be displayed.
 * - `getPlayCount(level, contentID) -> Int`: Returns the raw engagement count for specific content.
 * - `markSentenceHeard(...)`: Input signal to increment the play count. Triggers an immediate local save and marks the state as "Dirty" for future cloud sync.
 *
 * **Persistence Strategy:**
 * - **Local (Hot):** In-memory `StateFlow` for instant UI reactivity.
 * - **Local (Cold):** Serialized JSON (`history_cache.json`) in internal storage for crash resilience and offline support.
 * - **Cloud:** **Firestore** (`users/{uid}/history/{level}`). This is the master record used to sync progress to other devices (e.g., iPad/Android Tablet).
 */

@Singleton
class HistorySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val connectivityRepository: ConnectivityRepository
) {

    companion object {
        private const val TAG = "HistorySyncManager"
        private const val FILENAME = "history_cache.json"

        // Known Levels
        private val LEVELS = listOf("A1", "A2", "B1", "B2", "Reference")

        // Spaced-repetition dot: max colour stage (1=red, 2=amber, 3=green).
        const val SPACED_MAX = 3

        // Spaced-repetition rule (RELEASE): the three colours must land on THREE SEPARATE CALENDAR DAYS.
        //   red   = 1st hearing (any day),
        //   amber = a hearing on a LATER day than the red day,
        //   green = a hearing on a LATER day than the amber day.
        // So each stage advances at most once per day and only one stage per hearing.
        //
        // DEBUG can't wait for real days, so it stands in a short seconds gap for "a later day" - allowing
        // the red -> amber -> green progression to be tested in a couple of minutes.
        const val DEBUG_SPACED_GAP_SECONDS = 30L
    }

    // In-memory store: [Level : HistoryData]
    private var history: MutableMap<String, HistoryData> = mutableMapOf()

    // ✅ Reactive State for UI (Red Dots)
    private val _historyState = MutableStateFlow<Map<String, HistoryData>>(emptyMap())
    val historyState = _historyState.asStateFlow()

    // Dirty flag to minimize writes
    private var isDirty = false

    private val gson = Gson()
    // SupervisorJob ensures one failure doesn't crash the whole scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        loadFromLocalDisk()
    }

    // MARK: - Public API

    /**
     * Call this when audio finishes playing.
     * Updates memory, emits new state, and saves to local disk immediately.
     */
    fun markSentenceHeard(level: String, sentenceHash: String) {
        val data = history.getOrPut(level) { HistoryData() }

        // Increment count
        val current = data.items[sentenceHash] ?: 0
        data.items[sentenceHash] = current + 1

        // Update Timestamp
        data.lastUpdated = System.currentTimeMillis() / 1000 // Seconds
        isDirty = true

        saveToLocalDisk()
        emitState()
    }

    /**
     * ⏪ ROLLBACK: Decrements the count. Used if optimistic playback failed.
     */
    fun undoMarkSentenceHeard(level: String, sentenceHash: String) {
        val data = history[level] ?: return

        val currentCount = data.items[sentenceHash] ?: 0

        if (currentCount > 0) {
            val newCount = currentCount - 1
            if (newCount == 0) {
                data.items.remove(sentenceHash) // Clean up
            } else {
                data.items[sentenceHash] = newCount
            }

            isDirty = true
            saveToLocalDisk()
            emitState()
        }
    }
    fun undoMarkSentenceHeardLLMAllternative(level: String, sentenceHash: String) {
        val data = history[level] ?: return
        val currentCount = data.items[sentenceHash] ?: 0

        if (currentCount > 0) {
            val newCount = currentCount - 1
            if (newCount == 0) {
                data.items.remove(sentenceHash) // Remove completely if back to 0
            } else {
                data.items[sentenceHash] = newCount
            }

            isDirty = true
            saveToLocalDisk()
            // We don't necessarily need to emitState here if the ViewModel handles the UI refresh,
            // but it's good practice.
            // _historyState.value = history.toMap()
        }
    }
    fun getPlayCount(level: String, sentenceHash: String): Int {
        return history[level]?.items?.get(sentenceHash) ?: 0
    }

    fun isHeard(level: String, sentenceHash: String): Boolean {
        return getPlayCount(level, sentenceHash) > 0
    }

    // MARK: - Spaced repetition (LOCAL-ONLY, not synced)

    /**
     * Record a play for the spaced-repetition dot. The audio always plays; this only advances the
     * red -> amber -> green stage, and ONLY when the hearing falls on a LATER CALENDAR DAY than the last
     * counted play for this sentence (and while below [SPACED_MAX]). So the three colours are earned on
     * three separate days, and playing the same sentence many times in one day keeps the same colour.
     * (DEBUG substitutes a short seconds gap for "a later day" so it can be tested quickly.)
     * Persisted locally; deliberately NOT sent to Firestore. Reusable for any level (vocab tabs now; could
     * extend to reference sheets later). Returns the current stage (0..3).
     */
    fun recordSpacedPlay(level: String, sentenceHash: String): Int {
        val data = history.getOrPut(level) { HistoryData() }
        val entry = data.spaced.getOrPut(sentenceHash) { SpacedPlay() }
        val now = System.currentTimeMillis() / 1000 // seconds

        // Advance at most ONE stage per play (so a gap can't skip red->green in one go), and only once we've
        // reached a later day than the last counted play (DEBUG: a short seconds gap stands in for a day).
        val stage = entry.count
        if (stage < SPACED_MAX && canAdvanceSpacedStage(entry.lastAt, now)) {
            entry.count += 1
            entry.lastAt = now
            // Persist + notify UI, but do NOT set isDirty: this is local-only, so it must not trigger a
            // Firestore flush of the (unrelated) synced play counts.
            saveToLocalDisk()
            emitState()
        }
        return entry.count
    }

    /**
     * True when a hearing at [nowSeconds] may advance the spaced dot by one stage, given the last counted
     * play at [lastAtSeconds] (epoch seconds). RELEASE: [nowSeconds] must be on a strictly later local
     * calendar day. DEBUG: at least [DEBUG_SPACED_GAP_SECONDS] must have elapsed. The first hearing always
     * qualifies (lastAt is 0 -> 1970, always an earlier day / long-ago).
     */
    private fun canAdvanceSpacedStage(lastAtSeconds: Long, nowSeconds: Long): Boolean {
        if (BuildConfig.DEBUG) {
            return (nowSeconds - lastAtSeconds) >= DEBUG_SPACED_GAP_SECONDS
        }
        val zone = ZoneId.systemDefault()
        val lastDay = Instant.ofEpochSecond(lastAtSeconds).atZone(zone).toLocalDate()
        val nowDay: LocalDate = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate()
        return nowDay.isAfter(lastDay)
    }

    /** Current spaced-repetition stage for a sentence (0 = none, 1 = red, 2 = amber, 3 = green). */
    fun getSpacedCount(level: String, sentenceHash: String): Int {
        return history[level]?.spaced?.get(sentenceHash)?.count ?: 0
    }

    /**
     * Snapshot helper for background processing (Thread safe copy)
     */
    fun getSnapshot(level: String): Map<String, Int> {
        return history[level]?.items?.toMap() ?: emptyMap()
    }

    // MARK: - Cloud Sync Logic

    /**
     * Call on App Launch (Foreground)
     */
    fun fetchCloudUpdates() {
        val uid = auth.currentUser?.uid ?: return

        scope.launch {
            // Check all levels concurrently
            LEVELS.map { level ->
                async { syncLevel(uid, level) }
            }.awaitAll()
        }
    }

    private suspend fun syncLevel(uid: String, level: String) {

        if (connectivityRepository.isCurrentlyOffline()) { return } //No point in calling if offline

        try {
            val docRef = db.collection("users").document(uid).collection("history").document(level)
            val snapshot = docRef.get().await()

            if (snapshot.exists()) {
                val cloudTimestamp = snapshot.getLong("last_updated") ?: 0L
                val rawData = snapshot.get("data") as? Map<String, Long> ?: emptyMap()

                // Firestore stores numbers as Long, map to Int
                val cloudItems = rawData.mapValues { it.value.toInt() }

                // Check Timestamps
                val localTimestamp = history[level]?.lastUpdated ?: 0L

                if (cloudTimestamp > localTimestamp) {
                    Log.d(TAG, "☁️ Cloud has newer data for $level. Merging.")
                    mergeDown(level, cloudItems, cloudTimestamp)
                } else {
                    Log.v(TAG, "✅ Local $level is up to date.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed for $level", e)
        }
    }

    private fun mergeDown(level: String, cloudItems: Map<String, Int>, cloudTime: Long) {
        val data = history.getOrPut(level) { HistoryData() }

        // Strategy: Max(Local, Cloud)
        for ((key, cloudCount) in cloudItems) {
            val localCount = data.items[key] ?: 0
            if (cloudCount > localCount) {
                data.items[key] = cloudCount
            }
        }
        data.lastUpdated = cloudTime
        saveToLocalDisk()
        emitState()
    }

    /**
     * Call on App Background (onStop)
     */
    fun flushToFirebase() {
        val uid = auth.currentUser?.uid
        if (!isDirty || uid == null) return

        Log.d(TAG, "☁️ Flushing History changes to Firebase...")

        scope.launch {
            history.forEach { (level, data) ->
                val docRef = db.collection("users").document(uid).collection("history").document(level)

                val payload = mapOf(
                    "last_updated" to data.lastUpdated,
                    "data" to data.items
                )

                docRef.set(payload, SetOptions.merge())
            }
            isDirty = false
        }
    }

    // MARK: - Testing / Debugging

    /**
     * 🚨 DESTRUCTIVE: Wipes history for a specific level from Memory, Disk, and Cloud.
     */
    fun clearHistoryForLevel(level: String) {
        val uid = auth.currentUser?.uid ?: return

        scope.launch {
            // 1. Clear Memory
            history[level] = HistoryData() // Reset

            // 2. Clear Local Disk
            saveToLocalDisk()
            emitState()

            // 3. Clear Cloud
            try {
                db.collection("users")
                    .document(uid)
                    .collection("history")
                    .document(level)
                    .delete()
                    .await()

                Log.d(TAG, "🗑️ Cleared history for level: $level (Local & Cloud)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete cloud document for $level", e)
            }
        }
    }

    // MARK: - Local Persistence Helpers

    private fun saveToLocalDisk() {
        // Run in background to avoid blocking UI thread if called synchronously
        scope.launch {
            try {
                val jsonString = gson.toJson(history)
                val file = File(context.filesDir, FILENAME)
                file.writeText(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save history to disk", e)
            }
        }
    }

    private fun loadFromLocalDisk() {
        // Run blocking on init to ensure data is ready for UI immediately
        try {
            val file = File(context.filesDir, FILENAME)
            if (file.exists()) {
                val jsonString = file.readText()
                val type = object : TypeToken<MutableMap<String, HistoryData>>() {}.type
                history = gson.fromJson(jsonString, type)
                emitState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load history from disk", e)
        }
    }

    private fun emitState() {
        _historyState.value = history.toMap()
    }

    // MARK: - Debugging
    fun debugPrintAllHistory() {
        Log.d(TAG, "\n📜 ===== HISTORY SYNC MANAGER REPORT =====")
        if (history.isEmpty()) {
            Log.d(TAG, "   (History is empty)")
            return
        }

        var grandTotalUnique = 0
        var grandTotalPlays = 0

        history.toSortedMap().forEach { (level, data) ->
            val uniqueCount = data.items.size
            val totalPlays = data.items.values.sum()

            grandTotalUnique += uniqueCount
            grandTotalPlays += totalPlays

            Log.d(TAG, "\n   📂 LEVEL: $level")
            Log.d(TAG, "   🔢 Unique Items: $uniqueCount")
            Log.d(TAG, "   ▶️ Total Plays: $totalPlays")

            // Sample top 3
            data.items.entries.sortedByDescending { it.value }.take(12).forEach {
                Log.d(TAG, "      • ${it.key} : ${it.value} plays")
            }
        }
        Log.d(TAG, "   ======================================")
        Log.d(TAG, "   Σ  GRAND TOTAL: $grandTotalUnique items / $grandTotalPlays plays")
        Log.d(TAG, "   ======================================\n")
    }
    // MARK: - Debugging / Testing

    /**
     * 🚨 DEBUG: Removes a specific sentence from history to test completion triggers.
     */
    fun debugUnhearSentence(level: String, sentenceHash: String) {
        val data = history[level] ?: return

        if (data.items.containsKey(sentenceHash)) {
            // Remove completely
            data.items.remove(sentenceHash)

            data.lastUpdated = System.currentTimeMillis() / 1000
            isDirty = true

            saveToLocalDisk()
            emitState() // Updates UI

            Log.d(TAG, "📉 Debug: Unheard item in level $level")
        }
    }
}