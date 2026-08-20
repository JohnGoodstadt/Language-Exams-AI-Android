package com.goodstadt.john.language.exams.packages.UploadJson

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/**
 * DEBUG + `de` ONLY. This file lives in `src/deDebug`, so it is compiled solely into the German debug
 * variant and is never part of a release build or any other flavour.
 *
 * Administers the German Firestore quiz-content project. The default [FirebaseFirestore] instance
 * points at whichever Firebase project the `de` flavour's google-services.json configures (the
 * separate German project).
 */
class FirestoreUploadAdminRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : UploadAdminRepository {

    override suspend fun readUploadDate(): Result<String?> = try {
        // /global/exam_sheets/sheets/GermanA1Vocab
        val snapshot = firestore
            .collection("global").document("exam_sheets")
            .collection("sheets").document("GermanA1Vocab")
            .get()
            .await()

        if (!snapshot.exists()) {
            Timber.w("UploadAdmin: /global/exam_sheets/sheets/GermanA1Vocab does not exist")
            Result.success(null)
        } else {
            val raw = snapshot.get("uploadDate")
            Timber.i("UploadAdmin: uploadDate raw=$raw (${raw?.javaClass?.simpleName})")
            Result.success(formatUploadDate(raw))
        }
    } catch (e: Exception) {
        Timber.e(e, "UploadAdmin: failed to read uploadDate")
        Result.failure(e)
    }

    /** Renders whatever type `uploadDate` is stored as into a readable string. */
    private fun formatUploadDate(raw: Any?): String? = when (raw) {
        null -> null
        is Timestamp -> DateFormat.getDateTimeInstance().format(raw.toDate())
        is Date -> DateFormat.getDateTimeInstance().format(raw)
        is Number -> {
            // Epoch could be seconds or millis - show the raw value plus a best-guess date.
            val millis = if (raw.toLong() < 100_000_000_000L) raw.toLong() * 1000 else raw.toLong()
            "$raw (${DateFormat.getDateTimeInstance().format(Date(millis))})"
        }
        else -> raw.toString()
    }
}
