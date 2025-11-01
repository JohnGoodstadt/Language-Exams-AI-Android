package com.goodstadt.john.language.exams.data

import android.content.Context
import android.util.Log
import com.goodstadt.john.language.exams.data.api.GoogleCloudTTS
import com.goodstadt.john.language.exams.data.examsheets.ExamSheetRepository
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.TabDetails
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
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
class VocabRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfigRepository: AppConfigRepository,
    private val examSheetRepository: ExamSheetRepository,
    private val googleCloudTts: GoogleCloudTTS,
    private val audioPlayerService: AudioPlayerService,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    // Cache the result in memory after the first successful load
    private val vocabCache = mutableMapOf<String, VocabFile>()


    //Problem was getVocabData() called twice sub millisecond
    // ✅ ADDED: A map to store ongoing fetch operations.
    // The key is the logicalName, the value is the Deferred result.
    private val ongoingFetches = mutableMapOf<String, Deferred<Result<VocabFile>>>()
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
    suspend fun getVocabData(name: String): Result<VocabFile>  {
//        val parentCaller = getParentCaller()
//        val parentFunctionName = parentCaller?.methodName ?: "Unknown"
//        Timber.d("This log is from getVocabData, but it was called by: $parentFunctionName")

        // Use CoroutineScope to manage the lifecycle of our fetches
        return coroutineScope {
            val logicalName = normalizeToLogicalName(name)

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
                    val result = examSheetRepository.getVocabSheet(
                        name = logicalName,
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
                    val bundleResult = loadBundledVocabData(resourceName)

                    // ✅ CENTRALIZED CACHING: Also cache the result from the bundle.
                    bundleResult.getOrNull()?.let { vocabCache[logicalName] = it }

                    return@async bundleResult

                } catch (e: Exception) {
                    Timber.e(
                        e,
                        "VocabRepo: CRITICAL error in orchestrator. Falling back to bundle for '$logicalName'."
                    )
                    val resourceName = mapLogicalToResourceName(logicalName)
                    val bundleResult = loadBundledVocabData(resourceName)
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

    } //end getVocabData()



    /**
     * ✅ THE FIX: A public function specifically for STATIC, bundled data.
     * It provides a simple API for fixed screens like Conjugations.
     * It uses the centralized in-memory cache for session-level performance.
     */
    fun loadBundledVocabData(resourceName: String): Result<VocabFile> {
        // 1. Check the in-memory cache first.
        vocabCache[resourceName]?.let { cachedFile ->
            Timber.d("VocabRepo: Returning '$resourceName' from MEMORY CACHE.")
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
    private fun getParentCaller(): StackTraceElement? {
        // The call stack is an array of stack trace elements.
        val stackTrace = Thread.currentThread().stackTrace

        // Let's analyze the stack from the point of view of this helper function:
        // stackTrace[0] == Thread.getStackTrace()
        // stackTrace[1] == getParentCaller() (this function)
        // stackTrace[2] == functionB() (the function that called this helper)
        // stackTrace[3] == functionA() (THE PARENT we are looking for!)

        // We need to make sure the stack is deep enough before accessing the index.
        return if (stackTrace.size > 3) {
            stackTrace[3]
        } else {
            null
        }
    }
    /**
     * ✅ RENAMED: This is now the private, "dumb" implementation.
     * Its only job is to read and parse a file from res/raw. It does no caching.
     */
    private fun _loadFromBundle(resourceName: String): Result<VocabFile> {
        return try {
            val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
            if (resourceId == 0) {
                return Result.failure(Exception("Resource file not found in bundle: $resourceName.json"))
            }
            Timber.v("VocabRepo: Loading '$resourceName' from res/raw.")
            val inputStream = context.resources.openRawResource(resourceId)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val vocabFile = jsonParser.decodeFromString<VocabFile>(jsonString)
            Result.success(vocabFile)
        } catch (e: Exception) {
            Timber.e(e, "VocabRepo: Failed to load from bundle: $resourceName")
            Result.failure(e)
        }
    }
    /**
     * The original function, now renamed to be a private fallback for loading from res/raw.
     */
    fun loadFromBundleOriginal(resourceName: String): Result<VocabFile> {
        vocabCache[resourceName]?.let { cachedVocabFile ->
            Timber.d("Repo: Returning '$resourceName' from MEMORY CACHE. Yippee!")
            return Result.success(cachedVocabFile)
        }

        try {
            Timber.d("loadFromBundle: Sheet '$resourceName'")
            val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
            if (resourceId == 0) {
                return Result.failure(Exception("Resource file not found: $resourceName.json"))
            }

            Timber.v("Loading '$resourceName' from local bundle.")
            val inputStream = context.resources.openRawResource(resourceId)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val vocabFile = jsonParser.decodeFromString<VocabFile>(jsonString)

            // Cache the result from the bundle so we don't read the file again this session
            vocabCache[resourceName] = vocabFile
            return Result.success(vocabFile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load from bundle: $resourceName")
            return Result.failure(e)
        }
    }

    suspend fun debugDecodeVocabData(fileName: String): Result<VocabFile> = withContext(Dispatchers.IO) {

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
            val vocabFile = jsonParser.decodeFromString<VocabFile>(jsonString)

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
        val vocabDataResult = getVocabData(currentExamFile)

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
    fun getSentenceKeysWithCachedAudioWorse(categories: List<Category>, voiceName: String): Set<String> {
        val cacheDir = context.filesDir

        // 1. Flatten all words from all categories into a single list.
        val allWords = categories.flatMap { it.words }

        var allSentencesWithCache = emptyList<String>()

        allWords.forEach() { word ->
            val allSentences = allWords.flatMap { it.sentences.map { it.sentence } }

           allSentences.filter { sentence ->
                val uniqueSentenceId = generateUniqueSentenceId(word.word, sentence, voiceName)
                //Timber.e("Looking for $uniqueSentenceId")
                val audioCacheFile = File(cacheDir, "$uniqueSentenceId.mp3")
                audioCacheFile.exists()
            }

            allSentencesWithCache = allSentencesWithCache
        }



        // 3. Map the filtered list of VocabWord objects to just their keys and return as a Set.
        return allSentencesWithCache.toSet()
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
        val vocabDataResult = getVocabData(currentExamFile)

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
        val vocabDataResult = getVocabData(currentExamFile)

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
        val vocabDataResult = getVocabData(currentExamFile)
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
        val vocabDataResult = getVocabData(currentExamFile)

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
    private fun normalizeToLogicalName(name: String): String {
        return when (name) {
            "vocab_data_a1" -> "EnglishA1Vocab"
            "vocab_data_a2" -> "EnglishA2Vocab"
            "vocab_data_b1" -> "EnglishB1Vocab"
            "vocab_data_b2" -> "EnglishB2Vocab"
            // Add any other legacy mappings here

            // If the name is already in the correct format, just return it.
            else -> name
        }
    }
    /**
     * Maps the logical Firestore name to the Android-specific resource name.
     */
    private fun mapLogicalToResourceName(logicalName: String): String {
        return when (logicalName) {
            "EnglishA1Vocab" -> "vocab_data_a1"
            "EnglishA2Vocab" -> "vocab_data_a2"
            "EnglishB1Vocab" -> "vocab_data_b1"
            "EnglishB2Vocab" -> "vocab_data_b2"
            // Add other mappings here as needed
            else -> logicalName // Fallback for other files
        }
    }

}