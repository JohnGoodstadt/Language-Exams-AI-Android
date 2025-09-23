package com.goodstadt.john.language.exams.data

import android.content.Context
import android.util.Log
import com.goodstadt.john.language.exams.data.api.GoogleCloudTTS
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.TabDetails
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
        // --- NEW: Inject the new services ---
    private val googleCloudTts: GoogleCloudTTS,
    private val audioPlayerService: AudioPlayerService,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    // Cache the result in memory after the first successful load
    private val vocabCache = mutableMapOf<String, VocabFile>()


    // A lazy json parser instance with lenient configuration
    private val jsonParser = Json {
        ignoreUnknownKeys = true    // Be robust against future changes in the JSON
        coerceInputValues = true    // Use default values if a field is null
        //allowTrailingCommas = true  // Allow trailing commas, common in hand-edited JSON
    }

    /**
     * Loads the vocabulary data from the specified JSON file.
     * This version is dynamic and includes a more robust cache.
     * @param fileName The name of the resource file to load (without the .json extension).
     */
    suspend fun getVocabData(fileName: String): Result<VocabFile> = withContext(Dispatchers.IO) {
        // Return from cache if available for this specific file
        vocabCache[fileName]?.let {
            Timber.v("Returning '$fileName' from cache.") // For debugging
            return@withContext Result.success(it)
        }

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
            vocabCache[fileName] = vocabFile
            Result.success(vocabFile)
        } catch (e: Exception) {
            Timber.e(e.localizedMessage)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    fun getWordKeysWithCachedAudioObsolete(categories: List<Category>, voiceName: String): Set<String> {
        val cacheDir = context.filesDir

        // 1. Flatten all words from all categories into a single list.
        val allWords = categories.flatMap { it.words }

        // 2. Filter this list to keep only the words that have a cached file.
        val wordsWithCache = allWords.filter { word ->
            // Use 'any' to check if at least ONE sentence's audio exists.
            // This is efficient because it stops checking as soon as it finds one.
            word.sentences.any { sentence ->
                val uniqueSentenceId = generateUniqueSentenceId(word, sentence, voiceName)
                val audioCacheFile = File(cacheDir, "$uniqueSentenceId.mp3")
                audioCacheFile.exists()
            }
        }

        // 3. Map the filtered list of VocabWord objects to just their keys and return as a Set.
        return wordsWithCache.map { it.word }.toSet()
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
                Timber.e("Looking for $uniqueSentenceId")
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
                Timber.e("Looking for $uniqueSentenceId")
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
}