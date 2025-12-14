package com.goodstadt.john.language.exams.data


import android.content.Context
import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.repository.UserPreferencesRepository
import com.goodstadt.john.language.exams.models.Format0File
import com.goodstadt.john.language.exams.models.TabNumberEnum
import com.goodstadt.john.language.exams.utils.CategoryProgress
import com.goodstadt.john.language.exams.utils.ReferenceStats
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class AudioCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyManager: HistorySyncManager,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    // --- Dependencies ---
    private val prefs = context.getSharedPreferences("audio_cache_prefs", Context.MODE_PRIVATE)
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true } // Or Gson if you prefer
    private val analytics = FirebaseAnalytics.getInstance(context)

    // --- Concurrency ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // --- StateFlows (Observables for UI) ---
    private val _totalExamWordCount = MutableStateFlow(0)
    val totalExamWordCount = _totalExamWordCount.asStateFlow()

    private val _totalExamWordsHeardOverall = MutableStateFlow(0)
    val totalExamWordsHeardOverall = _totalExamWordsHeardOverall.asStateFlow()

    private val _totalExamWordHeardCount = MutableStateFlow<Map<TabNumberEnum, Int>>(emptyMap())
    val totalExamWordHeardCount = _totalExamWordHeardCount.asStateFlow()

    private val _currentVoicePrefix = MutableStateFlow("")
    val currentVoicePrefix = _currentVoicePrefix.asStateFlow()

    // --- Internal Storage ---

    // 1. Disk Cache: Flat set of filenames on disk (e.g. "GoogleUK_Hello_hash.mp3")
    // Used for playback logic ("Do I need to download?")
    private var cachedSentences: MutableSet<String> = mutableSetOf()

    // 2. Main Quest Stats: Calculated from History + VocabFile
    private var categoryHeardCounts: MutableMap<String, Int> = mutableMapOf()
    private var categoryTotalCounts: MutableMap<String, Int> = mutableMapOf()

    // 3. Reference Stats: Stored in StateFlow for Reactivity
    private val _referenceHeardCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val referenceHeardCounts = _referenceHeardCounts.asStateFlow()

    private var referenceTotalCounts: MutableMap<String, Int> = mutableMapOf()
    private var aiParagraphCount: Int = 0
    private var aiParagraphHeardCount: Int = 0

    // 4. Cached User Preferences (Synchronous Access)
    private var cachedCurrentLevel: String = "B1"

    private var currentVocabFile: Format0File? = null

    // Keys
    private val REF_HEARD_KEY = "ref_heard_counts_v1"
    private val REF_TOTAL_KEY = "ref_total_counts_v1"
    private val AI_PARA_COUNT_KEY = "ai_paragraph_count_v1"
    private val AI_PARA_HEARD_KEY = "ai_paragraph_heard_count_v1"

    init {
        loadReferenceStats()

        // Listen to User Preferences for synchronous access
        scope.launch {
            userPreferencesRepository.selectedSkillLevelFlow.collect { level ->
                cachedCurrentLevel = level
            }
        }

        // Run Global Legacy Migration on startup (background)
        migrateAllLegacyFiles()
    }

    // MARK: - Public API for Views

    fun getStats(forCategoryTitle: String): CategoryStats {
        val h = categoryHeardCounts[forCategoryTitle] ?: 0
        val t = categoryTotalCounts[forCategoryTitle] ?: 0
        return CategoryStats(heard = h, total = t)
    }

    fun getAllCategoryProgress(): List<CategoryProgress> {
        val allTitles = (categoryTotalCounts.keys + categoryHeardCounts.keys).distinct().sorted()

        return allTitles.map { title ->
            CategoryProgress(
                title = title,
                total = categoryTotalCounts[title] ?: 0,
                heard = categoryHeardCounts[title] ?: 0,
                tabNumber = 1,
                sortOrder = 1
            )
        }
    }

    // MARK: - Playback Logic

    /**
     * Call this when a sentence starts playing (Local or Cloud).
     * Updates Disk Cache List and triggers History update.
     */
    fun didPlaySentence(text: String, categoryTitle: String, categoryTabNumber: Int) {
        scope.launch {
            // 1. Update Disk Cache List (Voice Specific)
            val voice = _currentVoicePrefix.value
            val filename = FirebaseAudioService.generateUnifiedFilename(text, voice)

            mutex.withLock {
                cachedSentences.add(filename)
            }

            // 2. Update History (Voice Agnostic)
            val contentID = FirebaseAudioService.generateContentID(text)
            val level = getCurrentAppLevel()

            // Check previous state
            val previousCount = historyManager.getPlayCount(level, contentID)
            val isFirstTime = previousCount == 0

            // Update History
            historyManager.markSentenceHeard(level, contentID)

            // 3. Update UI Counters if new
            if (isFirstTime) {
                mutex.withLock {
                    val title = categoryTitle.trim()
                    categoryHeardCounts[title] = (categoryHeardCounts[title] ?: 0) + 1
                    _totalExamWordsHeardOverall.value += 1

                    TabNumberEnum.fromInt(categoryTabNumber)?.let { tab ->
                        val newMap = _totalExamWordHeardCount.value.toMutableMap()
                        newMap[tab] = (newMap[tab] ?: 0) + 1
                        _totalExamWordHeardCount.value = newMap
                    }
                }
                Timber.tag("AudioCacheManager").d("📈 Progress: New word mastered! ($text)")
            }
        }
    }

    // MARK: - Voice & Load Logic

    fun setCurrentVocabFile(vocabFile: Format0File, voicePrefix: String) {
        this.currentVocabFile = vocabFile
        if (_currentVoicePrefix.value != voicePrefix) {
            _currentVoicePrefix.value = voicePrefix
        }

        // Always refresh stats on load
        refreshStatsFromHistory()
    }

    /**
     * Recalculates UI counters by comparing Vocab List vs History
     */
    private fun refreshStatsFromHistory() {
        val file = currentVocabFile ?: return
        val level = getCurrentAppLevel()

        scope.launch {
            val start = System.currentTimeMillis()

            // Local Accumulators
            val newTotalByTab = mutableMapOf<TabNumberEnum, Int>()
            val newHeardByTab = mutableMapOf<TabNumberEnum, Int>()
            val newHeardByCat = mutableMapOf<String, Int>()
            val newTotalByCat = mutableMapOf<String, Int>()

            // Get Snapshot for speed
            val historySnapshot = historyManager.getSnapshot(level)

            for (category in file.categories) {
                val tab = TabNumberEnum.fromInt(category.tabNumber) ?: continue
                val title = category.title.trim()
                val words = category.words

                // Totals
                newTotalByTab[tab] = (newTotalByTab[tab] ?: 0) + words.size
                newTotalByCat[title] = (newTotalByCat[title] ?: 0) + words.size

                // Heard
                var heardCount = 0
                for (wordEntry in words) {
                    val sentence = wordEntry.sentences.firstOrNull()?.sentence ?: continue
                    val contentID = FirebaseAudioService.generateContentID(sentence)

                    if ((historySnapshot[contentID] ?: 0) > 0) {
                        heardCount++
                    }
                }

                newHeardByTab[tab] = (newHeardByTab[tab] ?: 0) + heardCount
                newHeardByCat[title] = (newHeardByCat[title] ?: 0) + heardCount
            }

            // Publish
            mutex.withLock {
                categoryTotalCounts = newTotalByCat
                categoryHeardCounts = newHeardByCat

                _totalExamWordCount.value = newTotalByTab.values.sum()
                _totalExamWordHeardCount.value = newHeardByTab
                _totalExamWordsHeardOverall.value = newHeardByTab.values.sum()
            }

            Timber.tag("AudioCacheManager").d("📊 Stats refreshed from History in ${System.currentTimeMillis() - start}ms")
        }
    }

    // MARK: - Reference Stats API

    fun updateReferenceStats(key: String, heard: Int, total: Int) {
        scope.launch {
            mutex.withLock {
                referenceTotalCounts[key] = total

                // Copy-Modify-Write pattern for StateFlow
                val newMap = _referenceHeardCounts.value.toMutableMap()
                newMap[key] = heard
                _referenceHeardCounts.value = newMap

                saveReferenceStats()
            }
        }
    }

    fun getReferenceStats(key: String): ReferenceStats {
        val h = _referenceHeardCounts.value[key] ?: 0
        val t = referenceTotalCounts[key] ?: 0
        return ReferenceStats(heard = h, total = t)
    }

    // MARK: - Legacy Migration (Global)

    /**
     * Scans the entire files directory for Legacy filenames (Voice_Sentence.mp3).
     * 1. Extracts Voice and Sentence.
     * 2. Renames to Unified format.
     * 3. Updates History.
     */
    fun migrateAllLegacyFiles() {
        scope.launch {
            val migrationKey = "global_legacy_migration_complex_v1"
            if (prefs.getBoolean(migrationKey, false)) return@launch

            val filesDir = context.filesDir
            val currentLevel = getCurrentAppLevel()

            Timber.tag("Migration").i("🧹 Starting Global Legacy Migration...")

            var renamedCount = 0
            var historyUpdatedCount = 0

            val allFiles = filesDir.listFiles() ?: emptyArray()

            for (file in allFiles) {
                if (!file.name.endsWith(".mp3")) continue

                // Skip if already new format
                if (isAlreadyUnified(file.name)) continue

                // 1. Parse
                val parts = parseComplexLegacyFilename(file.name) ?: continue

                // 2. Generate New Name
                val newUnifiedFilename = FirebaseAudioService.generateUnifiedFilename(parts.sentence, parts.voice)
                val newFile = File(filesDir, newUnifiedFilename)

                // 3. Rename
                if (!newFile.exists()) {
                    if (file.renameTo(newFile)) {
                        renamedCount++
                    } else {
                        continue
                    }
                } else {
                    file.delete() // Duplicate
                }

                // 4. Update History
                val contentID = FirebaseAudioService.generateContentID(parts.sentence)
                if (historyManager.getPlayCount(currentLevel, contentID) == 0) {
                    historyManager.markSentenceHeard(context, currentLevel, contentID)
                    historyUpdatedCount++
                }
            }

            if (renamedCount > 0 || historyUpdatedCount > 0) {
                Timber.tag("Migration").i("✅ Global Migration: Renamed $renamedCount, History +$historyUpdatedCount")
                historyManager.flushToFirebase()
                refreshStatsFromHistory()
            } else {
                Timber.tag("Migration").i("ℹ️ No legacy files found.")
            }

            prefs.edit().putBoolean(migrationKey, true).apply()
        }
    }

    // MARK: - Migration Helpers

    data class LegacyFileParts(val voice: String, val word: String, val sentence: String)

    private fun parseComplexLegacyFilename(filename: String): LegacyFileParts? {
        if (!filename.endsWith(".mp3")) return null
        val nameWithoutExt = filename.dropLast(4)

        val dotIndex = nameWithoutExt.indexOf('.')
        if (dotIndex == -1) return null

        val prefixSection = nameWithoutExt.substring(0, dotIndex)
        val sentenceSection = nameWithoutExt.substring(dotIndex + 1)

        val lastUnderscore = prefixSection.lastIndexOf('_')
        if (lastUnderscore == -1) return null

        val voice = prefixSection.substring(0, lastUnderscore)
        val word = prefixSection.substring(lastUnderscore + 1)
        val sentence = sentenceSection.replace("_", " ")

        return LegacyFileParts(voice, word, sentence)
    }

    private fun isAlreadyUnified(filename: String): Boolean {
        // Look for underscore + 10 hex chars + .mp3 at end
        val regex = Regex("_[0-9a-f]{10}\\.mp3$")
        return regex.containsMatchIn(filename)
    }

    // MARK: - Helpers

    private fun getCurrentAppLevel(): String {
        return cachedCurrentLevel
    }

    // MARK: - Persistence & Loading

    private fun loadReferenceStats() {
        try {
            // Note: Update JSON handling to use your preferred library (Gson or Kotlinx.Serialization)
            // Assuming simplified string loading here for brevity, match your actual implementation.
            val heardJson = prefs.getString(REF_HEARD_KEY, null)
            if (heardJson != null) {
                // _referenceHeardCounts.value = ... load map ...
            }

            // Load others...
            aiParagraphCount = prefs.getInt(AI_PARA_COUNT_KEY, 0)
            aiParagraphHeardCount = prefs.getInt(AI_PARA_HEARD_KEY, 0)

        } catch (e: Exception) {
            Timber.tag("AudioCacheManager").e(e, "Error loading stats")
        }
    }

    private fun saveReferenceStats() {
        try {
            val editor = prefs.edit()
            // Save map...
            editor.putInt(AI_PARA_COUNT_KEY, aiParagraphCount)
            editor.putInt(AI_PARA_HEARD_KEY, aiParagraphHeardCount)
            editor.apply()
        } catch (e: Exception) {
            Timber.tag("AudioCacheManager").e(e, "Error saving stats")
        }
    }

    // MARK: - AI & Debugging
    // (Keep your existing AI log functions and debugPrintAllReferenceStats here)
    // Ensure debugPrintAllReferenceStats uses _referenceHeardCounts.value

    fun getAIParagraphCount(): Int = aiParagraphCount
    fun getAIParagraphHeardCount(): Int = aiParagraphHeardCount
}