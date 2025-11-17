package com.goodstadt.john.language.exams.data.examsheets

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.HeaderWordAndSentence
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesList
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesListRoot
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.TabHeaderForFirestore
import com.goodstadt.john.language.exams.models.Format0File
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.models.Format2Entry
import com.goodstadt.john.language.exams.models.Format2File
import com.goodstadt.john.language.exams.models.Format2Level
import com.goodstadt.john.language.exams.models.Format2Sentence
import com.goodstadt.john.language.exams.models.Format2WordAndSentenceDTO
import com.goodstadt.john.language.exams.models.SheetHeaderFormat2DTO
import com.goodstadt.john.language.exams.models.WordAndSentenceForFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
    suspend fun getFormat0Sheet(sheet_name: String, forceRefresh: Boolean): Result<Format0File> =
        withContext(Dispatchers.IO) {
            try {
                // a. Check disk cache first (unless forcing a refresh)
                if (!forceRefresh) {
                    Timber.d("ExamSheetRepo.getVocabSheet(): '$sheet_name' from cache, if exists ...")
                    readFormat0FileFromCache(sheet_name)?.let { cachedFile ->
                        Timber.d("ExamSheetRepo.getVocabSheet(): Returning '$sheet_name' from disk cache. Yippee!")
                        return@withContext Result.success(cachedFile)
                    }
                }

                // b. If no cache or force refresh, call your existing network fetcher.
                //    This function already fetches, builds the object, and caches it.
                Timber.d("ExamSheetRepo: getVocabSheet '$sheet_name' NOT in cache, download")
                return@withContext fetchFromNetworkAndCacheFormat0File(sheet_name)

            } catch (e: Exception) {
                Timber.e(
                    e,
                    "ExamSheetRepo.getVocabSheet(): CRITICAL Error in getVocabSheet for '$sheet_name'."
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

            val cacheFile = getCacheFilePointer(examName)
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

    /**
     * A type-safe function for reading a `HeaderWordsSentencesListRoot` from the disk cache.
     */
    private suspend fun readFormat1SheetFromCache(logicalName: String): HeaderWordsSentencesListRoot? =
        withContext(Dispatchers.IO) {
            val file = getCacheFilePointer(logicalName)
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
     * A type-safe function for reading a `VocabFile` specifically from the disk cache.
     */
    private suspend fun readFormat0FileFromCache(logicalName: String): Format0File? =
        withContext(Dispatchers.IO) {

            val file = getCacheFilePointer(logicalName)
            if (!file.exists()) return@withContext null

            return@withContext try {
                val jsonString = file.readText()
                jsonParser.decodeFromString<Format0File>(jsonString)
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
    private suspend fun fetchFromNetworkAndCacheFormat0File(sheet_name: String): Result<Format0File> {
        Timber.d("ExamSheetRepo.fetchFromNetworkAndCache(): '$sheet_name' from network...")
        return try {
            // This is your existing function that talks to Firestore.
            // Let's assume it returns a VocabFile on success.
            val vocabFile = downloadFromFirestoreCollections(sheet_name)

            // Get the cache file location.d
            val cacheFile = getCacheFilePointer(sheet_name)

            // Save the newly fetched data to the disk cache.
            // This replaces the old `saveToDiskCache` function's logic.
            val jsonString = jsonParser.encodeToString(Format0File.serializer(), vocabFile)
            cacheFile.writeText(jsonString)

            Timber.i("ExamSheetRepo.fetchFromNetworkAndCache(): Successfully fetched and cached '$sheet_name'.")
            Result.success(vocabFile)
        } catch (e: Exception) {
            Timber.e(
                e,
                "ExxamSheetRepo.fetchFromNetworkAndCache(): ERROR - Failed to fetch or cache '$sheet_name'."
            )
            Result.failure(e)
        }
    }

    private suspend fun downloadFromFirestoreCollections(examName: String): Format0File =
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

                            Format0Word(
                                id = firestoreWord.id,
                                sortOrder = firestoreWord.sortOrder,
                                translation = firestoreWord.translation,
                                romanisation = firestoreWord.romanisation,
                                partOfSpeech = firestoreWord.partOfSpeech,
                                word = firestoreWord.word,
                                group = firestoreWord.group,
                                sentences = sentences,
                                definition = firestoreWord.definition,
                                IPA = firestoreWord.IPA,
                                pronounce = firestoreWord.pronounce
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
            return@coroutineScope Format0File(
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


    private fun getCacheFilePointer(examName: String): File {
        Timber.i("ExamSheetRepository.getCacheFile()")
        //Log.i("MyTestTag", "Native Log: getCacheFile was executed.")
        val fileName = "${examName}_cache.json"
        return File(cacheDir, fileName)
    }

    suspend fun getFormat2Sheet(sheet_name: String, forceRefresh: Boolean): Result<Format2File> {
        return try {
            // a. Check disk cache first (unless forcing a refresh)
            if (!forceRefresh) {
                readFormat2SheetFromCache(sheet_name)?.let { cachedFile ->
                    Timber.d("ExamSheetRepo: Returning '$sheet_name' (Format2) from disk cache. Yippee")
                    return Result.success(cachedFile)
                }
            }

            val format2File = downloadAndAssembleFormat2(sheet_name)
            val cacheFile = getCacheFilePointer(sheet_name)
            val jsonString = jsonParser.encodeToString(Format2File.serializer(), format2File)
            cacheFile.writeText(jsonString)

            Timber.i("ExamSheetRepo: Successfully fetched and cached '$sheet_name' (Format2).")

            // 3. ✅ Wrap the successful result in Result.success()
            Result.success(format2File)


        } catch (e: Exception) {
            Timber.e(e, "ExamSheetRepo: CRITICAL Error in getFormat2Sheet for '$sheet_name'.")
            return Result.failure(e)
        }
    }
    private suspend fun readFormat2SheetFromCache(logicalName: String): Format2File? = withContext(Dispatchers.IO) {
        val file = getCacheFilePointer(logicalName)
        if (!file.exists()) return@withContext null

        return@withContext try {
            val jsonString = file.readText()
            jsonParser.decodeFromString<Format2File>(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "Failed to read Format2 from disk cache for '$logicalName'")
            null
        }
    }
// ... inside your ExamSheetRepository class ...

    /**
     * The specific function that knows how to download and assemble a Format2File object
     * from your native Firestore documents. This is the direct Kotlin translation of your final Swift version.
     */
    private suspend fun downloadAndAssembleFormat2(examName: String): Format2File = coroutineScope {
        Timber.d("ExamSheetRepo: Assembling Format2 sheet for '$examName' from sub-collections.")

        val examDocRef = firestore.collection("global").document("exam_sheets")
            .collection("sheets").document(examName)

        // 1. --- LAUNCH CONCURRENT FETCHES for the root metadata and the flattened word list ---
        val rootDtoDeferred = async(Dispatchers.IO) {
            examDocRef.get().await().toObject(SheetHeaderFormat2DTO::class.java)
                ?: throw Exception("Root document '$examName' (Format2) not found or failed to parse.")
        }
        val allEntriesDeferred = async(Dispatchers.IO) {
            val snapshot = examDocRef.collection("wordsAndSentences").get().await()
            snapshot.toObjects(Format2WordAndSentenceDTO::class.java)
        }

        // 2. --- AWAIT ALL RESULTS ---
        val rootDto = rootDtoDeferred.await()
        val allEntries = allEntriesDeferred.await()

        Timber.d("ExamSheetRepo: Fetched metadata and ${allEntries.size} total entries for '$examName'.")

        // 3. --- RE-ASSEMBLE THE HIERARCHY ---
        // Group the flat list of DTOs by their 'sortorder'. This is the key to creating the 4 sections.
        val groupedBySortOrder = allEntries.groupBy { it.sortorder }

        // 4. --- TRANSFORM the grouped data into your final domain models ---
        val finalLevels = groupedBySortOrder.entries
            .mapNotNull { (sortOrder, dtosInGroup) ->
                // Get metadata from the first DTO in the group
                val firstDto = dtosInGroup.firstOrNull() ?: return@mapNotNull null

                // Map the DTOs in this specific group to the `Format2Entry` domain model
                val entries = dtosInGroup.map { dto ->
                    val sentences = dto.sentences.map { Format2Sentence(sentence = it) }
                    Format2Entry(word = dto.word, definition = dto.definition, sentences = sentences)
                }

                // The 'title' now comes from the DTO, not the dictionary key
                Format2Level(
                    description = firstDto.description,
                    sortorder = sortOrder, // The key of our groupBy map is the sort order
                    wordsAndSentences = entries
                )
            }
            .sortedBy { it.sortorder } // Sort the final levels by their sort order

        // 5. --- ASSEMBLE the final `Format2File` domain model ---
        return@coroutineScope Format2File(
            fileformat = rootDto.fileformat,
            location = rootDto.location,
            sheetname = rootDto.sheetname,
            title = rootDto.title,
            description = rootDto.description,
            updatedDate = (rootDto.updatedDate?.time ?: 0L).toInt(),
            data = finalLevels
        )
    }

}