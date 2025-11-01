package com.goodstadt.john.language.exams.data.examsheets

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.DummySheetDefinition
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

/*
Layer 3 (UI Logic)	ViewModels (GroupedVM, GenericVM, etc.)	To prepare UI state for a specific screen.	(No one below it)
Layer 2 (Orchestration & Business Logic)	VocabRepository	To be the single entry point for all VocabFile data. It orchestrates caching, versioning, and data source selection.	Only ViewModels.
Layer 1 (Data Source Implementation)	ExamSheetRepository	To be a low-level worker. Its only job is to manage the disk cache and network fetching for VocabFiles from Firestore.	Only VocabRepository.
 */

@Singleton
class ExamSheetRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context,
    private val jsonParser: Json
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir = context.filesDir

    // --- 1. PUBLIC, TYPE-SAFE API ---

    /**
     * Public function to get a VocabFile. This is the main entry point.
     * It handles the cache-first, network-next logic correctly.
     */
    suspend fun getVocabSheet(name: String, forceRefresh: Boolean): Result<VocabFile> = withContext(Dispatchers.IO) {
        try {
            // a. Check disk cache first (unless forcing a refresh)
            if (!forceRefresh) {
                Timber.d("ExamSheetRepo.getVocabSheet(): '$name' from cache, if exists ...")
                readVocabFileFromCache(name)?.let { cachedFile ->
                    Timber.d("ExamSheetRepo.getVocabSheet(): Returning '$name' from disk cache. Yippee!")
                    return@withContext Result.success(cachedFile)
                }
            }

            // b. If no cache or force refresh, call your existing network fetcher.
            //    This function already fetches, builds the object, and caches it.
            Timber.d("ExamSheetRepo: getVocabSheet '$name' NOT in cache, download")
            return@withContext fetchFromNetworkAndCache(name)

        } catch (e: Exception) {
            Timber.e(e, "ExamSheetRepo.getVocabSheet(): CRITICAL Error in getVocabSheet for '$name'.")
            return@withContext Result.failure(e)
        }
    }

    /**
     * A type-safe function for reading a `VocabFile` specifically from the disk cache.
     */
    private suspend fun readVocabFileFromCache(logicalName: String): VocabFile? = withContext(Dispatchers.IO) {
        val file = getCacheFile(logicalName)
        if (!file.exists()) return@withContext null

        return@withContext try {
            val jsonString = file.readText()
            jsonParser.decodeFromString<VocabFile>(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "ExamSheetRepo():Failed to read VocabFile from disk cache for '$logicalName'")
            null
        }
    }
    /**
     * MODIFIED: Renamed and generalized from `forceExamSheetRefresh`.
     * This is now the single function responsible for fetching a sheet from Firestore
     * and saving it to the disk cache.
     */
    private suspend fun fetchFromNetworkAndCache(examName: String): Result<VocabFile> {
        Timber.d("ExamSheetRepo.fetchFromNetworkAndCache(): '$examName' from network...")
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

            Timber.i("ExamSheetRepo.fetchFromNetworkAndCache(): Successfully fetched and cached '$examName'.")
            Result.success(vocabFile)
        } catch (e: Exception) {
            Timber.e(e, "ExxamSheetRepo.fetchFromNetworkAndCache(): ERROR - Failed to fetch or cache '$examName'.")
            Result.failure(e)
        }
    }
    private suspend fun downloadFromFirestoreCollections(examName: String): VocabFile = coroutineScope {
        Timber.i("ExamSheetRepo.downloadFromFirestoreCollections(): '$examName'.")
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

    private fun getCacheFile(examName: String): File {
        Timber.i("ExamSheetRepository.getCacheFile()")
        Log.i("MyTestTag", "Native Log: getCacheFile was executed.")
        val fileName = "${examName}_cache.json"
        return File(cacheDir, fileName)
    }


}