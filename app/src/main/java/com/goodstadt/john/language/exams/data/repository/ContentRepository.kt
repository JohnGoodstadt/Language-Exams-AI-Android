package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.config.DebugFlags
import com.goodstadt.john.language.exams.config.LanguageConfig.mapLogicalToResourceName
import com.goodstadt.john.language.exams.config.LanguageConfig.normalizeToLogicalName
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.AudioPlayerService
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.api.GoogleCloudTTS
import com.goodstadt.john.language.exams.data.examsheets.ExamSheetRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.faultTTSAPICount
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Format0File
import com.goodstadt.john.language.exams.models.Format2File
import com.goodstadt.john.language.exams.models.Format7or10File
import com.goodstadt.john.language.exams.models.WordQuizRoot
import com.goodstadt.john.language.exams.data.GrammarSheetMapping
import com.goodstadt.john.language.exams.data.UsageQuizSheetMapping
import com.goodstadt.john.language.exams.data.SectionQuizSheetMapping
import com.goodstadt.john.language.exams.models.Format3File
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesListRoot
import com.goodstadt.john.language.exams.models.TabDetails
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.utils.logging.TimberFault
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **ContentRepository**
 *
 * A Singleton repository acting as the primary data gateway for the application's educational content.
 * It abstracts the complexity of data fetching, versioning, and media retrieval, providing a unified API for ViewModels.
 *
 * **Key Responsibilities:**
 * - **Content Resolution:** Fetches and parses structural data (Vocab Lists, Reference Sheets, Quizzes) using a Multi-Level Caching strategy (Memory L1 -> Disk L2 -> Firestore L3).
 * - **Audio Orchestration:** Implements a cost-optimized "Waterfall" strategy for TTS playback:
 *     1. Checks **Local Disk** (Free/Instant).
 *     2. Checks **Firebase Cloud Storage** (Free/Fast).
 *     3. Falls back to **Google Cloud TTS API** (Paid/Slow), then caches the result to Disk and Cloud for future use.
 * - **AI Generation:** Interfaces with the Generative AI (Gemini) API to create dynamic paragraphs and content on demand.
 *
 * **Inputs:**
 * - Sheet Identifiers (e.g., "EnglishB1Vocab") to request specific JSON structures.
 * - Raw text strings to request audio playback or AI generation.
 *
 * **Outputs:**
 * - Deserialized Data Models (`VocabFile`, `Format1File`, etc.) ready for UI consumption.
 * - `PlaybackResult` status indicating success, failure, or the source of the audio (Network vs Cache).
 *
 * **Persistence Strategy:**
 * - **Local (JSON):** Caches versioned content files to internal storage to enable offline text capability and reduce Firestore reads.
 * - **Local (Audio):** Writes generated MP3s to the device's internal `filesDir`, creating a permanent offline audio cache.
 * - **Cloud:** Uploads generated TTS audio to Firebase Storage to create a shared "Crowdsourced Cache" for other users.
 */

//enum class PlaybackSource {
//    CACHE,
//    NETWORK
//}

/*
Layer 3 (UI Logic)	ViewModels (GroupedVM, GenericVM, etc.)	To prepare UI state for a specific screen.	(No one below it)
Layer 2 (Orchestration & Business Logic)	VocabRepository	To be the single entry point for all VocabFile data. It orchestrates caching, versioning, and data source selection.	Only ViewModels.
Layer 1 (Data Source Implementation)	ExamSheetRepository	To be a low-level worker. Its only job is to manage the disk cache and network fetching for VocabFiles from Firestore.	Only VocabRepository.
 */
// In data/VocabRepository.kt
sealed class PlaybackResult {
    data object PlayedFromLocalCache : PlaybackResult()
    data object CacheNotFound : PlaybackResult()
    data object PlayedFromNetworkAndCached : PlaybackResult()
    data class Failure(val exception: Exception) : PlaybackResult()
}
sealed interface PlaybackResultSplit {

    // 1. ✅ FREE & INSTANT
    // Found on the user's device (filesDir). No network used.
    object PlayedFromCache : PlaybackResultSplit

    // 2. ✅ FREE & FAST
    // Downloaded from Firebase Storage.
    // Bandwidth cost only (negligible). Does NOT count towards Rate Limit.
    object PlayedFromCloudStorage : PlaybackResultSplit

    // 3. 💲 PAID & SLOWER
    // Generated via Google Cloud TTS API.
    // Costs money. COUNTS towards Hourly/Daily Rate Limit.
    object PlayedFromGoogleTTS : PlaybackResultSplit

    // 4. LEGACY / GENERIC
    // Kept for backwards compatibility if other parts of your app check this.
    // (Usually represents a successful network download before we distinguished Cloud vs TTS)
    object PlayedFromNetworkAndCached : PlaybackResultSplit

    // 5. FAILURES
    data class Failure(val exception: Throwable) : PlaybackResultSplit
    object CacheNotFound : PlaybackResultSplit
}

private enum class AudioDataSource {
    LOCAL_DISK,
    FIREBASE_CLOUD,
    GOOGLE_TTS
}

@Singleton
class ContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfigRepository: AppConfigRepository,
    private val examSheetRepository: ExamSheetRepository,
    private val googleCloudTts: GoogleCloudTTS,
    private val audioPlayerService: AudioPlayerService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository: TTSStatsRepository,
   // private val vocabRepository: ContentRepository,
) {
    // Cache the result in memory after the first successful load
    private val vocabCache = mutableMapOf<String, Format0File>()
    private val format1Cache = mutableMapOf<String, HeaderWordsSentencesListRoot>()
    private val format2Cache = mutableMapOf<String, Format2File>()
    private val format3Cache = mutableMapOf<String, Format3File>()
    private val format7or10Cache = mutableMapOf<String, Format7or10File>()
    private val format13Cache = mutableMapOf<String, WordQuizRoot>()

    //Problem was getVocabData() called twice sub millisecond
    // ✅ ADDED: A map to store ongoing fetch operations.
    // The key is the logicalName, the value is the Deferred result.
    private val ongoingFetches = mutableMapOf<String, Deferred<Result<Format0File>>>()
    // Define a scope for playback (Main thread is usually best for MediaPlayer)
    private val playbackScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // A lazy json parser instance with lenient configuration
    private val jsonParser = Json {
        ignoreUnknownKeys = true    // Be robust against future changes in the JSON
        coerceInputValues = true    // Use default values if a field is null
        //allowTrailingCommas = true  // NOT ALLOWED in standard json parser. allow trailing commas, common in hand-edited JSON
    }


    /**
     * The primary data orchestrator. It follows the strategy:
     * 1. Check in-memory cache (if not forcing a refresh).
     * 2. Delegate to ExamSheetRepository (for disk cache / network).
     * 3. Fallback to the app bundle.
     * It is the ONLY function that writes to the in-memory cache.
     */
    suspend fun getFormat0Data(sheet_name: String): Result<Format0File>  {
//        val parentCaller = getParentCaller()
//        val parentFunctionName = parentCaller?.methodName ?: "Unknown"
//        Timber.d("This log is from getVocabData, but it was called by: $parentFunctionName")

        // Use CoroutineScope to manage the lifecycle of our fetches
        return coroutineScope {
            val logicalName = normalizeToLogicalName(sheet_name)

            // --- 1. Check for an ONGOING fetch for this exact name ---
            ongoingFetches[logicalName]?.let { activeJob ->
                Timber.d("VocabRepo: Found an IN-PROGRESS fetch for '$logicalName'. Awaiting its result.")
                // If a job is already running, don't start a new one.
                // Just wait for the existing one to finish and return its result.
                return@coroutineScope activeJob.await()
            }
            // --- If no ongoing fetch, start a new one ---
            val newJob = async(Dispatchers.IO) {
                try {
                    // --- 1. Version Check ---
                    val remoteVersions = appConfigRepository.getRemoteSheetVersions()
                    val remoteVersion = remoteVersions[logicalName] ?: 1
                    val localVersion = appConfigRepository.getLocalVersion(logicalName)
                    val forceRefresh = remoteVersion > localVersion
                    Timber.d("VocabRepo: Sheet '$logicalName' -> Remote v$remoteVersion, Local v$localVersion, Force refresh: $forceRefresh")

                    // --- 2. In-Memory Cache Check ---
                    if (!forceRefresh) {
                        vocabCache[logicalName]?.let { cachedFile ->
                            Timber.d("VocabRepo: Returning '$logicalName' from MEMORY CACHE.")
                            return@async Result.success(cachedFile)
                        }
                    }

                    // --- DEBUG bundle-only switch (German flavour only) ---
                    // German content is still being uploaded, so getFormat0Sheet() often fails slowly
                    // before falling back to the bundle. When DebugFlags.BUNDLE_ONLY_VOCAB is on
                    // (debug builds only) the German flavour goes STRAIGHT to the bundled resource,
                    // skipping Firestore - EXCEPT for sheets listed in DebugFlags.FIRESTORE_TEST_SHEETS,
                    // which still load from Firestore so you can test individual just-uploaded files.
                    // English keeps the normal cache -> Firestore -> bundle path (all content is live).
                    if (BuildConfig.DEBUG && DebugFlags.BUNDLE_ONLY_VOCAB && BuildConfig.FLAVOR == "de"
                        && logicalName !in DebugFlags.FIRESTORE_TEST_SHEETS) {
                        val resourceName = mapLogicalToResourceName(logicalName)
                        Timber.w("VocabRepo: BUNDLE_ONLY_VOCAB on -> loading '$logicalName' straight from bundle ('$resourceName'), skipping Firestore.")
                        val bundleResult = loadBundledFormat0Data(resourceName)
                        bundleResult.getOrNull()?.let { vocabCache[logicalName] = it }
                        return@async bundleResult
                    }

                    // --- 3. Delegate to ExamSheetRepository (Disk/Network) ---
                    Timber.d("VocabRepository.getVocabData: Delegating to ExamSheetRepository for '$logicalName'...")
                    val result = examSheetRepository.getFormat0Sheet(
                        sheet_name = logicalName,
                        forceRefresh = forceRefresh
                    )

                    if (result.isSuccess) {
                        val vocabFile = result.getOrThrow()
                        // ✅ CENTRALIZED CACHING: Save the successful result to the memory cache.
                        vocabCache[logicalName] = vocabFile
                        Timber.d("VocabRepo: Warmed up memory cache for '$logicalName' from repository.")

                        if (forceRefresh) {
                            appConfigRepository.updateLocalVersion(logicalName, remoteVersion)
                        }
                        return@async result
                    }

                    // --- 4. Bundle Fallback ---
                    Timber.w(
                        result.exceptionOrNull(),
                        "VocabRepo: ExamSheetRepository failed. Falling back to bundle for '$logicalName'."
                    )
                    val resourceName = mapLogicalToResourceName(logicalName)
                    val bundleResult = loadBundledFormat0Data(resourceName)

                    // ✅ CENTRALIZED CACHING: Also cache the result from the bundle.
                    bundleResult.getOrNull()?.let { vocabCache[logicalName] = it }

                    return@async bundleResult

                } catch (e: Exception) {
                    Timber.e(
                        e,
                        "VocabRepo: CRITICAL error in orchestrator. Falling back to bundle for '$logicalName'."
                    )
                    FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository.getFormat0Data() Data load failed for $sheet_name"))

                    val resourceName = mapLogicalToResourceName(logicalName)
                    val bundleResult = loadBundledFormat0Data(resourceName)
                    bundleResult.getOrNull()?.let { vocabCache[logicalName] = it }
                    return@async bundleResult
                } finally {
                    // ✅ CRUCIAL: Remove the job from the map when it's done.
                    // This allows for a fresh fetch the next time it's requested.
                    ongoingFetches.remove(logicalName)
                    Timber.d("VocabRepo: Fetch job for '$logicalName' has completed and been removed from ongoingFetches.")
                }
            } //: End Try

            ongoingFetches[logicalName] = newJob
            // The result of the `coroutineScope` is the result of the last expression,
            // which is the awaited result of our new job.
            newJob.await()
        }//: End Return

    } //end

    suspend fun getFormat1Data(name: String): Result<HeaderWordsSentencesListRoot> = withContext(Dispatchers.IO) {
        val logicalName = normalizeToLogicalName(name)
        try {
            // --- 1. VERSION CHECK ---
            val remoteVersions = appConfigRepository.getRemoteSheetVersions()
            val remoteVersion = remoteVersions[logicalName] ?: 1
            val localVersion = appConfigRepository.getLocalVersion(logicalName)
            val forceRefresh = remoteVersion > localVersion
            Timber.d("Format 1: Sheet '$logicalName' (Format1) -> Remote v$remoteVersion, Local v$localVersion, Force refresh: $forceRefresh")

            // --- 2. IN-MEMORY CACHE CHECK ---
            if (!forceRefresh) {
                format1Cache[logicalName]?.let { cachedFile ->
                    Timber.d("Format 1: Returning '$logicalName' (Format1) from MEMORY CACHE.")
                    return@withContext Result.success(cachedFile)
                }
            }

            // --- 3. DELEGATE TO ExamSheetRepository ---
            Timber.d("VocabRepo: Delegating fetch for '$logicalName' (Format1) to ExamSheetRepository...")
            val result = examSheetRepository.getFormat1Sheet(name = logicalName, forceRefresh = forceRefresh)

            // --- 4. WARM UP MEMORY CACHE & UPDATE VERSION ---
            if (result.isSuccess) {
                val format1File = result.getOrThrow()
                format1Cache[logicalName] = format1File
                Timber.d("VocabRepo: Warmed up memory cache for '$logicalName' (Format1).")

                if (forceRefresh) {
                    appConfigRepository.updateLocalVersion(logicalName,remoteVersion)
                }
                return@withContext result
            } else{
                // --- 4. Bundle Fallback ---
                Timber.w(
                    result.exceptionOrNull(),
                    "VocabRepo: ExamSheetRepository failed. Falling back to bundle for '$logicalName'."
                )
                val resourceName = mapLogicalToResourceName(logicalName)
                val bundleResult = loadBundledFormat1Data(resourceName)

                // ✅ CENTRALIZED CACHING: Also cache the result from the bundle.
                bundleResult.getOrNull()?.let { format1Cache[logicalName] = it }

                return@withContext bundleResult
            }

        } catch (e: Exception) {
            Timber.e(e, "VocabRepo: CRITICAL error in getFormat1Data for '$logicalName'.")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository.getFormat1Data() Data load failed for $name"))
            return@withContext Result.failure(e) // We don't have a bundle fallback for this type
        }
    }

    fun loadBundledFormat1Data(resourceName: String): Result<HeaderWordsSentencesListRoot> {
        // 1. Check the in-memory cache first.
        format1Cache[resourceName]?.let { cachedFile ->
            Timber.d("Format 2: Returning '$resourceName' from MEMORY CACHE. Yippee!")
            return Result.success(cachedFile)
        }

        // 2. If not in cache, call the private loader.
        val result = _loadFromBundleFormat1(resourceName)

        // 3. On success, save the result to the in-memory cache for next time.
        result.getOrNull()?.let {
            format1Cache[resourceName] = it
            Timber.d("Format 2: Warmed up memory cache for bundled file '$resourceName'.")
        }

        return result
    }
    private fun _loadFromBundleFormat1(resourceName: String): Result<HeaderWordsSentencesListRoot> {
        return try {
            val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
            if (resourceId == 0) {
                return Result.failure(Exception("Resource file not found in bundle: $resourceName.json"))
            }
            Timber.v("Format 2: Loading '$resourceName' from res/raw.")
            val inputStream = context.resources.openRawResource(resourceId)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val format1File = jsonParser.decodeFromString<HeaderWordsSentencesListRoot>(jsonString)
            Result.success(format1File)
        } catch (e: Exception) {
            Timber.e(e, "Format 2: Failed to load from bundle: $resourceName")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository._loadFromBundle() Data load failed for $resourceName"))
            Result.failure(e)
        }
    }

    suspend fun getFormat2Data(name: String): Result<Format2File> = withContext(Dispatchers.IO) {
        val logicalName = normalizeToLogicalName(name)
        try {
            // --- 1. VERSION CHECK ---
            val remoteVersions = appConfigRepository.getRemoteSheetVersions()
            val remoteVersion = remoteVersions[logicalName] ?: 1
            val localVersion = appConfigRepository.getLocalVersion(logicalName)
            val forceRefresh = remoteVersion > localVersion
            Timber.d("ContentRepo: Sheet '$logicalName' (Format2) -> Remote v$remoteVersion, Local v$localVersion, Force refresh: $forceRefresh")

            // --- 2. IN-MEMORY CACHE CHECK ---
            if (!forceRefresh) {
                format2Cache[logicalName]?.let { cachedFile ->
                    Timber.d("ContentRepo: Returning '$logicalName' (Format2) from MEMORY CACHE.")
                    return@withContext Result.success(cachedFile)
                }
            }

            // --- 3. DELEGATE TO ExamSheetRepository ---
            Timber.d("ContentRepo: Delegating fetch for '$logicalName' (Format2) to ExamSheetRepository...")

            // This is the line you will add in the next step.
            // For now, let's create a placeholder that fails, so you can see the flow.
             val result = examSheetRepository.getFormat2Sheet(logicalName, forceRefresh = forceRefresh)
            //val result: Result<Format2File> = Result.failure(NotImplementedError("getFormat2Sheet is not yet implemented in ExamSheetRepository"))


            // --- 4. WARM UP MEMORY CACHE & UPDATE VERSION ---
            if (result.isSuccess) {
                val format2File = result.getOrThrow()
                format2Cache[logicalName] = format2File
                Timber.d("ContentRepo: Warmed up memory cache for '$logicalName' (Format2).")

                if (forceRefresh) {
                    appConfigRepository.updateLocalVersion(logicalName,remoteVersion)
                }
                return@withContext result
            } else {
                // --- 4. Bundle Fallback ---
                Timber.w(
                    result.exceptionOrNull(),
                    "VocabRepo: ExamSheetRepository failed. Falling back to bundle for '$logicalName'."
                )
                val resourceName = mapLogicalToResourceName(logicalName)
                val bundleResult = loadBundledFormat2Data(resourceName)

                // ✅ CENTRALIZED CACHING: Also cache the result from the bundle.
                bundleResult.getOrNull()?.let { format2Cache[logicalName] = it }

                return@withContext bundleResult

            }


        } catch (e: Exception) {
            Timber.e(e, "ContentRepo: CRITICAL error in getFormat2Data for '$logicalName'.")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository.getFormat2Data() failed for $name", e))
            return@withContext Result.failure(e) // Format2 does not have a bundle fallback
        }
    }

    fun loadBundledFormat2Data(resourceName: String): Result<Format2File> {
        // 1. Check the in-memory cache first.
        format2Cache[resourceName]?.let { cachedFile ->
            Timber.d("Format 2: Returning '$resourceName' from MEMORY CACHE. Yippee!")
            return Result.success(cachedFile)
        }

        // 2. If not in cache, call the private loader.
        val result = _loadFromBundleFormat2(resourceName)

        // 3. On success, save the result to the in-memory cache for next time.
        result.getOrNull()?.let {
            format2Cache[resourceName] = it
            Timber.d("Format 2: Warmed up memory cache for bundled file '$resourceName'.")
        }

        return result
    }
    private fun _loadFromBundleFormat2(resourceName: String): Result<Format2File> {
        return try {
            val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
            if (resourceId == 0) {
                return Result.failure(Exception("Resource file not found in bundle: $resourceName.json"))
            }
            Timber.v("Format 2: Loading '$resourceName' from res/raw.")
            val inputStream = context.resources.openRawResource(resourceId)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val format2File = jsonParser.decodeFromString<Format2File>(jsonString)
            Result.success(format2File)
        } catch (e: Exception) {
            Timber.e(e, "Format 2: Failed to load from bundle: $resourceName")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository._loadFromBundle() Data load failed for $resourceName"))
            Result.failure(e)
        }
    }
    /**
     * Downloads a fileFormat-7/10 sheet (grammar / usage quiz). The [name] is the logical Firestore doc
     * name, e.g. "GermanA1ModalVerbs" (grammar) or "GermanUsageQuiz1A1" (usage). Mirrors getFormat2Data:
     * version check -> memory cache -> ExamSheetRepository (disk/Firestore) -> bundle asset fallback.
     */
    suspend fun getFormat7or10Data(name: String): Result<Format7or10File> = withContext(Dispatchers.IO) {
        val logicalName = name // already the logical Firestore doc name
        try {
            // 1. Version check
            val remoteVersions = appConfigRepository.getRemoteSheetVersions()
            val remoteVersion = remoteVersions[logicalName] ?: 1
            val localVersion = appConfigRepository.getLocalVersion(logicalName)
            val forceRefresh = remoteVersion > localVersion
            Timber.d("ContentRepo: Sheet '$logicalName' (Format7/10) -> Remote v$remoteVersion, Local v$localVersion, Force refresh: $forceRefresh")

            // 2. In-memory cache
            if (!forceRefresh) {
                format7or10Cache[logicalName]?.let { cachedFile ->
                    Timber.d("ContentRepo: Returning '$logicalName' (Format7/10) from MEMORY CACHE.")
                    return@withContext Result.success(cachedFile)
                }
            }

            // 3. Delegate to ExamSheetRepository (disk cache -> Firestore -> cache to disk)
            val result = examSheetRepository.getFormat7or10Sheet(logicalName, forceRefresh = forceRefresh)

            if (result.isSuccess) {
                val file = result.getOrThrow()
                format7or10Cache[logicalName] = file
                if (forceRefresh) appConfigRepository.updateLocalVersion(logicalName, remoteVersion)
                return@withContext result
            }

            // 4. Bundle fallback (grammar/usage bundles live under assets/Quizzes/…, not res/raw)
            Timber.w(result.exceptionOrNull(), "ContentRepo: Firestore failed for '$logicalName' (Format7/10); trying bundle.")
            val assetPath = format7or10BundleAssetPath(logicalName)
                ?: return@withContext Result.failure(Exception("No bundle mapping for Format7/10 sheet '$logicalName'"))
            val bundleResult = loadBundledFormat7or10Data(logicalName, assetPath)
            bundleResult.getOrNull()?.let { format7or10Cache[logicalName] = it }
            return@withContext bundleResult

        } catch (e: Exception) {
            Timber.e(e, "ContentRepo: CRITICAL error in getFormat7or10Data for '$logicalName'.")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository.getFormat7or10Data() failed for $name", e))
            return@withContext Result.failure(e)
        }
    }

    /**
     * Downloads a fileFormat-13 section-quiz sheet as a [WordQuizRoot]. [name] is the logical Firestore
     * doc name, e.g. "GermanSectionSheetA1Adjectives1". Mirrors getFormat7or10Data: version check ->
     * memory cache -> ExamSheetRepository (disk/Firestore) -> bundle asset fallback.
     */
    suspend fun getFormat13Data(name: String): Result<WordQuizRoot> = withContext(Dispatchers.IO) {
        val logicalName = name
        try {
            val remoteVersions = appConfigRepository.getRemoteSheetVersions()
            val remoteVersion = remoteVersions[logicalName] ?: 1
            val localVersion = appConfigRepository.getLocalVersion(logicalName)
            val forceRefresh = remoteVersion > localVersion
            Timber.d("ContentRepo: Sheet '$logicalName' (Format13) -> Remote v$remoteVersion, Local v$localVersion, Force refresh: $forceRefresh")

            if (!forceRefresh) {
                format13Cache[logicalName]?.let {
                    Timber.d("ContentRepo: Returning '$logicalName' (Format13) from MEMORY CACHE.")
                    return@withContext Result.success(it)
                }
            }

            val result = examSheetRepository.getFormat13Sheet(logicalName, forceRefresh = forceRefresh)
            if (result.isSuccess) {
                val file = result.getOrThrow()
                format13Cache[logicalName] = file
                if (forceRefresh) appConfigRepository.updateLocalVersion(logicalName, remoteVersion)
                return@withContext result
            }

            // Bundle fallback (section-quiz bundles live under assets/Quizzes/SectionQuiz/…).
            Timber.w(result.exceptionOrNull(), "ContentRepo: Firestore failed for '$logicalName' (Format13); trying bundle.")
            val assetPath = SectionQuizSheetMapping.mapLogicalToResourceName(logicalName)
                ?: return@withContext Result.failure(Exception("No bundle mapping for Format13 sheet '$logicalName'"))
            val bundleResult = _loadFromBundleFormat13(assetPath)
            bundleResult.getOrNull()?.let { format13Cache[logicalName] = it }
            return@withContext bundleResult

        } catch (e: Exception) {
            Timber.e(e, "ContentRepo: CRITICAL error in getFormat13Data for '$logicalName'.")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository.getFormat13Data() failed for $name", e))
            return@withContext Result.failure(e)
        }
    }

    /** fileFormat 13 bundles are ASSETS (Quizzes/SectionQuiz/…), so read via assets, not res/raw. */
    private fun _loadFromBundleFormat13(assetPath: String): Result<WordQuizRoot> {
        return try {
            Timber.v("Format13: Loading '$assetPath' from assets.")
            val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            Result.success(jsonParser.decodeFromString<WordQuizRoot>(jsonString))
        } catch (e: Exception) {
            Timber.e(e, "Format13: Failed to load from bundle asset: $assetPath")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository._loadFromBundleFormat13() failed for $assetPath"))
            Result.failure(e)
        }
    }

    /** Grammar names carry the level (GermanA1ModalVerbs); usage names carry a UsageQuiz area. */
    private fun format7or10BundleAssetPath(logicalName: String): String? =
        if (logicalName.startsWith("GermanUsageQuiz")) {
            UsageQuizSheetMapping.mapLogicalToResourceName(logicalName)
        } else {
            GrammarSheetMapping.mapLogicalToResourceName(logicalName)
        }

    private fun loadBundledFormat7or10Data(logicalName: String, assetPath: String): Result<Format7or10File> {
        format7or10Cache[logicalName]?.let { return Result.success(it) }
        val result = _loadFromBundleFormat7or10(assetPath)
        result.getOrNull()?.let { format7or10Cache[logicalName] = it }
        return result
    }

    /** fileFormat 7/10 bundles are ASSETS (Quizzes/…), so read via assets, not res/raw. */
    private fun _loadFromBundleFormat7or10(assetPath: String): Result<Format7or10File> {
        // Try the given (-de) path first, then the -en variant: some de usage sheets ship as -en.
        val candidates = buildList {
            add(assetPath)
            if (assetPath.endsWith("-de.json")) add(assetPath.removeSuffix("-de.json") + "-en.json")
        }
        for (path in candidates) {
            try {
                Timber.v("Format7/10: Loading '$path' from assets.")
                val jsonString = context.assets.open(path).bufferedReader().use { it.readText() }
                return Result.success(jsonParser.decodeFromString<Format7or10File>(jsonString))
            } catch (e: Exception) {
                Timber.v("Format7/10: bundle asset not found: $path")
            }
        }
        val error = Exception("Format7/10 bundle asset not found for any of: $candidates")
        Timber.e(error)
        FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository._loadFromBundleFormat7or10() failed for $assetPath"))
        return Result.failure(error)
    }

    suspend fun getFormat3Data(name: String): Result<Format3File> = withContext(Dispatchers.IO) {
        val logicalName = normalizeToLogicalName(name)
        try {
            // --- 1. VERSION CHECK ---
            val remoteVersions = appConfigRepository.getRemoteSheetVersions()
            val remoteVersion = remoteVersions[logicalName] ?: 1
            val localVersion = appConfigRepository.getLocalVersion(logicalName)
            val forceRefresh = remoteVersion > localVersion
            Timber.d("ContentRepo: Sheet '$logicalName' (Format3) -> Remote v$remoteVersion, Local v$localVersion, Force refresh: $forceRefresh")

            // --- 2. IN-MEMORY CACHE CHECK ---
            if (!forceRefresh) {
                format3Cache[logicalName]?.let { cachedFile ->
                    Timber.d("ContentRepo: Returning '$logicalName' (Format3 from MEMORY CACHE.")
                    return@withContext Result.success(cachedFile)
                }
            }

            // --- 3. DELEGATE TO ExamSheetRepository ---
            Timber.d("ContentRepo: Delegating fetch for '$logicalName' (Format3) to ExamSheetRepository...")

            // This is the line you will add in the next step.
            // For now, let's create a placeholder that fails, so you can see the flow.
            val result = examSheetRepository.getFormat3Sheet(logicalName, forceRefresh = forceRefresh)
            //val result: Result<Format2File> = Result.failure(NotImplementedError("getFormat2Sheet is not yet implemented in ExamSheetRepository"))


            // --- 4. WARM UP MEMORY CACHE & UPDATE VERSION ---f
            if (result.isSuccess) {
                val format3File = result.getOrThrow()
                if (format3File.categories.isNotEmpty()){ //dont store if download error return empty object (e.g. mis-spelt sheet name)
                    format3Cache[logicalName] = format3File
                    Timber.d("ContentRepo: Warmed up memory cache for '$logicalName' (Format3).")
                }else{
                    Timber.e("ContentRepo: Error getting  sheet for '$logicalName' (Format3). Empty object. (mis-spelt sheet name?)")
                }



                if (forceRefresh) {
                    appConfigRepository.updateLocalVersion(logicalName,remoteVersion)
                }
                return@withContext result
            } else{

                // --- 4. Bundle Fallback ---
                Timber.w(
                    result.exceptionOrNull(),
                    "VocabRepo: ExamSheetRepository failed. Falling back to bundle for '$logicalName'."
                )
                val resourceName = mapLogicalToResourceName(logicalName)
                val bundleResult = loadBundledFormat3Data(resourceName)

                // ✅ CENTRALIZED CACHING: Also cache the result from the bundle.
                bundleResult.getOrNull()?.let { format3Cache[logicalName] = it }

                return@withContext bundleResult
            }


        } catch (e: Exception) {
            Timber.e(e, "ContentRepo: CRITICAL error in getFormat2Data for '$logicalName'.")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository.getFormat3Data() failed for $name", e))
            return@withContext Result.failure(e) // Format2 does not have a bundle fallback
        }
    }
    /**
     * ✅ THE FIX: A public function specifically for STATIC, bundled data.
     * It provides a simple API for fixed screens like Conjugations.
     * It uses the centralized in-memory cache for session-level performance.
     */
    fun loadBundledFormat0Data(resourceName: String): Result<Format0File> {
        // 1. Check the in-memory cache first.
        vocabCache[resourceName]?.let { cachedFile ->
            Timber.d("VocabRepo: Returning '$resourceName' from MEMORY CACHE. Yippee!")
            return Result.success(cachedFile)
        }

        // 2. If not in cache, call the private loader.
        val result = _loadFromBundleFormat0(resourceName)

        // 3. On success, save the result to the in-memory cache for next time.
        result.getOrNull()?.let {
            vocabCache[resourceName] = it
            Timber.d("VocabRepo: Warmed up memory cache for bundled file '$resourceName'.")
        }

        return result
    }

    // --- PRIVATE IMPLEMENTATION & HELPERS ---

    /**
     * ✅ RENAMED: This is now the private, "dumb" implementation.
     * Its only job is to read and parse a file from res/raw. It does no caching.
     */
    private fun _loadFromBundleFormat0(resourceName: String): Result<Format0File> {
        return try {
            val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
            if (resourceId == 0) {
                return Result.failure(Exception("Resource file not found in bundle: $resourceName.json"))
            }
            Timber.v("VocabRepo: Loading '$resourceName' from res/raw.")
            val inputStream = context.resources.openRawResource(resourceId)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val vocabFile = jsonParser.decodeFromString<Format0File>(jsonString)
            Result.success(vocabFile)
        } catch (e: Exception) {
            Timber.e(e, "VocabRepo: Failed to load from bundle: $resourceName")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository._loadFromBundle() Data load failed for $resourceName"))
            Result.failure(e)
        }
    }

    fun loadBundledFormat3Data(resourceName: String): Result<Format3File> {
        // 1. Check the in-memory cache first.
        format3Cache[resourceName]?.let { cachedFile ->
            Timber.d("Format 3: Returning '$resourceName' from MEMORY CACHE. Yippee!")
            return Result.success(cachedFile)
        }

        // 2. If not in cache, call the private loader.
        val result = _loadFromBundleFormat3(resourceName)

        // 3. On success, save the result to the in-memory cache for next time.
        result.getOrNull()?.let {
            format3Cache[resourceName] = it
            Timber.d("VocabRepo: Warmed up memory cache for bundled file '$resourceName'.")
        }

        return result
    }

    // --- PRIVATE IMPLEMENTATION & HELPERS ---

    /**
     * ✅ RENAMED: This is now the private, "dumb" implementation.
     * Its only job is to read and parse a file from res/raw. It does no caching.
     */
    private fun _loadFromBundleFormat3(resourceName: String): Result<Format3File> {
        return try {
            val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
            if (resourceId == 0) {
                return Result.failure(Exception("Resource file not found in bundle: $resourceName.json"))
            }
            Timber.v("VocabRepo: Loading '$resourceName' from res/raw.")
            val inputStream = context.resources.openRawResource(resourceId)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val format3File = jsonParser.decodeFromString<Format3File>(jsonString)
            Result.success(format3File)
        } catch (e: Exception) {
            Timber.e(e, "VocabRepo: Failed to load from bundle: $resourceName")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository._loadFromBundle() Data load failed for $resourceName"))
            Result.failure(e)
        }
    }

    suspend fun debugDecodeFormat0Data(fileName: String): Result<Format0File> = withContext(Dispatchers.IO) {

        try {
            // Dynamically get the resource ID from the filename string
            val resourceId = context.resources.getIdentifier(
                fileName,
                "raw",
                context.packageName
            )

            // Check if the resource was found
            if (resourceId == 0) {
                return@withContext Result.failure(Exception("Resource file not found: $fileName.json"))
            }

            Timber.v("Loading '$fileName' from resources.") // For debugging
            val inputStream = context.resources.openRawResource(resourceId)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val vocabFile = jsonParser.decodeFromString<Format0File>(jsonString)

            // Cache the result using the filename as the key
            //vocabCache[fileName] = vocabFile
            Result.success(vocabFile)
        } catch (e: Exception) {
            Timber.e(e.localizedMessage)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Fetches audio data for the given text from the TTS service and plays it.
     * This version includes a file-based caching mechanism.
     */
    suspend fun playTextToSpeech(
        text: String,
        uniqueSentenceId: String,
        voiceName: String, // <-- New parameter
        languageCode: String, // <-- New parameter
        onTTSApiCallStart: () -> Unit = {}, //slow call to TTS API
        onTTSApiCallComplete: () -> Unit = {} //slow call to TTS API
    ): PlaybackResult {
        // 1. Define the cache file based on the unique ID.
        // We use the app's private cache directory, which is the correct place for this.
        val cacheDir = context.filesDir
        val audioCacheFile = File(cacheDir, "$uniqueSentenceId.mp3")

        // 2. Check if the cached file exists.
        if (audioCacheFile.exists()) {
            Timber.v("Playing from cache: Yippee!") // For debugging
            // If it exists, play the audio data from the file.
            val playResult = audioPlayerService.playAudio(audioCacheFile.readBytes())

            return if (playResult.isSuccess) {
                PlaybackResult.PlayedFromLocalCache
            } else {
                PlaybackResult.Failure(
                    playResult.exceptionOrNull() as? Exception
                        ?: Exception("Unknown cache playback error")
                )
            }
        }

        // 3. If not cached, fetch from the network.
        onTTSApiCallStart()
        try {
            val audioResult = googleCloudTts.getAudioData(text, voiceName, languageCode)

            return audioResult.fold(
                onSuccess = { audioData ->
                    onTTSApiCallComplete()
                    // 4. On successful fetch, SAVE the data to the cache file.
                    try {
                        audioCacheFile.writeBytes(audioData)
                        // Timber.v("Saved to cache: ${audioCacheFile.name}  ${audioCacheFile.absolutePath}") // For debugging
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    val playResult = audioPlayerService.playAudio(audioData)

                    if (playResult.isSuccess){
                        PlaybackResult.PlayedFromNetworkAndCached

                    }else{
                        PlaybackResult.Failure(
                            playResult.exceptionOrNull() as? Exception
                                ?: Exception("Unknown network playback error")
                        )
                    }
                },
                onFailure = { exception ->
                    TimberFault.f(
                        message = "TTS API - Failure",
                        localizedMessage = exception.localizedMessage ?: "null localizedMessage",
                        secondaryText = text.take(24),
                        area = "ContentRepository.playTextToSpeech()"
                    )
                    ttsStatsRepository.incGlobalFaultCount(faultTTSAPICount)

                    PlaybackResult.Failure(
                        exception as? Exception ?: Exception(
                            "Network error",
                            exception
                        )
                    )
                }
            )
        }
        finally { }

    }


// In ContentRepository.kt



    // Helper to keep the main function clean

    // MARK: - Helper


    /**
     * Fetches audio data for the given text from the TTS service and plays it.
     * This version includes a file-based caching mechanism.
     */
    suspend fun playFromCacheIfFound(
        uniqueSentenceId: String,
    ): Boolean {
        // 1. Define the cache file based on the unique ID.
        // We use the app's private cache directory, which is the correct place for this.
        val cacheDir = context.filesDir
        val audioCacheFile = File(cacheDir, "$uniqueSentenceId.mp3")

        // 2. Check if the cached file exists.
        if (audioCacheFile.exists()) {
            Timber.v("Playing from cache: Yippee!") // For debugging
            // If it exists, play the audio data from the file.
            val playResult = audioPlayerService.playAudio(audioCacheFile.readBytes())

            return if (playResult.isSuccess) {
               true
            } else {
              false //found but error
            }
        }
       return false

    }
    // --- THIS IS THE NEW, SIMPLIFIED FUNCTION ---
    /**
     * Searches the cached vocabulary for a specific word and returns its sentences.
     * It will automatically load the current exam's vocab file if it's not already in the cache.
     *
     * @param wordKey The word to search for (e.g., "hello").
     * @return A list of sentence strings, or an empty list if not found.
     */
    suspend fun getSentencesForWord(wordKey: String): List<String> {
        // 1. Get the current exam file name from user preferences.
        val currentExamFile = userPreferencesRepository.selectedFileNameFlow.first()

        // 2. Use your existing getVocabData function. This will automatically
        //    load from the file if needed, or return instantly from the cache.
        val vocabDataResult = getFormat0Data(currentExamFile)

        // 3. Process the result to find the word.
        return vocabDataResult.fold(
            onSuccess = { vocabFile ->
                // Search within the successfully loaded/cached VocabFile
                val foundWord = vocabFile.categories
                    .flatMap { it.words }
                    .firstOrNull { it.word == wordKey }

                // Return the sentences or an empty list
                foundWord?.sentences?.map { it.sentence } ?: emptyList()
            },
            onFailure = {
                // If loading the file fails for any reason, return an empty list.
                emptyList()
            }
        )
    }
    /**
     * Checks a list of categories and returns the keys of words that have
     * at least one audio sentence cached on disk for the given voice.
     *
     * @param categories The list of categories to check.
     * @param voiceName The specific voice name used to generate the cache filename.
     * @return A Set of word keys (strings) that have cached audio.
     */
    fun getWordKeysWithCachedAudio(categories: List<Category>, voiceName: String): Set<String> {
        val cacheDir = context.filesDir

        // 1. Flatten all words from all categories into a single list.
        val allWords = categories.flatMap { it.words }

        // 2. Filter this list to keep only the words that have a cached file.
        val wordsWithCache = allWords.filter { word ->
            // Use 'any' to check if at least ONE sentence's audio exists.
            // This is efficient because it stops checking as soon as it finds one.
            word.sentences.any { sentence ->
                val uniqueSentenceId = generateUniqueSentenceId(word, sentence, voiceName)
               //Timber.e("Looking for $uniqueSentenceId")
                val audioCacheFile = File(cacheDir, "$uniqueSentenceId.mp3")
                audioCacheFile.exists()
            }
        }

        // 3. Map the filtered list of VocabWord objects to just their keys and return as a Set.
        return wordsWithCache.map { it.word }.toSet()
    }

    /**
     * Checks a list of categories and returns a Set of sentence strings that have
     * a corresponding audio file cached on disk for the given voice.
     *
     * @param categories The list of categories to check.
     * @param voiceName The specific voice name used to generate the cache filename.
     * @return A Set of sentence strings that have cached audio.
     */
    fun getSentenceKeysWithCachedAudio(categories: List<Category>, voiceName: String): Set<String> {
        val cacheDir = context.filesDir

        // This entire operation is now a single, efficient expression.
        return categories
            // 1. Flatten the structure from List<Category> to a flat List<VocabWord>.
            .flatMap { category -> category.words }
            // 2. Flatten it further from List<VocabWord> to a flat List of pairs,
            //    where each pair holds a word and one of its sentences.
            .flatMap { word ->
                word.sentences.map { sentence ->
                    word to sentence // Create a Pair(VocabWord, Sentence)
                }
            }
            // 3. Filter this list of pairs. Keep only the pairs where the
            //    corresponding audio file exists on disk.
            .filter { (word, sentence) -> // Destructure the pair for easy access
                val uniqueSentenceId = generateUniqueSentenceId(word, sentence, voiceName)
                val audioCacheFile = File(cacheDir, "$uniqueSentenceId.mp3")
                audioCacheFile.exists()
            }
            // 4. Map the filtered list of pairs to just the sentence string.
            .map { (word, sentence) ->
                generateUniqueSentenceId(word, sentence, voiceName)//sentence.sentence // We only care about the sentence string now
            }
            // 5. Convert the final List<String> into a Set<String> to get unique values.
            .toSet()
    }
    /**
     * Finds the title and tab number for the first category that contains the target word.
     *
     * @param wordKey The word to search for.
     * @return A [TabDetails] object with the category's info, or default values if not found.
     */
    suspend fun findTabDetailsForWord(wordKey: String): TabDetails {
        // 1. Get the current exam file name from user preferences.
        val currentExamFile = userPreferencesRepository.selectedFileNameFlow.first()

        // 2. Use your existing getVocabData function to load from cache or file.
        val vocabDataResult = getFormat0Data(currentExamFile)

        return vocabDataResult.fold(
            onSuccess = { vocabFile ->
                // 3. Search for the category containing the word.
                val matchingCategory = vocabFile.categories.firstOrNull { category ->
                    // The 'any' function is the Kotlin equivalent of Swift's 'contains(where:)'
                    category.words.any { it.word == wordKey }
                }

                // 4. Return the details, using the Elvis operator (?:) for default values.
                TabDetails(
                    title = matchingCategory?.title ?: "Unknown",
                    tabNumber = matchingCategory?.tabNumber ?: 1 // Default to 1 if not found
                )
            },
            onFailure = {
                // If the vocab file fails to load, return default details.
                TabDetails("Error", 1)
            }
        )
    }

// In data/VocabRepository.kt

    // Add a function like this:
    suspend fun getCategoriesForTab(tabIdentifier: String): List<Category> {
        val tabNumber = tabIdentifier.filter { it.isDigit() }.toIntOrNull() ?: return emptyList()

        // Get the current exam file name from user preferences.
        val currentExamFile = userPreferencesRepository.selectedFileNameFlow.first()
//        val currentExamFile999 =  "` vocab_data_a1"
        // Use your existing getVocabData function to load from cache or file.
        val vocabDataResult = getFormat0Data(currentExamFile)

        return vocabDataResult.fold(
            onSuccess = { vocabFile ->
                // Filter the categories to only include ones for the current tab
                vocabFile.categories.filter { it.tabNumber == tabNumber }
            },
            onFailure = { error ->
                Timber.e("Failed to get vocab data for tab $tabIdentifier", error)
                emptyList()
            }
        )
    }
    suspend fun getCategories(): List<Category> {

        // Get the current exam file name from user preferences.
        val currentExamFile = userPreferencesRepository.selectedFileNameFlow.first()
        val vocabDataResult = getFormat0Data(currentExamFile)
//        val vocabDataResult = getVocabData("vocab_data_a1")

        return vocabDataResult.fold(
            onSuccess = { vocabFile ->
                vocabFile.categories
            },
            onFailure = { error ->
                Timber.e("Failed to get vocab data categories", error)
                emptyList()
            }
        )
    }
    suspend fun getCategoryByTitle(categoryTitle: String): Category? {
        val currentExamFile = userPreferencesRepository.selectedFileNameFlow.first()
        val vocabDataResult = getFormat0Data(currentExamFile)

        return vocabDataResult.getOrNull()?.categories?.firstOrNull {
            it.title == categoryTitle
        }
    }
    /**
     * Calculates how many words in a given category have at least one cached audio file.
     *
     * @param category The Category to check.
     * @param voiceName The specific voice used for caching.
     * @return The number of words with cached audio as an Int.
     */
    fun getCompletedWordCountForCategory(category: Category, voiceName: String): Int {
        val filesDir = context.filesDir
       // val audioDir = File(filesDir, "audio_cache")
        //if (!audioDir.exists()) return 0

        return category.words.count { word ->
            // .any is efficient, it stops as soon as one cached sentence is found for a word.
            word.sentences.any { sentence ->
                val uniqueSentenceId = generateUniqueSentenceId(word, sentence, voiceName)
                val audioCacheFile = File(filesDir, "$uniqueSentenceId.mp3")
                audioCacheFile.exists()
            }
        }
    }


    // --- ADD THIS NEW FUNCTION ---
    /**
     * Delegates the command to stop audio playback to the AudioPlayerService.
     */
    fun stopPlayback() {
        audioPlayerService.stopPlayback()
    }

    /*
    New Code to split up large function playTextToSpeechAndSaveToCache() in to 3
     */
    // MARK: - The Orchestrator (Parent Function)

    suspend fun playTextToSpeechAndSaveToCacheSplit(
        text: String,
        uniqueSentenceId: String,
        voiceName: String,
        languageCode: String,
        checkCloud: Boolean = true
    ): PlaybackResultSplit {

        // 1. Check Local Disk (Fastest, Free)
        if (playFromLocalCacheIfExists(uniqueSentenceId)) {
            return PlaybackResultSplit.PlayedFromCache
        }

        // 2. Check Firebase Cloud (Fast, Free)
        // Only if allowed by logic (e.g. user has heard it before)
        if (checkCloud) {
            if (playFromCloudStorageIfExists(uniqueSentenceId)) {
                return PlaybackResultSplit.PlayedFromCloudStorage // or PlayedFromNetworkAndCached
            }
        }

        // 3. Fallback to Google TTS (Slow, Paid)
        return generateAndPlayTTS(
            text = text,
            uniqueSentenceId = uniqueSentenceId,
            voiceName = voiceName,
            languageCode = languageCode
        )
    }

    // MARK: - Step 1: Local Cache

    /**
     * Checks if file exists locally. If so, plays it.
     * Returns TRUE if found and played successfully.
     */
    suspend fun playFromLocalCacheIfExists(filename: String): Boolean {
        val localFile = File(context.filesDir, filename)

        if (localFile.exists()) {
            return try {
                val bytes = localFile.readBytes()
                val result = audioPlayerService.playAudio(bytes)
                if (result.isSuccess) {
                    Timber.v("🔊 Played from Local Disk: $filename")
                    true
                } else {
                    Timber.e(result.exceptionOrNull(), "Local file exists but failed to play")
                    false // Corrupt file? Fall through to network to repair it.
                }
            } catch (e: Exception) {
                Timber.e(e, "Error reading local file")
                false
            }
        }
        return false
    }

    // MARK: - Step 2: Cloud Storage

    /**
     * Checks if file exists in Firebase. If so, downloads, saves, and plays it.
     * Returns TRUE if found, downloaded, and played successfully.
     */
    suspend fun playFromCloudStorageIfExists(filename: String): Boolean {
        val localFile = File(context.filesDir, filename)

        return try {
            // downloadAudio returns Boolean (true if found, false if 404/error)
            val foundInCloud = FirebaseAudioService.downloadAudio(filename, localFile)

            if (foundInCloud) {
                // If download succeeded, file is now on disk. Read and Play.
                val bytes = localFile.readBytes()
                val result = audioPlayerService.playAudio(bytes)

                if (result.isSuccess) {
                    Timber.v("☁️ Played from Cloud Storage: $filename")
                    true
                } else {
                    false
                }
            } else {
                false // Not in cloud
            }
        } catch (e: Exception) {
            Timber.w("Cloud check failed: ${e.message}")
            false
        }
    }

    // MARK: - Step 3: Google TTS API

    /**
     * Calls Google API. On success: Saves to Disk, Plays, and Uploads to Cloud.
     * Returns a full PlaybackResult (Success or Failure).
     */
    suspend fun generateAndPlayTTS(
        text: String,
        uniqueSentenceId: String,
        voiceName: String,
        languageCode: String
    ): PlaybackResultSplit {

        Timber.v("🗣️ Calling Google TTS API")

        val ttsResult = googleCloudTts.getAudioData(text, voiceName, languageCode)
        val localFile = File(context.filesDir, uniqueSentenceId)

        return ttsResult.fold(
            onSuccess = { audioData ->
                // A. Save to Local Disk (Critical for Step 1 next time)
                try {
                    localFile.writeBytes(audioData)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to write TTS data to disk")
                }

                // B. Play Audio
                val playResult = audioPlayerService.playAudio(audioData)

                // C. Upload to Firebase (Background / Fire & Forget)
                // Only upload if playback worked (valid audio)
                if (playResult.isSuccess) {
                    FirebaseAudioService.uploadAudio(localFile, uniqueSentenceId, text)
                }

                if (playResult.isSuccess) {
                    PlaybackResultSplit.PlayedFromGoogleTTS
                } else {
                    PlaybackResultSplit.Failure(playResult.exceptionOrNull() as? Exception ?: Exception("TTS Playback failed"))
                }
            },
            onFailure = { exception ->
                Timber.e(exception, "TTS API Call failed")
                PlaybackResultSplit.Failure(exception as? Exception ?: Exception("TTS API error"))
            }
        )
    }
    // --- HELPER FUNCTIONS ---


}