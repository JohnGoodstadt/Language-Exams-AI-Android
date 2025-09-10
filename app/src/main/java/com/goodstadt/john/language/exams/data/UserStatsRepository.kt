package com.goodstadt.john.language.exams.data // Or wherever your repositories live

import com.goodstadt.john.language.exams.models.WordAndSentence
import com.goodstadt.john.language.exams.models.WordHistory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton



@Singleton
class UserStatsRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private companion object fb {
        const val words = "words"
        const val users = "users"
        const val sentenceCount = "sentenceCount"
        const val timestamp = "timestamp"
        const val wordCount = "wordCount"

    }
//    private object fb2 {
//        const val words = "words"
//        const val users = "users"
//    }
    /**
     * Increments the "play count" for a specific vocabulary word.
     * @param uid The ID of the currently logged-in user.
     * @param wordId The ID of the word that was played.
     */
//    suspend fun incrementWordPlayCountObsolete(uid: String, wordId: String): Result<Unit> {
//        return try {
//            // A good data model would be a subcollection under the user
//            val docRef = firestore.collection(fb.users).document(uid)
//                .collection("wordStats").document(wordId)
//
//            // Using FieldValue.increment is efficient and handles offline cases well
//            val updates = mapOf("playCount" to FieldValue.increment(1))
//
//            docRef.set(updates, SetOptions.merge()).await() // .set with merge will create or update
//            Result.success(Unit)
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Result.failure(e)
//        }
//    }
//    fun fsUpdateSentenceHistoryIncCount(
//        wordAndSentence: WordAndSentence,
//        count: Int = 1
//    ) {
//        val auth = FirebaseAuth.getInstance()
//        val firestore = FirebaseFirestore.getInstance()
//
//        val currentUser = auth.currentUser
//            ?: return // User is not authenticated
//
//        val itemRef = firestore.collection(fb.users)
//            .document(currentUser.uid)
//            .collection(fb.words)
//            .document(wordAndSentence.word)
//
//        // Update the Firestore document
//        itemRef.update(
//            mapOf(
//                sentenceCount to FieldValue.increment(count.toLong()),
//                wordCount to FieldValue.increment(count.toLong()),
//                timestamp to Date()
//            )
//        ).addOnFailureListener { exception ->
//            Timber.e("Error updating field: ${exception.message}")
//
//            // Check if the error is due to a missing document (Firestore error code 5)
//            if ((exception as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.NOT_FOUND) {
//                fsCreateWordHistoryDoObsolete(wordAndSentence)
//            }
//        }
//    }
//    fun fsCreateWordHistoryDoObsolete(words: WordAndSentence) {
//        val auth = FirebaseAuth.getInstance()
//        val firestore = FirebaseFirestore.getInstance()
//
//        val uid = auth.currentUser?.uid
//        if (uid == null) {
//            return // User is not authenticated
//        }
//
//        // Create the WordHistory object
//        val wordHistoryDoc = WordHistory(
//            word = words.word,
//            sentence = words.sentence,
//            wordCount = 1,
//            sentenceCount = 1,
//            timestamp = Date()
//        )
//
//        // Save the data to Firestore
//        firestore.collection(fb.users)
//            .document(uid)
//            .collection(fb.words)
//            .document(words.word)
//            .set(wordHistoryDoc)
//            .addOnFailureListener { exception ->
//                Timber.e("Error writing document: ${exception.message}")
//            }
//    }
}