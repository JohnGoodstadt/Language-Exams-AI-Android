package com.goodstadt.john.language.exams.packages.ReferencePronouns

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await

interface PronounsReferenceRepository {
    suspend fun load(): PronounsReferenceSheet
}

data class PronounsReferenceSource(
    val languageTag: String,
    val firestoreSheetId: String,
    val assetFileName: String,
)

object PronounsReferenceSources {
    val DE = PronounsReferenceSource(
        languageTag = "de",
        firestoreSheetId = "GermanReferencePronouns",
        // Assets are opened relative to the assets root, so include the folder (matches the bundled file).
        assetFileName = "Quizzes/Reference/GermanReferencePronouns.json",
    )

    val EN = PronounsReferenceSource(
        languageTag = "en",
        firestoreSheetId = "EnglishReferencePronouns",
        assetFileName = "Quizzes/Reference/EnglishReferencePronouns.json",
    )

    fun forLanguageTag(languageTag: String): PronounsReferenceSource {
        return when (languageTag.substringBefore('-').lowercase()) {
            "de" -> DE
            "en" -> EN
            else -> error("Unsupported pronoun-reference language: $languageTag")
        }
    }
}

/**
 * Loads the configured Firestore sheet first and falls back to its bundled
 * asset when the remote document is unavailable.
 */
class FirestoreFirstPronounsReferenceRepository(
    context: Context,
    private val source: PronounsReferenceSource,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val gson: Gson = Gson(),
) : PronounsReferenceRepository {

    private val appContext = context.applicationContext

    override suspend fun load(): PronounsReferenceSheet {
        return runCatching { loadFromFirestore() }
            .getOrElse { loadFromAssets() }
            .also(::validate)
    }

    private suspend fun loadFromFirestore(): PronounsReferenceSheet {
        val snapshot = firestore
            .collection("global")
            .document("exam_sheets")
            .collection("sheets")
            .document(source.firestoreSheetId)
            .get()
            .await()

        check(snapshot.exists()) {
            "Firestore sheet ${source.firestoreSheetId} was not found"
        }

        return checkNotNull(snapshot.toObject(PronounsReferenceSheet::class.java)) {
            "Firestore sheet could not be decoded"
        }
    }

    private fun loadFromAssets(): PronounsReferenceSheet {
        return appContext.assets
            .open(source.assetFileName)
            .bufferedReader()
            .use { reader -> gson.fromJson(reader, PronounsReferenceSheet::class.java) }
    }

    private fun validate(sheet: PronounsReferenceSheet) {
        require(sheet.fileformat == 5) {
            "Expected fileformat 5 but found ${sheet.fileformat}"
        }
        require(sheet.sheetname == source.firestoreSheetId) {
            "Expected sheetname ${source.firestoreSheetId} but found ${sheet.sheetname}"
        }
        require(sheet.data.singleOrNull() != null) {
            "The reference sheet must contain exactly one data block"
        }
        require(sheet.data.single().locale == source.languageTag) {
            "Expected locale ${source.languageTag} but found ${sheet.data.single().locale}"
        }
    }
}
