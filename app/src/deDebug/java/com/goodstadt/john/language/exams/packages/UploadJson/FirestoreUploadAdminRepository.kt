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
        when (val fileFormat = root.optInt(FIELD_FILE_FORMAT, 0)) {
            0, 1 -> uploadFormat0(docName, root) // fileFormat 1 has the SAME structure as 0 -> reuse
            2 -> uploadFormat2(docName, root)
            else -> Result.failure(UnsupportedOperationException("Upload for fileFormat $fileFormat not implemented yet"))
        }
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: upload '$docName' failed")
        Result.failure(e)
    }

    override suspend fun sheetExists(docName: String): Result<Boolean> = try {
        Result.success(sheetDoc(docName).get().await().exists())
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: existence check for '$docName' failed")
        Result.failure(e)
    }

    override suspend fun readSheet(docName: String): Result<String?> = try {
        val snapshot = sheetDoc(docName).get().await()
        if (!snapshot.exists()) {
            // Top-level document simply not present (never uploaded) - NOT an error; caller shows blank.
            Timber.i("UploadAdmin: '$docName' top-level doc not present -> blank")
            Result.success(null)
        } else {
            when (val fileFormat = (snapshot.get(FIELD_FILE_FORMAT) as? Number)?.toInt() ?: 0) {
                0, 1 -> readFormat0(docName, snapshot)
                2 -> readFormat2(docName, snapshot)
                else -> Result.failure(UnsupportedOperationException("Read for fileFormat $fileFormat not implemented yet"))
            }
        }
    } catch (e: Exception) {
        // A real error (permission denied, network, …) reading the top-level document.
        Timber.e(e, "UploadAdmin: read '$docName' failed")
        Result.failure(e)
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
        private const val WORDS = "words"
        private const val WORDS_AND_SENTENCES = "wordsAndSentences"
        private const val FIELD_FILE_FORMAT = "fileformat"
        private const val FIELD_UPLOAD_DATE = "uploadDate"
        private const val GERMAN_A1_VOCAB = "GermanA1Vocab"
        private const val BATCH_LIMIT = 400 // Firestore hard limit is 500 ops per batch
    }
}
