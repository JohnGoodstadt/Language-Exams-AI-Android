package com.goodstadt.john.language.exams.data

import android.content.Context
import android.util.Log
import com.goodstadt.john.language.exams.models.HistoryData
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistorySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TAG = "HistorySyncManager"
        private const val FILENAME = "history_cache.json"

        // Known Levels
        private val LEVELS = listOf("A1", "A2", "B1", "B2", "Reference")
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

    fun getPlayCount(level: String, sentenceHash: String): Int {
        return history[level]?.items?.get(sentenceHash) ?: 0
    }

    fun isHeard(level: String, sentenceHash: String): Boolean {
        return getPlayCount(level, sentenceHash) > 0
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
            data.items.entries.sortedByDescending { it.value }.take(3).forEach {
                Log.d(TAG, "      • ${it.key.take(20)}... : ${it.value} plays")
            }
        }
        Log.d(TAG, "   ======================================")
        Log.d(TAG, "   Σ  GRAND TOTAL: $grandTotalUnique items / $grandTotalPlays plays")
        Log.d(TAG, "   ======================================\n")
    }
}