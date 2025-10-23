package com.goodstadt.john.language.exams.data.examsheets

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.models.VocabWord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamSheetRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context,
    private val jsonParser: Json
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir = context.filesDir

    /**
     * Main public function. Follows a "cache-first" strategy.
     * @return A [Result] containing the `VocabFile` on success.
     */
    suspend fun getExamSheetByOriginal(examName: String, forceRefresh: Boolean = false): Result<VocabFile> {
        return withContext(Dispatchers.IO) { // Move the entire operation to a background thread
            val cacheFile = getCacheFile(examName)

            if (!forceRefresh && cacheFile.exists()) {
                Timber.d("Repo: Cache found for '$examName'. Loading from local disk.")
                try {
                    return@withContext Result.success(loadFromCache(cacheFile))
                } catch (e: Exception) {
                    Timber.w(e, "Repo: WARN - Failed to load from cache. Attempting to re-download.")
                    // Fall through to download if cache is corrupt
                }
            }

            // If no cache or forced refresh, download and update the cache.
            forceExamSheetRefresh(examName)
        }
    }
    suspend fun getExamSheetBy(name: String, forceRefresh: Boolean = false): Result<VocabFile> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            readFromDiskCache(name)?.let { cachedFile ->
                Timber.d("ExamSheetRepo: Returning '$name' from disk cache.")
                return@withContext Result.success(cachedFile)
            }
        }

        // 2. If no cache or force refresh, call our unified network fetcher.
        //    The logic for fetching, parsing, and caching is now all in one place.
        return@withContext fetchFromNetworkAndCache(name)
    }
    /**
     * MODIFIED: Renamed and generalized from `forceExamSheetRefresh`.
     * This is now the single function responsible for fetching a sheet from Firestore
     * and saving it to the disk cache.
     */
    private suspend fun fetchFromNetworkAndCache(examName: String): Result<VocabFile> {
        Timber.d("ExamSheetRepo: Fetching '$examName' from network...")
        return try {
            // This is your existing function that talks to Firestore.
            // Let's assume it returns a VocabFile on success.
            val vocabFile = downloadFromFirestoreCollections(examName)

            // Get the cache file location.
            val cacheFile = getCacheFile(examName)

            // Save the newly fetched data to the disk cache.
            // This replaces the old `saveToDiskCache` function's logic.
            val jsonString = jsonParser.encodeToString(VocabFile.serializer(), vocabFile)
            cacheFile.writeText(jsonString)

            Timber.i("ExamSheetRepo: Successfully fetched and cached '$examName'.")
            Result.success(vocabFile)
        } catch (e: Exception) {
            Timber.e(e, "ExamSheetRepo: ERROR - Failed to fetch or cache '$examName'.")
            Result.failure(e)
        }
    }

    /**
     * Forcibly re-downloads data from Firestore and overwrites the local cache.
     * @return A [Result] containing the freshly downloaded `VocabFile` on success.
     */
    suspend fun forceExamSheetRefresh(examName: String): Result<VocabFile> {
        return withContext(Dispatchers.IO) {
            Timber.d("Repo: Forcing refresh for '$examName' from Firestore...")
            try {
                val vocabFile = downloadFromFirestoreCollections(examName)
                val cacheFile = getCacheFile(examName)

                // Save to cache
                val jsonString = json.encodeToString(vocabFile)
                cacheFile.writeText(jsonString)

                Timber.d("Repo: Cache for '$examName' has been updated.")
                Result.success(vocabFile)
            } catch (e: Exception) {
                Timber.e(e, "Repo: ERROR - Failed to force refresh for '$examName'.")
                Result.failure(e)
            }
        }
    }

    private suspend fun downloadFromFirestoreCollections(examName: String): VocabFile = coroutineScope {
        val examDocRef = firestore.collection("global").document("exam_sheets")
            .collection("sheets").document(examName)

        // 1. Fetch metadata and categories concurrently
        val vocabFileDtoDeferred = async {
            examDocRef.get().await().toObject<VocabFileDTO>()
                ?: throw DataFetchError.DocumentNotFoundError
        }
        val categoriesSnapshotDeferred = async { examDocRef.collection("categories").get().await() }

        val vocabFileDto = vocabFileDtoDeferred.await()

        // 2. Concurrently fetch all words for all categories
        val categories = categoriesSnapshotDeferred.await().documents.map { categoryDoc ->
            async { // Start a new concurrent task for each category
                val firestoreCategory = categoryDoc.toObject<FirestoreCategoryDTO>()!!
                val wordsSnapshot = categoryDoc.reference.collection("words").get().await()

                val words = wordsSnapshot.documents.mapNotNull { wordDoc ->
                    try {
                        val firestoreWord = wordDoc.toObject<FirestoreWordDTO>()!!
                        val sentences = firestoreWord.sentences.map { sentenceText ->
                            Sentence(sentence = sentenceText, translation = "") // Assuming empty translation
                        }

                        VocabWord(
                            id = firestoreWord.id, sortOrder = firestoreWord.sortOrder,
                            translation = firestoreWord.translation, romanisation = firestoreWord.romanisation,
                            partOfSpeech = firestoreWord.partOfSpeech, word = firestoreWord.word,
                            group = firestoreWord.group, sentences = sentences,
                            definition = firestoreWord.definition//TODO: need to add this
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Codable ERROR: Failed to decode VocabWord in category '${firestoreCategory.title}'.")
                        null
                    }
                }

                Category(
                    title = firestoreCategory.title, tabNumber = firestoreCategory.tabNumber,
                    sortOrder = firestoreCategory.sortOrder, words = words.sortedBy { it.sortOrder }
                )
            }
        }.awaitAll().sortedBy { it.sortOrder }

        // 3. Assemble the final domain model
        return@coroutineScope VocabFile(
            fileformat = vocabFileDto.fileformat, location = vocabFileDto.location,
            sheetName = vocabFileDto.sheetName, updatedDate = vocabFileDto.updatedDate,
            uploadDate = vocabFileDto.uploadDate, id = vocabFileDto.id, native = vocabFileDto.native,
            name = vocabFileDto.name, romanized = vocabFileDto.romanized,
            nativeName = vocabFileDto.nativeName, googleVoicePrefix = vocabFileDto.googleVoicePrefix,
            voiceName = vocabFileDto.voiceName, tabtitles = vocabFileDto.tabtitles,
            categories = categories
        )
    }

    private fun loadFromCache(file: File): VocabFile {
        Timber.i("loadFromCache")
        val jsonString = file.readText()
        return json.decodeFromString(jsonString)
    }

    private fun getCacheFile(for_examName: String): File {
        Timber.i("getting getCacheFile")
        val fileName = "${for_examName}_cache.json"
        return File(cacheDir, fileName)
    }
    // --- DISK CACHE HELPERS ---

    private fun getCacheFileNew(logicalName: String): File {
        val cacheDir = File(context.filesDir, "vocab_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return File(cacheDir, "$logicalName.json")
    }

    private suspend fun readFromDiskCache(logicalName: String): VocabFile? = withContext(Dispatchers.IO) {
        val file = getCacheFile(logicalName)
        if (!file.exists()) return@withContext null

        try {
            val jsonString = file.readText()
            jsonParser.decodeFromString<VocabFile>(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "Failed to read disk cache for $logicalName")
            null // If cache is corrupt, treat it as a miss
        }
    }

    private suspend fun saveToDiskCache(logicalName: String, vocabFile: VocabFile) = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(logicalName)

            // ✅ THE FIX: Call encodeToString with the object's serializer.
            val jsonString = jsonParser.encodeToString(VocabFile.serializer(), vocabFile)

            file.writeText(jsonString)
            Timber.d("Saved '$logicalName' to disk cache.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save to disk cache for $logicalName")
        }
    }
}