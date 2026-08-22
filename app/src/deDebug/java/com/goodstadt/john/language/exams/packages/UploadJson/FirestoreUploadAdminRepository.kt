package com.goodstadt.john.language.exams.packages.UploadJson

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/**
 * DEBUG + `de` ONLY (lives in `src/deDebug`, so it is compiled solely into the German debug variant).
 *
 * Administers the German Firestore quiz-content project. Every sheet is a document that hangs off the
 * `global/exam_sheets/sheets` collection, e.g. sheet "GermanA1Vocab" is the document
 * `/global/exam_sheets/sheets/GermanA1Vocab`.
 *
 * Uploads/reads are dispatched by the sheet's `fileFormat` so there is ONE upload routine and ONE
 * read routine per format. fileFormat 0 = a vocab list, written as STRICT SUBCOLLECTIONS to match the
 * completed English project so the same read code (ExamSheetRepository) runs:
 *
 *   /global/exam_sheets/sheets/<sheet>            (metadata: VocabFileDTO fields + tabtitles array)
 *      /categories/<category_n>                   (FirestoreCategoryDTO: title, tabNumber, sortOrder)
 *         /words/<word_n>                          (FirestoreWordDTO: word, sentences[], translations[], …)
 */
class FirestoreUploadAdminRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : UploadAdminRepository {

    /** The single place that resolves a sheet name to its document under global/exam_sheets/sheets. */
    private fun sheetDoc(docName: String): DocumentReference =
        firestore.collection(GLOBAL).document(EXAM_SHEETS).collection(SHEETS).document(docName)

    // ---------------------------------------------------------------- top-button convenience

    override suspend fun readUploadDate(): Result<String?> = try {
        val snapshot = sheetDoc(GERMAN_A1_VOCAB).get().await()
        if (!snapshot.exists()) {
            Timber.w("UploadAdmin: /$GLOBAL/$EXAM_SHEETS/$SHEETS/$GERMAN_A1_VOCAB does not exist")
            Result.success(null)
        } else {
            Result.success(formatUploadDate(snapshot.get(FIELD_UPLOAD_DATE)))
        }
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: failed to read uploadDate")
        Result.failure(e)
    }

    // ---------------------------------------------------------------- dispatch by fileFormat

    override suspend fun uploadSheet(docName: String, json: String): Result<Unit> = try {
        val root = JSONObject(json)
        if (docName == DAILY_WORD_DICTIONARY) {
            uploadDailyWordDictionary(docName, root)
        } else {
            when (val fileFormat = root.optInt(FIELD_FILE_FORMAT, 0)) {
                0 -> uploadFormat0(docName, root)
                1 -> uploadFormat1(docName, root) // data[]->wordsAndSentences[]: written as tabs + wordsAndSentences
                2 -> uploadFormat2(docName, root)
                7, 10 -> uploadFormat7or10(docName, root) // 7 & 10 share the same JSON structure
                13 -> uploadFormat13(docName, root)
                else -> Result.failure(UnsupportedOperationException("Upload for fileFormat $fileFormat not implemented yet"))
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: upload '$docName' failed")
        Result.failure(e)
    }

    override suspend fun sheetExists(docName: String): Result<Boolean> = try {
        val exists = if (docName == DAILY_WORD_DICTIONARY) {
            sheetDoc(docName).collection(POOL).document(CURRENT).get().await().exists()
        } else {
            sheetDoc(docName).get().await().exists()
        }
        Result.success(exists)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: existence check for '$docName' failed")
        Result.failure(e)
    }

    override suspend fun readSheet(docName: String): Result<String?> = try {
        if (docName == DAILY_WORD_DICTIONARY) {
            readDailyWordDictionary(docName)
        } else {
            val snapshot = sheetDoc(docName).get().await()
            if (!snapshot.exists()) {
                // Top-level document simply not present (never uploaded) - NOT an error; caller shows blank.
                Timber.i("UploadAdmin: '$docName' top-level doc not present -> blank")
                Result.success(null)
            } else {
                when (val fileFormat = (snapshot.get(FIELD_FILE_FORMAT) as? Number)?.toInt() ?: 0) {
                    0 -> readFormat0(docName, snapshot)
                    1 -> readFormat1(docName, snapshot)
                    2 -> readFormat2(docName, snapshot)
                    7, 10 -> readFormat7or10(docName, snapshot)
                    13 -> readFormat13(docName, snapshot)
                    else -> Result.failure(UnsupportedOperationException("Read for fileFormat $fileFormat not implemented yet"))
                }
            }
        }
    } catch (e: Exception) {
        // A real error (permission denied, network, …) reading the top-level document.
        Timber.e(e, "UploadAdmin: read '$docName' failed")
        Result.failure(e)
    }

    // ---------------------------------------------------------------- Daily Word Dictionary

    /**
     * Uploads the pool JSON into the shape consumed by both Android and iOS:
     *
     * /global/exam_sheets/sheets/DailyWordDictionary
     *    /entries/{entryId}
     *    /pool/current
     *
     * The JSON deliberately has no `fileformat`, so this sheet is dispatched by its document name.
     */
    private suspend fun uploadDailyWordDictionary(
        docName: String,
        root: JSONObject
    ): Result<Unit> = try {
        val poolConfig = root.optJSONObject(POOL)
            ?: throw IllegalArgumentException("Daily Word JSON is missing '$POOL'")
        val poolId = poolConfig.optString("poolId", CURRENT)
        require(poolId == CURRENT) { "Daily Word poolId must be '$CURRENT', found '$poolId'" }

        val orderedJson = poolConfig.optJSONArray("orderedEntryIds")
            ?: throw IllegalArgumentException("Daily Word JSON is missing 'pool.orderedEntryIds'")
        val orderedIds = (0 until orderedJson.length()).map { orderedJson.getString(it) }
        require(orderedIds.isNotEmpty()) { "Daily Word pool is empty" }
        require(orderedIds.none { it.isBlank() }) { "Daily Word pool contains a blank entryId" }
        require(orderedIds.none { '/' in it }) { "Daily Word entryIds cannot contain '/'" }
        require(orderedIds.distinct().size == orderedIds.size) { "Daily Word pool contains duplicate entryIds" }

        val entriesJson = root.optJSONArray(ENTRIES)
            ?: throw IllegalArgumentException("Daily Word JSON is missing '$ENTRIES'")
        val entryById = LinkedHashMap<String, JSONObject>()
        for (index in 0 until entriesJson.length()) {
            val entry = entriesJson.getJSONObject(index)
            val entryId = entry.optString("entryId")
            require(entryId.isNotBlank()) { "Daily Word entry at index $index has no entryId" }
            require('/' !in entryId) { "Daily Word entryId '$entryId' cannot contain '/'" }
            require(entryById.put(entryId, entry) == null) { "Duplicate Daily Word entryId '$entryId'" }
        }

        val missing = orderedIds.filterNot(entryById::containsKey)
        require(missing.isEmpty()) { "Pool references missing entries: $missing" }
        val notInPool = entryById.keys.filterNot(orderedIds::contains)
        require(notInPool.isEmpty()) { "Entries are not referenced by the pool: $notInPool" }

        val sheetRef = sheetDoc(docName)
        var batch = firestore.batch()
        var ops = 0
        for (entryId in orderedIds) {
            val entryRef = sheetRef.collection(ENTRIES).document(entryId)
            batch.set(entryRef, jsonObjectToFirestoreMap(entryById.getValue(entryId)))
            if (++ops >= BATCH_LIMIT) {
                batch.commit().await()
                batch = firestore.batch()
                ops = 0
            }
        }
        if (ops > 0) batch.commit().await()

        val now = System.currentTimeMillis() / 1000L
        val batchId = root.optString("batchId", "")
        val timezoneRule = root.optString("timezoneRule", "UTC")
        val uiPolicyJson = root.optJSONObject("uiPolicy") ?: JSONObject()
        val uiPolicy = hashMapOf<String, Any>(
            "maxBrowseDaysBack" to uiPolicyJson.optInt("maxBrowseDaysBack", 7),
            "lockForwardAtToday" to uiPolicyJson.optBoolean("lockForwardAtToday", true)
        )

        // Write the root metadata and the live pool only after all referenced entries exist.
        val indexBatch = firestore.batch()
        indexBatch.set(
            sheetRef,
            hashMapOf(
                "sheetName" to docName,
                "batchId" to batchId,
                "timezoneRule" to timezoneRule,
                "entryCount" to orderedIds.size,
                FIELD_UPLOAD_DATE to now,
                "updatedDate" to now
            )
        )
        indexBatch.set(
            sheetRef.collection(POOL).document(CURRENT),
            hashMapOf(
                "batchId" to batchId,
                "timezoneRule" to timezoneRule,
                "updatedDate" to now,
                "uiPolicy" to uiPolicy,
                "orderedEntryIds" to orderedIds
            )
        )
        indexBatch.commit().await()

        Timber.i("UploadAdmin: uploaded '$docName': ${orderedIds.size} entries and pool/$CURRENT")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: Daily Word upload '$docName' failed")
        Result.failure(e)
    }

    /** Reads the pool and verifies that every referenced entry document exists. */
    private suspend fun readDailyWordDictionary(docName: String): Result<String?> = try {
        val sheetRef = sheetDoc(docName)
        val poolSnapshot = sheetRef.collection(POOL).document(CURRENT).get().await()
        if (!poolSnapshot.exists()) {
            Timber.i("UploadAdmin: '$docName/$POOL/$CURRENT' not present -> blank")
            Result.success(null)
        } else {
            val orderedIds = (poolSnapshot.get("orderedEntryIds") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            val entriesSnapshot = sheetRef.collection(ENTRIES).get().await()
            val uploadedIds = entriesSnapshot.documents.map { it.id }.toSet()
            val missing = orderedIds.filterNot(uploadedIds::contains)
            if (orderedIds.isEmpty()) {
                Result.failure(IllegalStateException("'$docName' pool/current is empty"))
            } else if (missing.isNotEmpty()) {
                Result.failure(IllegalStateException("'$docName' is missing entry documents: $missing"))
            } else {
                val summary = "'$docName' OK: ${entriesSnapshot.size()} entries, " +
                    "${orderedIds.size} pool IDs, pool/$CURRENT verified"
                Timber.i("UploadAdmin: readDailyWordDictionary -> $summary")
                Result.success(summary)
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: Daily Word read '$docName' failed")
        Result.failure(e)
    }

    /** Converts JSON into Firestore values while omitting only top-level JSON null fields. */
    private fun jsonObjectToFirestoreMap(obj: JSONObject): Map<String, Any> = buildMap {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            jsonToStorable(obj.get(key))?.let { put(key, it) }
        }
    }

    // ---------------------------------------------------------------- fileFormat 0 (vocab)

    /**
     * Uploads a fileFormat-0 vocab sheet. Pass the parsed [root] JSON and the target [docName]; the
     * sheet doc, its `categories` subcollection and each category's `words` subcollection are created.
     */
    private suspend fun uploadFormat0(docName: String, root: JSONObject): Result<Unit> = try {
        val sheetRef = sheetDoc(docName)

        // 1. Wipe any previous categories/words so a re-upload is clean (no stale docs).
        clearCategories(sheetRef)

        // 2. Sheet metadata (VocabFileDTO field names; the JSON uses lowercase, so map explicitly).
        val meta = hashMapOf<String, Any>(
            "fileformat" to root.optInt("fileformat", 0),
            "location" to root.optInt("location", 0),
            "sheetName" to docName,
            "updatedDate" to root.optInt("updatedDate", 0),
            FIELD_UPLOAD_DATE to (System.currentTimeMillis() / 1000.0), // Double epoch seconds
            "id" to root.optString("id", ""),
            "native" to root.optString("native", ""),
            "name" to root.optString("name", ""),
            "romanized" to root.optBoolean("romanized", false),
            "nativeName" to root.optString("nativename", ""),
            "googleVoicePrefix" to root.optString("googlevoiceprefix", ""),
            "voiceName" to root.optString("voicename", ""),
            "tabtitles" to jsonStringArray(root.optJSONArray("tabtitles"))
        )
        sheetRef.set(meta).await()

        // 3. Categories subcollection, each with a words subcollection.
        val categories = root.optJSONArray("categories") ?: JSONArray()
        var totalWords = 0
        for (ci in 0 until categories.length()) {
            val cat = categories.getJSONObject(ci)
            val title = cat.optString("title", "")
            // The category document id IS its title (e.g. "Hallo (A1)"). Fall back to an index if the
            // title is blank, and swap any "/" (illegal in a Firestore doc id) for a dash.
            val catId = title.ifBlank { "category_%04d".format(ci) }.replace("/", "-")
            val catRef = sheetRef.collection(CATEGORIES).document(catId)
            catRef.set(
                hashMapOf(
                    "title" to title,
                    "tabNumber" to cat.optInt("tabNumber", 0),
                    // Use the category's POSITION in the JSON array as sortOrder. The source data has
                    // every category at sortOrder 10, but a Firestore collection.get() returns docs in
                    // document-id order (here: alphabetical by title), so without a real sort key the
                    // app's sortedBy{sortOrder} shows them alphabetically. The array order is the
                    // intended order (as the bundle proves), so index it here.
                    "sortOrder" to ci
                )
            ).await()

            val words = cat.optJSONArray("words") ?: JSONArray()
            var batch = firestore.batch()
            var ops = 0
            for (wi in 0 until words.length()) {
                val w = words.getJSONObject(wi)
                val sentencesJson = w.optJSONArray("sentences") ?: JSONArray()
                val sentences = ArrayList<String>()
                val translations = ArrayList<String>()
                for (si in 0 until sentencesJson.length()) {
                    val s = sentencesJson.getJSONObject(si)
                    sentences.add(s.optString("sentence", ""))
                    translations.add(s.optString("translation", ""))
                }
                // The word document id IS the 'word' field (unique within the category's words).
                // Fall back to an index if blank, and swap any "/" (illegal in a doc id) for a dash.
                val wordText = w.optString("word", "")
                val wordId = wordText.ifBlank { "word_%04d".format(wi) }.replace("/", "-")
                val wordRef = catRef.collection(WORDS).document(wordId)
                batch.set(
                    wordRef,
                    hashMapOf(
                        "id" to w.optInt("id", 0),
                        "sortOrder" to w.optInt("sortOrder", 0),
                        "translation" to w.optString("translation", ""),
                        "romanisation" to w.optString("romanisation", ""),
                        "partOfSpeech" to w.optString("partOfSpeech", ""),
                        "word" to w.optString("word", ""),
                        "definition" to w.optString("definition", ""),
                        "IPA" to w.optString("IPA", ""),
                        "pronounce" to w.optString("pronounce", ""),
                        "group" to w.optString("group", ""),
                        "sentences" to sentences,
                        "translations" to translations,
                        "lockedClause" to w.optString("lockedClause", ""),
                        "weakenedClause" to w.optString("weakenedClause", "")
                    )
                )
                totalWords++
                if (++ops >= BATCH_LIMIT) {
                    batch.commit().await(); batch = firestore.batch(); ops = 0
                }
            }
            if (ops > 0) batch.commit().await()
        }

        Timber.i("UploadAdmin: uploaded '$docName' (fileFormat 0): ${categories.length()} categories, $totalWords words")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: uploadFormat0 '$docName' failed")
        Result.failure(e)
    }

    /** Reads back a fileFormat-0 sheet and its subcollections, returning a summary for a sanity check. */
    private suspend fun readFormat0(
        docName: String,
        sheetSnap: com.google.firebase.firestore.DocumentSnapshot
    ): Result<String> = try {
        val categoriesSnap = sheetDoc(docName).collection(CATEGORIES).get().await()
        var wordCount = 0
        for (cat in categoriesSnap.documents) {
            wordCount += cat.reference.collection(WORDS).get().await().size()
        }
        val uploaded = formatUploadDate(sheetSnap.get(FIELD_UPLOAD_DATE)) ?: "?"
        val sheetName = sheetSnap.getString("sheetName") ?: docName
        val summary = "'$docName' OK (fileFormat 0): sheetName='$sheetName', " +
            "${categoriesSnap.size()} categories, $wordCount words, uploaded $uploaded"
        Timber.i("UploadAdmin: readFormat0 -> $summary")
        Result.success(summary)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: readFormat0 '$docName' failed")
        Result.failure(e)
    }

    // ---------------------------------------------------------------- fileFormat 1 (sounds-the-same etc.)

    /**
     * Uploads a fileFormat-1 sheet. The JSON shape is `data[]` sections, each
     * `{ title, description, sortorder, wordsAndSentences[] { word, sentence, definition } }`.
     * This is NOT the fileFormat-0 (categories/words) shape: the app reads it via
     * `downloadAndAssembleFormat1`, which expects a `tabs` subcollection (TabHeaderForFirestore:
     * title, description, tabID, sortorder) and a flat `wordsAndSentences` subcollection
     * (WordAndSentenceForFirestore: parentID, word, sentence, translation, definition), joined on
     * `parentID == tabID`. The source JSON has no tabID/parentID, so we synthesise the section index
     * as the tabID and stamp it onto each word's parentID.
     */
    private suspend fun uploadFormat1(docName: String, root: JSONObject): Result<Unit> = try {
        val sheetRef = sheetDoc(docName)

        // 1. Wipe any previous tabs/wordsAndSentences so a re-upload is clean.
        clearSubcollection(sheetRef, TABS)
        clearSubcollection(sheetRef, WORDS_AND_SENTENCES)

        // 2. Sheet header.
        val meta = hashMapOf<String, Any>(
            "fileformat" to root.optInt("fileformat", 1),
            "sheetname" to docName,
            "location" to root.optInt("location", 0),
            "updatedDate" to FieldValue.serverTimestamp(),
            FIELD_UPLOAD_DATE to FieldValue.serverTimestamp()
        )
        sheetRef.set(meta).await()

        // 3. data[] sections -> tabs docs; their words -> flat wordsAndSentences docs (parentID = tab index).
        val data = root.optJSONArray("data") ?: JSONArray()
        var batch = firestore.batch()
        var ops = 0
        var totalWords = 0
        for (ti in 0 until data.length()) {
            val section = data.getJSONObject(ti)
            val tabID = ti // synthesised, unique per section, matched by each word's parentID
            val sortorder = section.optInt("sortorder", ti + 1)

            val tabRef = sheetRef.collection(TABS).document("tab_%04d".format(tabID))
            batch.set(
                tabRef,
                hashMapOf(
                    "title" to section.optString("title", ""),
                    "description" to section.optString("description", ""),
                    "tabID" to tabID,
                    "sortorder" to sortorder
                )
            )
            if (++ops >= BATCH_LIMIT) { batch.commit().await(); batch = firestore.batch(); ops = 0 }

            val entries = section.optJSONArray("wordsAndSentences") ?: JSONArray()
            for (wi in 0 until entries.length()) {
                val entry = entries.getJSONObject(wi)
                val word = entry.optString("word", "")
                // Flat subcollection, so make the id unique across sections: "<tabID>_<word>".
                val docId = ("${tabID}_${word.ifBlank { "entry_%04d".format(totalWords) }}").replace("/", "-")
                val entryRef = sheetRef.collection(WORDS_AND_SENTENCES).document(docId)
                batch.set(
                    entryRef,
                    hashMapOf(
                        "parentID" to tabID,
                        "word" to word,
                        "sentence" to entry.optString("sentence", ""),
                        "translation" to entry.optString("translation", ""),
                        "definition" to entry.optString("definition", "")
                    )
                )
                totalWords++
                if (++ops >= BATCH_LIMIT) { batch.commit().await(); batch = firestore.batch(); ops = 0 }
            }
        }
        if (ops > 0) batch.commit().await()

        Timber.i("UploadAdmin: uploaded '$docName' (fileFormat 1): ${data.length()} tabs, $totalWords words")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: uploadFormat1 '$docName' failed")
        Result.failure(e)
    }

    /** Reads back a fileFormat-1 sheet's tabs + wordsAndSentences subcollections for a sanity check. */
    private suspend fun readFormat1(
        docName: String,
        sheetSnap: com.google.firebase.firestore.DocumentSnapshot
    ): Result<String> = try {
        val tabs = sheetDoc(docName).collection(TABS).get().await()
        val words = sheetDoc(docName).collection(WORDS_AND_SENTENCES).get().await()
        val uploaded = formatUploadDate(sheetSnap.get(FIELD_UPLOAD_DATE)) ?: "?"
        val summary = "'$docName' OK (fileFormat 1): ${tabs.size()} tabs, ${words.size()} words, uploaded $uploaded"
        Timber.i("UploadAdmin: readFormat1 -> $summary")
        Result.success(summary)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: readFormat1 '$docName' failed")
        Result.failure(e)
    }

    // ---------------------------------------------------------------- fileFormat 2 (word pairs etc.)

    /**
     * Uploads a fileFormat-2 sheet. The nested JSON (data[] levels -> wordsAndSentences[] entries) is
     * FLATTENED into a single `wordsAndSentences` subcollection of Format2WordAndSentenceDTO docs; the
     * app reassembles the levels by grouping on `sortorder`.
     */
    private suspend fun uploadFormat2(docName: String, root: JSONObject): Result<Unit> = try {
        val sheetRef = sheetDoc(docName)

        // 1. Wipe the previous flat entries so a re-upload is clean.
        clearSubcollection(sheetRef, WORDS_AND_SENTENCES)

        // 2. Sheet header (SheetHeaderFormat2DTO field names).
        val meta = hashMapOf<String, Any>(
            "fileformat" to root.optInt("fileformat", 2),
            "sheetname" to docName,
            "title" to root.optString("title", ""),
            "description" to root.optString("description", ""),
            "location" to root.optInt("location", 0),
            "updatedDate" to FieldValue.serverTimestamp(),
            FIELD_UPLOAD_DATE to FieldValue.serverTimestamp()
        )
        sheetRef.set(meta).await()

        // 3. Flatten data[] levels -> wordsAndSentences entries, carrying the level's title/desc/sortorder.
        val data = root.optJSONArray("data") ?: JSONArray()
        var batch = firestore.batch()
        var ops = 0
        var totalEntries = 0
        for (li in 0 until data.length()) {
            val level = data.getJSONObject(li)
            val levelTitle = level.optString("title", "")
            val levelDesc = level.optString("description", "")
            val levelSort = level.optInt("sortorder", li + 1)
            val entries = level.optJSONArray("wordsAndSentences") ?: JSONArray()
            for (ei in 0 until entries.length()) {
                val entry = entries.getJSONObject(ei)
                val word = entry.optString("word", "")
                val sentencesJson = entry.optJSONArray("sentences") ?: JSONArray()
                val sentences = ArrayList<String>()
                for (si in 0 until sentencesJson.length()) {
                    sentences.add(sentencesJson.getJSONObject(si).optString("sentence", ""))
                }
                // Flat subcollection, so make the id unique across levels: "<sortorder>_<word>".
                val docId = ("${levelSort}_${word.ifBlank { "entry_%04d".format(totalEntries) }}").replace("/", "-")
                val entryRef = sheetRef.collection(WORDS_AND_SENTENCES).document(docId)
                batch.set(
                    entryRef,
                    hashMapOf(
                        "title" to levelTitle,
                        "description" to levelDesc,
                        "sortorder" to levelSort,
                        "explanation" to entry.optString("explanation", ""),
                        "word" to word,
                        "definition" to entry.optString("definition", ""),
                        "sentences" to sentences
                    )
                )
                totalEntries++
                if (++ops >= BATCH_LIMIT) { batch.commit().await(); batch = firestore.batch(); ops = 0 }
            }
        }
        if (ops > 0) batch.commit().await()

        Timber.i("UploadAdmin: uploaded '$docName' (fileFormat 2): ${data.length()} levels, $totalEntries entries")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: uploadFormat2 '$docName' failed")
        Result.failure(e)
    }

    /** Reads back a fileFormat-2 sheet's flat wordsAndSentences subcollection for a sanity check. */
    private suspend fun readFormat2(
        docName: String,
        sheetSnap: com.google.firebase.firestore.DocumentSnapshot
    ): Result<String> = try {
        val entries = sheetDoc(docName).collection(WORDS_AND_SENTENCES).get().await()
        val levelCount = entries.documents.mapNotNull { (it.get("sortorder") as? Number)?.toInt() }.toSet().size
        val title = sheetSnap.getString("title") ?: docName
        val summary = "'$docName' OK (fileFormat 2): title='$title', $levelCount levels, ${entries.size()} entries"
        Timber.i("UploadAdmin: readFormat2 -> $summary")
        Result.success(summary)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: readFormat2 '$docName' failed")
        Result.failure(e)
    }

    // ---------------------------------------------------------------- fileFormat 7 (grammar quizzes)

    /**
     * Uploads a fileFormat-7 sheet (grammar quiz). The nested JSON (data[] levels -> sections[]) is
     * FLATTENED into a single `sections` subcollection; each section doc carries its level's metadata
     * (sortorder, learningTitle, learningPoints, …) so the app can regroup by `sortorder`. Each
     * section's answer options are stored as a `words` array of {ok, word} maps.
     */
    private suspend fun uploadFormat7or10(docName: String, root: JSONObject): Result<Unit> = try {
        val sheetRef = sheetDoc(docName)

        clearSubcollection(sheetRef, SECTIONS)

        val meta = hashMapOf<String, Any>(
            "fileformat" to root.optInt("fileformat", 7),
            "sheetname" to docName,
            "title" to root.optString("title", ""),
            "location" to root.optInt("location", 0),
            "updatedDate" to FieldValue.serverTimestamp(),
            FIELD_UPLOAD_DATE to FieldValue.serverTimestamp()
        )
        sheetRef.set(meta).await()

        val data = root.optJSONArray("data") ?: JSONArray()
        var batch = firestore.batch()
        var ops = 0
        var totalSections = 0
        for (li in 0 until data.length()) {
            val level = data.getJSONObject(li)
            val levelTitle = level.optString("title", "")
            val levelDesc = level.optString("description", "")
            val sortorder = level.optInt("sortorder", li + 1)
            val learningTitle = level.optString("learningTitle", "")
            val learningPoints = jsonStringArray(level.optJSONArray("learningPoints"))
            val sections = level.optJSONArray("sections") ?: JSONArray()
            for (si in 0 until sections.length()) {
                val sec = sections.getJSONObject(si)
                val wordsJson = sec.optJSONArray("words") ?: JSONArray()
                val words = ArrayList<Map<String, Any>>()
                for (wi in 0 until wordsJson.length()) {
                    val w = wordsJson.getJSONObject(wi)
                    words.add(mapOf("ok" to w.optBoolean("ok", false), "word" to w.optString("word", "")))
                }
                val entryRef = sheetRef.collection(SECTIONS).document("section_%04d".format(totalSections))
                batch.set(
                    entryRef,
                    hashMapOf(
                        // level metadata (repeated per section; regrouped by sortorder on read)
                        "levelTitle" to levelTitle,
                        "description" to levelDesc,
                        "sortorder" to sortorder,
                        "learningTitle" to learningTitle,
                        "learningPoints" to learningPoints,
                        // section fields
                        "title" to sec.optString("title", ""),
                        "page" to sec.optInt("page", si + 1),
                        "summary" to sec.optString("summary", ""),
                        "level" to sec.optString("level", ""),
                        "category" to sec.optString("category", ""),
                        "explain" to sec.optString("explain", ""),
                        "sentence" to sec.optString("sentence", ""),
                        "words" to words
                    )
                )
                totalSections++
                if (++ops >= BATCH_LIMIT) { batch.commit().await(); batch = firestore.batch(); ops = 0 }
            }
        }
        if (ops > 0) batch.commit().await()

        Timber.i("UploadAdmin: uploaded '$docName' (fileFormat 7/10): ${data.length()} levels, $totalSections sections")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: uploadFormat7or10 '$docName' failed")
        Result.failure(e)
    }

    /** Reads back a fileFormat-7 sheet's flat sections subcollection for a sanity check. */
    private suspend fun readFormat7or10(
        docName: String,
        sheetSnap: com.google.firebase.firestore.DocumentSnapshot
    ): Result<String> = try {
        val sections = sheetDoc(docName).collection(SECTIONS).get().await()
        val levelCount = sections.documents.mapNotNull { (it.get("sortorder") as? Number)?.toInt() }.toSet().size
        val title = sheetSnap.getString("title") ?: docName
        val summary = "'$docName' OK (fileFormat 7/10): title='$title', $levelCount levels, ${sections.size()} sections"
        Timber.i("UploadAdmin: readFormat7or10 -> $summary")
        Result.success(summary)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: readFormat7or10 '$docName' failed")
        Result.failure(e)
    }

    // ---------------------------------------------------------------- fileFormat 13 (section quizzes)

    /**
     * Uploads a fileFormat-13 sheet (WordQuiz section quiz). The nested JSON (data[] lists ->
     * sections[]) is FLATTENED into a `sections` subcollection; each section doc carries its list's
     * metadata (sortorder, title, description) plus the question, answers[{answer, ok}] and the nested
     * `explain` dictionary object (stored faithfully as a map), grouped by `sortorder` on read.
     */
    private suspend fun uploadFormat13(docName: String, root: JSONObject): Result<Unit> = try {
        val sheetRef = sheetDoc(docName)

        clearSubcollection(sheetRef, SECTIONS)

        val meta = hashMapOf<String, Any>(
            "fileformat" to root.optInt("fileformat", 13),
            "sheetname" to docName,
            "title" to root.optString("title", ""),
            "location" to root.optInt("location", 0),
            "updatedDate" to FieldValue.serverTimestamp(),
            FIELD_UPLOAD_DATE to FieldValue.serverTimestamp()
        )
        sheetRef.set(meta).await()

        val data = root.optJSONArray("data") ?: JSONArray()
        var batch = firestore.batch()
        var ops = 0
        var totalSections = 0
        for (li in 0 until data.length()) {
            val list = data.getJSONObject(li)
            val listTitle = list.optString("title", "")
            val listDesc = list.optString("description", "")
            val sortorder = list.optInt("sortorder", li + 1)
            val sections = list.optJSONArray("sections") ?: JSONArray()
            for (si in 0 until sections.length()) {
                val sec = sections.getJSONObject(si)
                val answersJson = sec.optJSONArray("answers") ?: JSONArray()
                val answers = ArrayList<Map<String, Any>>()
                for (ai in 0 until answersJson.length()) {
                    val a = answersJson.getJSONObject(ai)
                    answers.add(mapOf("answer" to a.optString("answer", ""), "ok" to a.optBoolean("ok", false)))
                }
                val explain = sec.optJSONObject("explain")?.let { jsonObjectToMap(it) } ?: emptyMap<String, Any?>()
                val entryRef = sheetRef.collection(SECTIONS).document("section_%04d".format(totalSections))
                batch.set(
                    entryRef,
                    hashMapOf(
                        "listTitle" to listTitle,
                        "description" to listDesc,
                        "sortorder" to sortorder,
                        "title" to sec.optString("title", ""),
                        "page" to sec.optInt("page", si + 1),
                        "question" to sec.optString("question", ""),
                        "summary" to sec.optString("summary", ""),
                        "answers" to answers,
                        "explain" to explain
                    )
                )
                totalSections++
                if (++ops >= BATCH_LIMIT) { batch.commit().await(); batch = firestore.batch(); ops = 0 }
            }
        }
        if (ops > 0) batch.commit().await()

        Timber.i("UploadAdmin: uploaded '$docName' (fileFormat 13): ${data.length()} lists, $totalSections questions")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: uploadFormat13 '$docName' failed")
        Result.failure(e)
    }

    /** Reads back a fileFormat-13 sheet's flat sections subcollection for a sanity check. */
    private suspend fun readFormat13(
        docName: String,
        sheetSnap: com.google.firebase.firestore.DocumentSnapshot
    ): Result<String> = try {
        val sections = sheetDoc(docName).collection(SECTIONS).get().await()
        val listCount = sections.documents.mapNotNull { (it.get("sortorder") as? Number)?.toInt() }.toSet().size
        val title = sheetSnap.getString("title") ?: docName
        val summary = "'$docName' OK (fileFormat 13): title='$title', $listCount lists, ${sections.size()} questions"
        Timber.i("UploadAdmin: readFormat13 -> $summary")
        Result.success(summary)
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: readFormat13 '$docName' failed")
        Result.failure(e)
    }

    /** Recursively converts a JSONObject into a Firestore-storable Map (nested objects/arrays kept). */
    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = HashMap<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = jsonToStorable(obj.get(k))
        }
        return map
    }

    private fun jsonToStorable(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> (0 until value.length()).map { jsonToStorable(value.get(it)) }
        else -> value // String / Boolean / Int / Long / Double
    }

    /** Deletes every doc in a single (flat) subcollection under [sheetRef] (batched). */
    private suspend fun clearSubcollection(sheetRef: DocumentReference, name: String) {
        val docs = sheetRef.collection(name).get().await()
        var batch = firestore.batch()
        var ops = 0
        for (d in docs.documents) {
            batch.delete(d.reference)
            if (++ops >= BATCH_LIMIT) { batch.commit().await(); batch = firestore.batch(); ops = 0 }
        }
        if (ops > 0) batch.commit().await()
    }

    /** Deletes every category doc and its words subcollection under [sheetRef] (batched). */
    private suspend fun clearCategories(sheetRef: DocumentReference) {
        val categories = sheetRef.collection(CATEGORIES).get().await()
        for (cat in categories.documents) {
            val words = cat.reference.collection(WORDS).get().await()
            var batch = firestore.batch()
            var ops = 0
            for (word in words.documents) {
                batch.delete(word.reference)
                if (++ops >= BATCH_LIMIT) { batch.commit().await(); batch = firestore.batch(); ops = 0 }
            }
            if (ops > 0) batch.commit().await()
            cat.reference.delete().await()
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun jsonStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.optString(it, "") }
    }

    /** Renders whatever type `uploadDate` is stored as into a readable string. */
    private fun formatUploadDate(raw: Any?): String? = when (raw) {
        null -> null
        is Timestamp -> DateFormat.getDateTimeInstance().format(raw.toDate())
        is Date -> DateFormat.getDateTimeInstance().format(raw)
        is Number -> {
            // fileFormat 0 stores uploadDate as epoch SECONDS (Double); older data may be millis.
            val millis = if (raw.toLong() < 100_000_000_000L) raw.toLong() * 1000 else raw.toLong()
            DateFormat.getDateTimeInstance().format(Date(millis))
        }
        else -> raw.toString()
    }

    companion object {
        // Firestore path: /global/exam_sheets/sheets/<sheetName>/categories/<cat>/words/<word>
        private const val GLOBAL = "global"
        private const val EXAM_SHEETS = "exam_sheets"
        private const val SHEETS = "sheets"
        private const val CATEGORIES = "categories"
        private const val ENTRIES = "entries"
        private const val POOL = "pool"
        private const val CURRENT = "current"
        private const val WORDS = "words"
        private const val WORDS_AND_SENTENCES = "wordsAndSentences"
        private const val TABS = "tabs"
        private const val SECTIONS = "sections"
        private const val FIELD_FILE_FORMAT = "fileformat"
        private const val FIELD_UPLOAD_DATE = "uploadDate"
        private const val GERMAN_A1_VOCAB = "GermanA1Vocab"
        private const val DAILY_WORD_DICTIONARY = "DailyWordDictionary"
        private const val BATCH_LIMIT = 400 // Firestore hard limit is 500 ops per batch
    }
}
