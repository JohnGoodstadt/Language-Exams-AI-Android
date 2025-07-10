package com.goodstadt.john.language.exams.data // Or wherever your repositories live

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    /**
     * Increments the "play count" for a specific vocabulary word.
     * @param uid The ID of the currently logged-in user.
     * @param wordId The ID of the word that was played.
     */
    suspend fun incrementWordPlayCount(uid: String, wordId: String): Result<Unit> {
        return try {
            // A good data model would be a subcollection under the user
            val docRef = firestore.collection(fb.users).document(uid)
                .collection("wordStats").document(wordId)

            // Using FieldValue.increment is efficient and handles offline cases well
            val updates = mapOf("playCount" to FieldValue.increment(1))

            docRef.set(updates, SetOptions.merge()).await() // .set with merge will create or update
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Records that a user has completed an exam.
     */
//    suspend fun recordExamCompletion(uid: String, examId: String, score: Int): Result<Unit> {
//        // Your Firestore logic to save exam results...
//    }
}