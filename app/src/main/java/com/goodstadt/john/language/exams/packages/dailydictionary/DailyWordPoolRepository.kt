package com.goodstadt.john.language.exams.packages.dailydictionary

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import java.io.File

class DailyWordPoolRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val context: Context
) {
    private val sheetRoot = "global/exam_sheets/sheets/DailyWordDictionary"

    private val poolDocRef
        get() = db.document("$sheetRoot/pool/current")

    private fun entryDocRef(entryId: String) =
        db.document("$sheetRoot/entries/$entryId")

    private val gson = Gson()

    suspend fun fetchPool(): PoolDoc {
        val snap = poolDocRef.get().await()
        return snap.toObject(PoolDoc::class.java)
            ?: throw IllegalStateException("pool/current missing or invalid")
    }

    suspend fun fetchEntry(entryId: String): DictionaryEntry {
        // cache-first
        loadCachedEntry(entryId)?.let { return it }

        val snap = entryDocRef(entryId).get().await()
        val entry = snap.toObject(DictionaryEntry::class.java)
            ?: throw IllegalStateException("Entry missing: $entryId")

        saveCachedEntry(entry)
        return entry
    }

    // --- Simple JSON cache (optional) ---
    private fun cacheDir(): File {
        val dir = File(context.filesDir, "DailyWordDictionaryCache/entries")
        dir.mkdirs()
        return dir
    }

    private fun cacheFile(entryId: String) = File(cacheDir(), "$entryId.json")

//    private fun saveCachedEntryOld(entry: DictionaryEntry) {
//        try {
//            val json = kotlinx.serialization.json.Json.encodeToString(
//                DictionaryEntry.serializer(),
//                entry
//            )
//            cacheFile(entry.entryId).writeText(json)
//        } catch (_: Exception) { }
//    }
//
//    private fun loadCachedEntryOld(entryId: String): DictionaryEntry? {
//        return try {
//            val f = cacheFile(entryId)
//            if (!f.exists()) null
//            else kotlinx.serialization.json.Json.decodeFromString(
//                DictionaryEntry.serializer(),
//                f.readText()
//            )
//        } catch (_: Exception) {
//            null
//        }
//    }


    private fun saveCachedEntry(entry: DictionaryEntry) {
        try {
            val json = gson.toJson(entry)
            cacheFile(entry.entryId).writeText(json)
        } catch (_: Exception) { }
    }

    private fun loadCachedEntry(entryId: String): DictionaryEntry? {
        return try {
            val f = cacheFile(entryId)
            if (!f.exists()) null else gson.fromJson(f.readText(), DictionaryEntry::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
