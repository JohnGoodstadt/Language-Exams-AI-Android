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
    @ApplicationContext private val context: Context
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir = context.filesDir

    /**
     * Main public function. Follows a "cache-first" strategy.
     * @return A [Result] containing the `VocabFile` on success.
     */
    suspend fun getExamSheetBy(examName: String, forceRefresh: Boolean = false): Result<VocabFile> {
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
                            group = firestoreWord.group, sentences = sentences
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
}