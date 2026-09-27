package com.goodstadt.john.language.exams.data // Or wherever your repositories live

import com.google.firebase.firestore.FirebaseFirestore
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

    /**
     * Increments the "play count" for a specific vocabulary word.
     * @param uid The ID of the currently logged-in user.
     * @param wordId The ID of the word that was played.
     */

}