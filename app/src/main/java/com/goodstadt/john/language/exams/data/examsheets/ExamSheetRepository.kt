package com.goodstadt.john.language.exams.data.examsheets

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.DummySheetDefinition
import com.goodstadt.john.language.exams.models.HeaderWordAndSentence
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesList
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesListRoot
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesListRootDTO
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.TabHeaderForFirestore
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.models.WordAndSentenceForFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
    suspend fun getVocabSheet(name: String, forceRefresh: Boolean): Result<VocabFile> =
        withContext(Dispatchers.IO) {
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
                Timber.e(
                    e,
                    "ExamSheetRepo.getVocabSheet(): CRITICAL Error in getVocabSheet for '$name'."
                )
                return@withContext Result.failure(e)
            }
        }


    /**
     * Public function to get a `HeaderWordsSentencesListRoot` (Format1).
     * Guarantees a `Result<HeaderWordsSentencesListRoot>` return type.
     */

    suspend fun getFormat1Sheet(
        name: String,
        forceRefresh: Boolean
    ): Result<HeaderWordsSentencesListRoot> {
        try {
            // a. Check disk cache first (unless forcing a refresh)
            if (!forceRefresh) {
                readFormat1SheetFromCache(name)?.let { cachedFile ->
                    Timber.d("ExamSheetRepo: Returning '$name' (Format1) from disk cache.")
                    return Result.success(cachedFile)
                }
            }

            // b. If no cache or force refresh, call the dedicated network fetcher for this type.
            return fetchFormat1FromNetworkAndCache(name)

        } catch (e: Exception) {
            Timber.e(e, "ExamSheetRepo: CRITICAL Error in getFormat1Sheet for '$name'.")
            return Result.failure(e)
        }
    }


    private suspend fun  fetchFormat1FromNetworkAndCache(examName: String): Result<HeaderWordsSentencesListRoot> {
        Timber.d("ExamSheetRepo: Fetching '$examName' (Format1) from network...")
        return try {
            // This function calls the specific logic to download and assemble a Format1 object.
            val format1File = downloadAndAssembleFormat1(examName)

            val cacheFile = getCacheFile(examName)
            // Use the correct serializer for this type
            val jsonString = jsonParser.encodeToString(HeaderWordsSentencesListRoot.serializer(), format1File)
            cacheFile.writeText(jsonString)

            Timber.i("ExamSheetRepo: Successfully fetched and cached '$examName' (Format1).")
            Result.success(format1File)
        } catch (e: Exception) {
            Timber.e(e, "ExamSheetRepo: ERROR - Failed to fetch or cache '$examName' (Format1).")
            Result.failure(e)
        }
    }
//    private suspend fun downloadAndAssembleFormat1(examName: String): HeaderWordsSentencesListRoot? {
//        // Your existing, proven logic for fetching this data type goes here.
//        // For example, if it's stored as a single document:
//        return null
//    }

    /**
     * A type-safe function for reading a `HeaderWordsSentencesListRoot` from the disk cache.
     */
    private suspend fun readFormat1SheetFromCache(logicalName: String): HeaderWordsSentencesListRoot? =
        withContext(Dispatchers.IO) {
            val file = getCacheFile(logicalName)
            if (!file.exists()) return@withContext null

            return@withContext try {
                val jsonString = file.readText()
                // Use the correct decoder for this type
                jsonParser.decodeFromString<HeaderWordsSentencesListRoot>(jsonString)
            } catch (e: Exception) {
                Timber.e(e, "Failed to read Format1 from disk cache for '$logicalName'")
                null
            }
        }

    /**
     * Fetches from Firestore, converts the native document to a JSON string,
     * saves that string to the cache, and returns the string.
     */
//    private suspend fun fetchAndCacheJsonStringWrong(examName: String): Result<String> {
//        Timber.d("ExamSheetRepo: Fetching '$examName' from network...")
//        return try {
//            val document = firestore.collection("vocab_sheets").document(examName).get().await()
//            if (!document.exists()) throw Exception("Document '$examName' not found")
//
//            val dataMap = document.data ?: throw Exception("Document '$examName' has no data")
//
//            // Convert the Firestore Map<String, Any> to a standard JSON String
//            val jsonString = jsonParser.encodeToString(dataMap)
//
//            // Save the raw string to the disk cache
//            val cacheFile = getCacheFile(examName)
//            cacheFile.writeText(jsonString)
//            Timber.i("ExamSheetRepo: Successfully fetched and cached JSON for '$examName'.")
//
//            Result.success(jsonString)
//        } catch (e: Exception) {
//            Timber.e(e, "ExamSheetRepo: ERROR - Failed to fetch or cache '$examName'.")
//            Result.failure(e)
//        }
//    }

    /**
     * ✅ REFACTORED: This is the new core function. Its ONLY job is to get a
     * valid JSON string, either from the cache or by fetching it from the network.
     * It does NO decoding itself.
     */
//    private suspend fun getSheetJson(name: String, forceRefresh: Boolean): Result<String> =
//        withContext(Dispatchers.IO) {
//            try {
//                // a. Check disk cache for the raw JSON string first.
//                if (!forceRefresh) {
//                    readJsonStringFromCache(name)?.let { cachedJson ->
//                        Timber.d("ExamSheetRepo: Returning JSON for '$name' from disk cache.")
//                        return@withContext Result.success(cachedJson)
//                    }
//                }
//
//                // b. If no cache or force refresh, call the network fetcher.
//                return fetchAndCacheJsonString(name)
//
//            } catch (e: Exception) {
//                Timber.e(e, "ExamSheetRepo: Error getting JSON for '$name'.")
//                return@withContext Result.failure(e)
//            }
//        }

    /**
     * A private, generic function that contains the core cache-first/network-next logic.
     * It works for any `Serializable` type `T`.
     */
//    private suspend fun <T> getSheetImpl(
//        name: String,
//        forceRefresh: Boolean,
//        fetcher: suspend (String) -> T, // A function that fetches the object `T` from network
//        decoder: (String) -> T         // A function that decodes a JSON string into `T`
//    ): Result<T> = withContext(Dispatchers.IO) {
//        try {
//            // a. Check disk cache first (unless forcing a refresh)
//            if (!forceRefresh) {
//                val cachedJson = getCacheFile(name)
////                if (cachedJson != null) {
//                    Timber.d("ExamSheetRepo: Decoding '$name' from disk cache.")
//                return@withContext Result.success(decoder(cachedJson))
////                }
//            }
//
//            // b. If no cache or force refresh, call the provided network fetcher.
//            Timber.d("ExamSheetRepo: Fetching '$name' from network via specific fetcher.")
//            val freshObject = fetcher(name) // This call is now generic
//
//            // c. Save the newly fetched object to the cache.
//            saveObjectToCache(name, freshObject)
//
//            return@with-Context Result.success(freshObject)
//
//        } catch (e: Exception) {
//            Timber.e(e, "ExamSheetRepo: Error in getSheetImpl for '$name'.")
//            return@withContext Result.failure(e)
//        }
//    }
    /**
     * A type-safe function for reading a `VocabFile` specifically from the disk cache.
     */
    private suspend fun readVocabFileFromCache(logicalName: String): VocabFile? =
        withContext(Dispatchers.IO) {

            val file = getCacheFile(logicalName)
            if (!file.exists()) return@withContext null

            return@withContext try {
                val jsonString = file.readText()
                jsonParser.decodeFromString<VocabFile>(jsonString)
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "ExamSheetRepo():Failed to read VocabFile from disk cache for '$logicalName'"
                )
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
            Timber.e(
                e,
                "ExxamSheetRepo.fetchFromNetworkAndCache(): ERROR - Failed to fetch or cache '$examName'."
            )
            Result.failure(e)
        }
    }

    private suspend fun downloadFromFirestoreCollections(examName: String): VocabFile =
        coroutineScope {
            Timber.i("ExamSheetRepo.downloadFromFirestoreCollections(): '$examName'.")
            val examDocRef = firestore.collection("global").document("exam_sheets")
                .collection("sheets").document(examName)

            // 1. Fetch metadata and categories concurrently
            val vocabFileDtoDeferred = async {
                examDocRef.get().await().toObject<VocabFileDTO>()
                    ?: throw DataFetchError.DocumentNotFoundError
            }
            val categoriesSnapshotDeferred =
                async { examDocRef.collection("categories").get().await() }

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
                                Sentence(
                                    sentence = sentenceText,
                                    translation = ""
                                ) // Assuming empty translation
                            }

                            VocabWord(
                                id = firestoreWord.id,
                                sortOrder = firestoreWord.sortOrder,
                                translation = firestoreWord.translation,
                                romanisation = firestoreWord.romanisation,
                                partOfSpeech = firestoreWord.partOfSpeech,
                                word = firestoreWord.word,
                                group = firestoreWord.group,
                                sentences = sentences,
                                definition = firestoreWord.definition//TODO: need to add this
                            )
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "Codable ERROR: Failed to decode VocabWord in category '${firestoreCategory.title}'."
                            )
                            null
                        }
                    }

                    Category(
                        title = firestoreCategory.title,
                        tabNumber = firestoreCategory.tabNumber,
                        sortOrder = firestoreCategory.sortOrder,
                        words = words.sortedBy { it.sortOrder }
                    )
                }
            }.awaitAll().sortedBy { it.sortOrder }

            // 3. Assemble the final domain model
            return@coroutineScope VocabFile(
                fileformat = vocabFileDto.fileformat,
                location = vocabFileDto.location,
                sheetName = vocabFileDto.sheetName,
                updatedDate = vocabFileDto.updatedDate,
                uploadDate = vocabFileDto.uploadDate,
                id = vocabFileDto.id,
                native = vocabFileDto.native,
                name = vocabFileDto.name,
                romanized = vocabFileDto.romanized,
                nativeName = vocabFileDto.nativeName,
                googleVoicePrefix = vocabFileDto.googleVoicePrefix,
                voiceName = vocabFileDto.voiceName,
                tabtitles = vocabFileDto.tabtitles,
                categories = categories
            )
        }

    // --- 4. PRIVATE, GENERIC CACHING HELPERS ---

    private suspend fun downloadAndAssembleFormat1(examName: String): HeaderWordsSentencesListRoot = coroutineScope {
        Timber.d("ExamSheetRepo: Assembling Format1 sheet for '$examName' from sub-collections.")

        // Define the base path to the specific exam sheet document
        val examDocRef = firestore.collection("global").document("exam_sheets")
            .collection("sheets").document(examName)

        //1 of 3
//        val rootDtoDeferred = async(Dispatchers.IO) {
//            examDocRef.get().await().toObject(HeaderWordsSentencesListRootDTO::class.java)
//                ?: throw Exception("Root document '$examName' (Format1) not found or failed to parse.")
//        }

        // 1. LAUNCH CONCURRENT FETCHES for headers and body content

        //2 of 3
        // 'async' starts a coroutine and returns a 'Deferred' which is a promise of a future result.
        val headersDeferred = async(Dispatchers.IO) {
            val snapshot = examDocRef.collection("tabs").orderBy("sortorder").get().await()
            // Use .toObjects() for clean conversion from documents to a list of data classes
            snapshot.toObjects(TabHeaderForFirestore::class.java)
        }

        //3 of 3
        val bodyDeferred = async(Dispatchers.IO) {
            val snapshot = examDocRef.collection("wordsAndSentences").get().await()
            snapshot.toObjects(WordAndSentenceForFirestore::class.java)
        }

        // 2. AWAIT THE RESULTS of both concurrent fetches
        // 'await()' suspends the function until the deferred value is ready.
       // val rootDto = rootDtoDeferred.await()
        val headers = headersDeferred.await()
        val body = bodyDeferred.await()

        Timber.d("ExamSheetRepo: Fetched ${headers.size} headers and ${body.size} body rows for '$examName'.")

        // 3. TRANSFORM (JOIN) the data in memory - this is your 'transformFirestoreToAppFormat1' logic
        val transformedData = headers.map { header ->
            // For each header, filter the body to find its children
            val wordsForThisHeader = body
                .filter { it.parentID == header.tabID }
                .map { row ->
                    // Map the Firestore DTO to your final, clean app model
                    HeaderWordAndSentence(
                        word = row.word,
                        sentence = row.sentence,
                        translation = row.translation,
                        definition = row.definition
                    )
                }

            // Create the final section object
            HeaderWordsSentencesList(
                title = header.title,
                description = header.description,
                sortOrder = header.sortorder,
                wordsAndSentences = wordsForThisHeader
            )
        }

        return@coroutineScope HeaderWordsSentencesListRoot(
            fileformat = 1,
            sheetName = examName,
            location = "remote",//TODO: what to go in here?
            data = transformedData // Use the fully assembled list here
        )

    }


    private fun getCacheFile(examName: String): File {
        Timber.i("ExamSheetRepository.getCacheFile()")
        //Log.i("MyTestTag", "Native Log: getCacheFile was executed.")
        val fileName = "${examName}_cache.json"
        return File(cacheDir, fileName)
    }

//    private fun getCacheFile(logicalName: String): File {
//        Timber.i("ExamSheetRepository.getCacheFile()")
//        Log.i("MyTestTag", "Native Log: getCacheFile was executed.")
//        val fileName = "${logicalName}_cache.json"
//        val cacheDir = File(context.filesDir, fileName)
//        if (!cacheDir.exists()) {
//            cacheDir.mkdirs()
//        }
//        return File(cacheDir, "$logicalName.json")
//    }

    /**
     * ✅ ADD THIS FUNCTION
     * Reads the raw JSON string from a cached file on disk.
     *
     * @param logicalName The unique identifier for the sheet (e.g., "EnglishA1Vocab").
     * @return The JSON string if the file is found and readable, otherwise `null`.
     */
    private suspend fun readJsonStringFromCache(logicalName: String): String? =
        withContext(Dispatchers.IO) {
            // 1. Get the File object for the cache file.
            val file = getCacheFile(logicalName)

            // 2. If the file doesn't exist, it's a cache miss. Return null immediately.
            if (!file.exists()) {
                return@withContext null
            }

            // 3. Try to read the file's content.
            return@withContext try {
                // This is the core operation. It reads the entire file into a String.
                file.readText()
            } catch (e: Exception) {
                // 4. If there's any error reading the file (e.g., it's corrupt),
                //    log the error and return null to treat it as a cache miss.
                Timber.e(e, "Failed to read disk cache for '$logicalName'")
                null
            }
        }

}