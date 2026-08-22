package com.goodstadt.john.language.exams.data.examsheets

import android.content.Context
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.faultDownloadSheet
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Format0File
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.models.Format2Entry
import com.goodstadt.john.language.exams.models.Format2File
import com.goodstadt.john.language.exams.models.Format2Level
import com.goodstadt.john.language.exams.models.Format2Sentence
import com.goodstadt.john.language.exams.models.Format2WordAndSentenceDTO
import com.goodstadt.john.language.exams.models.Format3File
import com.goodstadt.john.language.exams.models.Format1Entry
import com.goodstadt.john.language.exams.models.Format1Level
import com.goodstadt.john.language.exams.models.Format1File
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.SheetHeaderFormat2DTO
import com.goodstadt.john.language.exams.models.Format7or10File
import com.goodstadt.john.language.exams.models.Format7or10List
import com.goodstadt.john.language.exams.models.Format7or10Section
import com.goodstadt.john.language.exams.models.Format7or10SectionDTO
import com.goodstadt.john.language.exams.models.Format7or10Word
import com.goodstadt.john.language.exams.models.WordQuizRoot
import com.goodstadt.john.language.exams.models.WordQuizList
import com.goodstadt.john.language.exams.models.WordQuizSections
import com.goodstadt.john.language.exams.models.WordQuizSWordsState
import com.goodstadt.john.language.exams.models.Format13SectionDTO
import com.goodstadt.john.language.exams.packages.dailydictionary.DictionaryEntry
import com.goodstadt.john.language.exams.models.TabHeaderForFirestore
import com.goodstadt.john.language.exams.models.WordAndSentenceForFirestore
import com.goodstadt.john.language.exams.utils.logging.TimberFault
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
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

/**
 * **ExamSheetRepository**
 *
 * A Singleton repository responsible for the retrieval, deserialization, and caching of static educational content (JSON sheets).
 * It acts as the direct interface between the application and the Firestore "Content" collections.
 *
 * **Key Responsibilities:**
 * - **Multi-Level Caching:** Implements a robust caching strategy to minimize network usage and ensure offline access:
 *     1. **L1 (Memory):** Returns hot objects instantly if already loaded in the current session.
 *     2. **L2 (Disk):** Checks internal storage for previously downloaded JSON files.
 *     3. **L3 (Network):** Fetches fresh data from **Firestore** if local data is missing or stale.
 * - **Version Control:** Compares local file versions against **Remote Config** or Firestore metadata to determine if a "Force Refresh" is required to download content updates.
 * - **Data Parsing:** Handles the deserialization of raw JSON into typed data models (`VocabFile`, `Format1File`, `Format2File`, `Format7or10File`).
 *
 * **Inputs:**
 * - Sheet Identifiers (e.g., "EnglishB1Vocab", "EnglishPrepositions").
 * - `forceRefresh` flags triggered by version mismatches.
 *
 * **Outputs:**
 * - Strongly-typed data objects representing the structure of a specific screen (Vocab List, Reference Table, or Quiz).
 * - Throws exceptions for network failures to be handled by the UI or upper layers.
 *
 * **Persistence Strategy:**
 * - **Cloud:** **Firestore** acts as the master source of truth for all text content.
 * - **Local:** Downloaded JSON files are saved to the device's internal storage directory, allowing the app to function fully offline after the initial sync.
 */

/*
Layer 3 (UI Logic)	ViewModels (GroupedVM, GenericVM, etc.)	To prepare UI state for a specific screen.	(No one below it)
Layer 2 (Orchestration & Business Logic)	VocabRepository	To be the single entry point for all VocabFile data. It orchestrates caching, versioning, and data source selection.	Only ViewModels.
Layer 1 (Data Source Implementation)	ExamSheetRepository	To be a low-level worker. Its only job is to manage the disk cache and network fetching for VocabFiles from Firestore.	Only VocabRepository.
 */

@Singleton
class ExamSheetRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val ttsStatsRepository:TTSStatsRepository,
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
                TimberFault.f(
                    message = "ExamSheetRepo: ERROR - Failed to fetch or cache '$sheet_name' (Format0).",
                    localizedMessage = e.localizedMessage ?: "null localizedMessage",
                    secondaryText = "android",
                    area = "ExamSheetRepository.getFormat0Sheet()"
                )
                ttsStatsRepository.incGlobalFaultCount(faultDownloadSheet)
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
    ): Result<Format1File> {
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


    private suspend fun  fetchFormat1FromNetworkAndCache(examName: String): Result<Format1File> {
        Timber.d("ExamSheetRepo: Fetching '$examName' (Format1) from network...")
        return try {
            // This function calls the specific logic to download and assemble a Format1 object.
            val format1File = downloadAndAssembleFormat1(examName)

            // Guard: an empty assembly means the sub-collections aren't there yet (e.g. sheet doc
            // exists but tabs/wordsAndSentences weren't written, or a join produced nothing). Do NOT
            // cache an empty result - that would poison the cache. Fail so the caller falls back to
            // the bundle and re-attempts the network next time.
            if (format1File.data.isEmpty()) {
                Timber.w("ExamSheetRepo: Format1 '$examName' assembled EMPTY - not caching, returning failure for fallback.")
                return Result.failure(Exception("Format1 sheet '$examName' assembled with no data (empty sub-collections)."))
            }

            val cacheFile = getCacheFilePointer(examName)
            // Use the correct serializer for this type
            val jsonString = jsonParser.encodeToString(Format1File.serializer(), format1File)
            cacheFile.writeText(jsonString)

            Timber.i("ExamSheetRepo: Successfully fetched and cached '$examName' (Format1).")
            Result.success(format1File)
        } catch (e: Exception) {
            Timber.e(e, "ExamSheetRepo: ERROR - Failed to fetch or cache '$examName' (Format1).")
            TimberFault.f(
                message = "ExamSheetRepo: ERROR - Failed to fetch or cache '$examName' (Format1).",
                localizedMessage = e.localizedMessage ?: "null localizedMessage",
                secondaryText = "android",
                area = "ExamSheetRepository.fetchFormat1FromNetworkAndCache()"
            )
            ttsStatsRepository.incGlobalFaultCount(faultDownloadSheet)
            Result.failure(e)
        }
    }

    /**
     * A type-safe function for reading a `HeaderWordsSentencesListRoot` from the disk cache.
     */
    private suspend fun readFormat1SheetFromCache(logicalName: String): Format1File? =
        withContext(Dispatchers.IO) {
            val file = getCacheFilePointer(logicalName)
            if (!file.exists()) return@withContext null

            return@withContext try {
                val jsonString = file.readText()
                // Use the correct decoder for this type
                val decoded = jsonParser.decodeFromString<Format1File>(jsonString)
                // Ignore an empty cached file (a stale artefact from before the sheet was populated):
                // returning null forces a fresh network fetch instead of serving empty data forever.
                if (decoded.data.isEmpty()) {
                    Timber.w("ExamSheetRepo: cached Format1 '$logicalName' is EMPTY - ignoring cache, will re-fetch.")
                    null
                } else {
                    decoded
                }
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
                "ExamSheetRepo.fetchFromNetworkAndCache(): ERROR - Failed to fetch or cache '$sheet_name'."
            )
            TimberFault.f(
                message = "ExamSheetRepo ERROR - Failed to fetch or cache'${sheet_name}'.",
                localizedMessage = e.localizedMessage ?: "null localizedMessage",
                secondaryText = "android",
                area = "ExamSheetRepository.fetchFromNetworkAndCacheFormat0File()"
            )
            ttsStatsRepository.incGlobalFaultCount(faultDownloadSheet)
            Result.failure(e)
        }
    }

    private suspend fun downloadFromFirestoreCollections(examName: String): Format0File =
        coroutineScope {
            Timber.i("ExamSheetRepo.downloadFromFirestoreCollections(): '$examName'.")
            val examDocRef = firestore.collection("global").document(fb.exam_sheets)
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
                                pronounce = firestoreWord.pronounce,
                                lockedClause = firestoreWord.lockedClause,
                                weakenedClause = firestoreWord.weakenedClause
                            )
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "Codable ERROR: Failed to decode VocabWord in category '${firestoreCategory.title}'."
                            )
                            TimberFault.f(
                                message = "ExamSheetRepo: Codable ERROR: Failed to decode VocabWord in category '${firestoreCategory.title}}'.",
                                localizedMessage = e.localizedMessage ?: "null localizedMessage",
                                secondaryText = "android",
                                area = "ExamSheetRepository.downloadFromFirestoreCollections()"
                            )
                            ttsStatsRepository.incGlobalFaultCount(faultDownloadSheet)
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

    private suspend fun downloadAndAssembleFormat1(examName: String): Format1File = coroutineScope {
        Timber.d("ExamSheetRepo: Assembling Format1 sheet for '$examName' from sub-collections.")

        // Define the base path to the specific exam sheet document
        val examDocRef = firestore.collection(fb.global).document(fb.exam_sheets)
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
            val snapshot = examDocRef.collection(fb.tabs).orderBy(fb.sortorder).get().await()
            // Use .toObjects() for clean conversion from documents to a list of data classes
            snapshot.toObjects(TabHeaderForFirestore::class.java)
        }

        //3 of 3
        val bodyDeferred = async(Dispatchers.IO) {
            val snapshot = examDocRef.collection(fb.wordsAndSentences).get().await()
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
                    Format1Entry(
                        word = row.word,
                        sentence = row.sentence,
                        translation = row.translation,
                        definition = row.definition
                    )
                }

            // Create the final section object
            Format1Level(
                title = header.title,
                description = header.description,
                sortOrder = header.sortorder,
                wordsAndSentences = wordsForThisHeader
            )
        }

        return@coroutineScope Format1File(
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

            //we now have bundle file
//            Timber.e(e, "ExamSheetRepo: CRITICAL Error in getFormat2Sheet for '$sheet_name'.")
//            TimberFault.f(
//                message = "ExamSheetRepo: CRITICAL Error in getFormat2Sheet for '$sheet_name'.",
//                localizedMessage = e.localizedMessage ?: "null localizedMessage",
//                secondaryText = "android",
//                area = "ExamSheetRepository.getFormat2Sheet()"
//            )
//            ttsStatsRepository.incGlobalFaultCount(faultDownloadSheet)
            return Result.failure(e)
        }
    }
    // ---------------- fileFormat 7 / 10 (grammar & usage quizzes; same structure) ----------------

    suspend fun getFormat7or10Sheet(sheet_name: String, forceRefresh: Boolean): Result<Format7or10File> {
        return try {
            // a. Disk cache first (unless forcing a refresh)
            if (!forceRefresh) {
                readFormat7or10SheetFromCache(sheet_name)?.let { cachedFile ->
                    Timber.d("ExamSheetRepo: Returning '$sheet_name' (Format7/10) from disk cache.")
                    return Result.success(cachedFile)
                }
            }
            // b. Assemble from Firestore, then cache to disk.
            val file = downloadAndAssembleFormat7or10(sheet_name)
            val cacheFile = getCacheFilePointer(sheet_name)
            cacheFile.writeText(jsonParser.encodeToString(Format7or10File.serializer(), file))
            Timber.i("ExamSheetRepo: Fetched and cached '$sheet_name' (Format7/10).")
            Result.success(file)
        } catch (e: Exception) {
            Timber.w(e, "ExamSheetRepo: getFormat7or10Sheet failed for '$sheet_name'.")
            Result.failure(e)
        }
    }

    private suspend fun readFormat7or10SheetFromCache(logicalName: String): Format7or10File? =
        withContext(Dispatchers.IO) {
            val file = getCacheFilePointer(logicalName)
            if (!file.exists()) return@withContext null
            return@withContext try {
                jsonParser.decodeFromString<Format7or10File>(file.readText())
            } catch (e: Exception) {
                Timber.e(e, "Failed to read Format7/10 from disk cache for '$logicalName'")
                null
            }
        }

    /** Reads the flattened `sections` subcollection and regroups by `sortorder` into levels. */
    private suspend fun downloadAndAssembleFormat7or10(examName: String): Format7or10File = coroutineScope {
        val examDocRef = firestore.collection(fb.global).document(fb.exam_sheets)
            .collection("sheets").document(examName)

        val headerDeferred = async(Dispatchers.IO) {
            examDocRef.get().await().also {
                if (!it.exists()) throw Exception("Root document '$examName' (Format7/10) not found.")
            }
        }
        val sectionsDeferred = async(Dispatchers.IO) {
            examDocRef.collection("sections").get().await().toObjects(Format7or10SectionDTO::class.java)
        }
        val header = headerDeferred.await()
        val allSections = sectionsDeferred.await()

        val lists = allSections.groupBy { it.sortorder }.entries.mapNotNull { (sortorder, dtos) ->
            val first = dtos.firstOrNull() ?: return@mapNotNull null
            val sections = dtos.sortedBy { it.page }.map { dto ->
                Format7or10Section(
                    title = dto.title,
                    page = dto.page,
                    sentence = dto.sentence,
                    explain = dto.explain,
                    summary = dto.summary,
                    level = dto.level,
                    category = dto.category,
                    words = dto.words.map { Format7or10Word(word = it.word, ok = it.ok) }
                )
            }
            Format7or10List(
                title = first.levelTitle,
                description = first.description,
                sortorder = sortorder,
                learningTitle = first.learningTitle,
                learningPoints = first.learningPoints,
                sections = sections
            )
        }.sortedBy { it.sortorder }

        return@coroutineScope Format7or10File(
            fileFormat = (header.get("fileformat") as? Number)?.toInt() ?: 7,
            sheetName = header.getString("sheetname") ?: examName,
            title = header.getString("title"),
            updatedDate = (header.get("updatedDate") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L,
            location = (header.get("location") as? Number)?.toInt() ?: 0,
            data = lists
        )
    }

    // ---------------- fileFormat 13 (section quizzes -> WordQuizRoot) ----------------

    suspend fun getFormat13Sheet(sheet_name: String, forceRefresh: Boolean): Result<WordQuizRoot> {
        return try {
            if (!forceRefresh) {
                readFormat13SheetFromCache(sheet_name)?.let { cachedFile ->
                    Timber.d("ExamSheetRepo: Returning '$sheet_name' (Format13) from disk cache.")
                    return Result.success(cachedFile)
                }
            }
            val file = downloadAndAssembleFormat13(sheet_name)
            val cacheFile = getCacheFilePointer(sheet_name)
            cacheFile.writeText(jsonParser.encodeToString(WordQuizRoot.serializer(), file))
            Timber.i("ExamSheetRepo: Fetched and cached '$sheet_name' (Format13).")
            Result.success(file)
        } catch (e: Exception) {
            Timber.w(e, "ExamSheetRepo: getFormat13Sheet failed for '$sheet_name'.")
            Result.failure(e)
        }
    }

    private suspend fun readFormat13SheetFromCache(logicalName: String): WordQuizRoot? =
        withContext(Dispatchers.IO) {
            val file = getCacheFilePointer(logicalName)
            if (!file.exists()) return@withContext null
            return@withContext try {
                jsonParser.decodeFromString<WordQuizRoot>(file.readText())
            } catch (e: Exception) {
                Timber.e(e, "Failed to read Format13 from disk cache for '$logicalName'")
                null
            }
        }

    /** Reads the flattened `sections` subcollection and regroups by `sortorder` into a WordQuizRoot. */
    private suspend fun downloadAndAssembleFormat13(examName: String): WordQuizRoot = coroutineScope {
        val examDocRef = firestore.collection(fb.global).document(fb.exam_sheets)
            .collection("sheets").document(examName)

        val headerDeferred = async(Dispatchers.IO) {
            examDocRef.get().await().also {
                if (!it.exists()) throw Exception("Root document '$examName' (Format13) not found.")
            }
        }
        val sectionsDeferred = async(Dispatchers.IO) {
            examDocRef.collection("sections").get().await().toObjects(Format13SectionDTO::class.java)
        }
        val header = headerDeferred.await()
        val allSections = sectionsDeferred.await()

        val lists = allSections.groupBy { it.sortorder }.entries.mapNotNull { (sortorder, dtos) ->
            val first = dtos.firstOrNull() ?: return@mapNotNull null
            val sections = dtos.sortedBy { it.page }.map { dto ->
                WordQuizSections(
                    title = dto.title,
                    page = dto.page,
                    question = dto.question,
                    explain = dto.explain?.let { mapToDictionaryEntry(it) },
                    summary = dto.summary,
                    answers = dto.answers.map { WordQuizSWordsState(answer = it.answer, ok = it.ok) }
                )
            }
            WordQuizList(
                title = first.listTitle,
                description = first.description,
                sortorder = sortorder,
                sections = sections
            )
        }.sortedBy { it.sortorder }

        return@coroutineScope WordQuizRoot(
            fileFormat = (header.get("fileformat") as? Number)?.toInt() ?: 13,
            sheetName = header.getString("sheetname") ?: examName,
            title = header.getString("title"),
            updatedDate = (header.get("updatedDate") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L,
            location = (header.get("location") as? Number)?.toInt() ?: 0,
            data = lists
        )
    }

    /** Rebuilds a DictionaryEntry from the Firestore-stored nested map (map -> JSON -> kotlinx decode). */
    private fun mapToDictionaryEntry(map: Map<String, Any?>): DictionaryEntry? = try {
        val json = org.json.JSONObject(map).toString()
        jsonParser.decodeFromString<DictionaryEntry>(json)
    } catch (e: Exception) {
        Timber.w(e, "ExamSheetRepo: failed to decode explain (Format13); using null")
        null
    }

    suspend fun getFormat3Sheet(sheet_name: String, forceRefresh: Boolean): Result<Format3File>{
        return try {
            // a. Check disk cache first (unless forcing a refresh)
            if (!forceRefresh) {
                readFormat3SheetFromCache(sheet_name)?.let { cachedFile ->
                    Timber.d("ExamSheetRepo: Returning '$sheet_name' (Format2) from disk cache. Yippee")
                    return Result.success(cachedFile)
                }
            }

            val format3File = downloadAndAssembleFormat3(sheet_name)
            val cacheFile = getCacheFilePointer(sheet_name)
            val jsonString = jsonParser.encodeToString(Format3File.serializer(), format3File)
            cacheFile.writeText(jsonString)

            Timber.i("ExamSheetRepo: Successfully fetched and cached '$sheet_name' (Format3).")

            // 3. ✅ Wrap the successful result in Result.success()
            Result.success(format3File)


        } catch (e: Exception) {
            Timber.e(e, "ExamSheetRepo: CRITICAL Error in getFormat3Sheet for '$sheet_name'.")
            TimberFault.f(
                message = "ExamSheetRepo: CRITICAL Error in getFormat3Sheet for '$sheet_name'.",
                localizedMessage = e.localizedMessage ?: "null localizedMessage",
                secondaryText = "android",
                area = "ExamSheetRepository.getFormat3Sheet()"
            )
            ttsStatsRepository.incGlobalFaultCount(faultDownloadSheet)
            return Result.failure(e)
        }
    }



    private suspend fun downloadAndAssembleFormat3(documentId: String): Format3File {
        val db = FirebaseFirestore.getInstance()
        // Make sure this matches your actual Firestore collection name
        val collectionName = "reference_sheets"

       // val examDocRef = firestore.collection(fb.global).document(fb.exam_sheets)
         //   .collection("sheets").document(documentId)

        return try {
            val snapshot = db.collection(fb.global).document(fb.exam_sheets)
                .collection("sheets").document(documentId)
                .get()
                .await()

            if (snapshot.exists()) {
                // Convert to object. If parsing fails, toObject returns null,
                // so we elvis operator (?:) to fall back to an empty object.
                val sheet = snapshot.toObject(Format3File::class.java)

                if (sheet != null) {
                    Timber.i("Successfully loaded sheet: ${sheet.title}")
                    sheet
                } else {
                    Timber.e("Document exists but failed to parse into Format3File")
                    Format3File() // Return empty object
                }
            } else {
                Timber.w("Document $documentId does not exist. Returning empty sheet.")
                Format3File() // Return empty object
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Format3 sheet: $documentId")
            Format3File() // Return empty object
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
    private suspend fun readFormat3SheetFromCache(logicalName: String): Format3File? = withContext(Dispatchers.IO) {
        val file = getCacheFilePointer(logicalName)
        if (!file.exists()) return@withContext null

        return@withContext try {
            val jsonString = file.readText()
            jsonParser.decodeFromString<Format3File>(jsonString)
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

        val examDocRef = firestore.collection(fb.global).document(fb.exam_sheets)
            .collection("sheets").document(examName)

        // 1. --- LAUNCH CONCURRENT FETCHES for the root metadata and the flattened word list ---
        val rootDtoDeferred = async(Dispatchers.IO) {
            examDocRef.get().await().toObject(SheetHeaderFormat2DTO::class.java)
                ?: throw Exception("Root document '$examName' (Format2) not found or failed to parse.")
        }
        val allEntriesDeferred = async(Dispatchers.IO) {
            val snapshot = examDocRef.collection(fb.wordsAndSentences).get().await()
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