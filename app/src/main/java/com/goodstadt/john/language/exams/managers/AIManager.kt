package com.goodstadt.john.language.exams.managers

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class AIManager {

    // 1. Initialize pointing to the London region
    private val functions = Firebase.functions("europe-west2")

    suspend fun getTeacherParagraph(words: String, modelName: String): String? {

        // 2. Security Check: Ensure user is logged in
        // Your function will return 401 Unauthenticated if this is missing
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Timber.e("❌ User not logged in. Cannot call proxy.")
            return null
        }

        // 3. Prepare the data payload
        val data = hashMapOf(
            "words" to words,
            "model" to modelName
        )

        return try {
            // 4. Call the Proxy
            val result = functions
                .getHttpsCallable("callGeminiProxy")
                .call(data)
                .await()

            // 5. Parse the result (Cast from Any?)
            val responseMap = result.data as? Map<*, *>
            val aiText = responseMap?.get("text") as? String
            val usage = responseMap?.get("usage") as? Map<*, *>

            val totalTokens = usage?.get("totalTokens") ?: 0
            Timber.i("✅ AI success! Tokens used: $totalTokens")

            aiText
        } catch (e: Exception) {
            Timber.e(e, "❌ Proxy Call Failed")
            null
        }
    }
}