package com.goodstadt.john.language.exams.data

import android.content.Context
import com.goodstadt.john.language.exams.data.api.GoogleCloudTTS
import com.goodstadt.john.language.exams.data.examsheets.ExamSheetRepository
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesListRoot
import com.goodstadt.john.language.exams.models.TabDetails
import com.goodstadt.john.language.exams.models.Format0File
import com.goodstadt.john.language.exams.models.Format2File
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackSource {
    CACHE,
    NETWORK
}

/*
Layer 3 (UI Logic)	ViewModels (GroupedVM, GenericVM, etc.)	To prepare UI state for a specific screen.	(No one below it)
Layer 2 (Orchestration & Business Logic)	VocabRepository	To be the single entry point for all VocabFile data. It orchestrates caching, versioning, and data source selection.	Only ViewModels.
Layer 1 (Data Source Implementation)	ExamSheetRepository	To be a low-level worker. Its only job is to manage the disk cache and network fetching for VocabFiles from Firestore.	Only VocabRepository.
 */
// In data/VocabRepository.kt
sealed class PlaybackResult {
    data object PlayedFromCache : PlaybackResult()
    data object CacheNotFound : PlaybackResult()
    data object PlayedFromNetworkAndCached : PlaybackResult()
    data class Failure(val exception: Exception) : PlaybackResult()
}

@Singleton
class ContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfigRepository: AppConfigRepository,
    private val examSheetRepository: ExamSheetRepository,
    private val googleCloudTts: GoogleCloudTTS,
    private val audioPlayerService: AudioPlayerService,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    // Cache the result in memory after the first successful load
    private val vocabCache = mutableMapOf<String, Format0File>()
    private val format1Cache = mutableMapOf<String, HeaderWordsSentencesListRoot>()
    private val format2Cache = mutableMapOf<String, Format2File>()

    //Problem was getVocabData() called twice sub millisecond
    // ✅ ADDED: A map to store ongoing fetch operations.
    // The key is the logicalName, the value is the Deferred result.
    private val ongoingFetches = mutableMapOf<String, Deferred<Result<Format0File>>>()


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
            }
            return@withContext result
        } catch (e: Exception) {
            Timber.e(e, "VocabRepo: CRITICAL error in getFormat1Data for '$logicalName'.")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository.getFormat1Data() Data load failed for $name"))
            return@withContext Result.failure(e) // We don't have a bundle fallback for this type
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
            }
            return@withContext result

        } catch (e: Exception) {
            Timber.e(e, "ContentRepo: CRITICAL error in getFormat2Data for '$logicalName'.")
            FirebaseCrashlytics.getInstance().recordException(Exception("ContentRepository.getFormat2Data() failed for $name", e))
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
        val result = _loadFromBundle(resourceName)

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
    private fun _loadFromBundle(resourceName: String): Result<Format0File> {
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
                PlaybackResult.PlayedFromCache
            } else {
                PlaybackResult.Failure(playResult.exceptionOrNull() as? Exception ?: Exception("Unknown cache playback error"))
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
                        PlaybackResult.Failure(playResult.exceptionOrNull() as? Exception ?: Exception("Unknown network playback error"))
                    }
                },
                onFailure = { exception ->
                    PlaybackResult.Failure(exception as? Exception ?: Exception("Network error", exception))
                }
            )
        }
        finally { }

    }
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

    // --- HELPER FUNCTIONS ---
    /**
     * ✅ ADDED: This is the "Anti-Corruption Layer".
     * It ensures that any legacy resource names are immediately converted to the
     * canonical logical name used throughout the new system.
     */
    private fun normalizeToLogicalName(resourceName: String): String {
        return when (resourceName) {
            "vocab_data_a1" -> "EnglishA1Vocab"
            "vocab_data_a2" -> "EnglishA2Vocab"
            "vocab_data_b1" -> "EnglishB1Vocab"
            "vocab_data_b2" -> "EnglishB2Vocab"
            "conjugations_to_be" -> "EnglishBConjugationsToBe"
            "conjugations_to_have" -> "EnglishBConjugationsToHave"
            "conjugations_to_do" -> "EnglishBConjugationsToDo"
            "conjugations_to_get" -> "EnglishBConjugationsToGet"

            // Add any other legacy mappings here

            // If the name is already in the correct format, just return it.
            else -> resourceName
        }
    }
    /**
     * Maps the logical Firestore name to the Android-specific resource name.
     */
    private fun mapLogicalToResourceName(sheet_name: String): String {
        return when (sheet_name) {
            "EnglishA1Vocab" -> "vocab_data_a1"
            "EnglishA2Vocab" -> "vocab_data_a2"
            "EnglishB1Vocab" -> "vocab_data_b1"
            "EnglishB2Vocab" -> "vocab_data_b2"
            "EnglishBConjugationsToBe" -> "conjugations_to_be"
            "EnglishBConjugationsToHave" -> "conjugations_to_have"
            "EnglishBConjugationsToDo" -> "conjugations_to_do"
            "EnglishBConjugationsToGet" -> "conjugations_to_get"
            // Add other mappings here as needed
            else -> sheet_name // Fallback for other files
        }
    }

}