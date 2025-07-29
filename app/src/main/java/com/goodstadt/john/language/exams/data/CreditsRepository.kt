package com.goodstadt.john.language.exams.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

// --- Constants (Easy to move to Remote Config later) ---
object CreditSystemConfig {
    const val FREE_TIER_CREDITS = 20
    const val WAIT_PERIOD_HOURS = 1
}

// --- Data class to represent the user's credit state ---
data class UserCredits(
    val current: Int = 0,
    val total: Int = 0,
    val lastRefillTimestamp: Long = 0L // For the wait period
)

@Singleton
class CreditsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val userId: String?
        get() = auth.currentUser?.uid

    // --- Public Flow to observe the user's credits in real-time ---
    val userCreditsFlow: Flow<UserCredits> = firestore.collection("users")
        .document(userId ?: "null_user") // Use a non-existent doc if not logged in
        .snapshots() // This is a real-time listener
        .mapNotNull { snapshot ->
            if (snapshot.exists()) {
                snapshot.toObject(UserCreditsFirestore::class.java)?.toUserCredits()
            } else {
                null // Return null if the user document doesn't exist yet
            }
        }


    // --- Firestore-specific data class for safe mapping ---
    private data class UserCreditsFirestore(
        // These are the new field names that match your Firestore document
        val llmCurrentCredit: Int = 0,
        val llmTotalCredit: Int = 0,
        val llmLastRefillTimestamp: Long = 0L
    ) {
        fun toUserCredits() = UserCredits(
            current = llmCurrentCredit,
            total = llmTotalCredit,
            lastRefillTimestamp = llmLastRefillTimestamp
        )
    }

    /**
     * Decrements the user's credit count by 1.
     * This is the main function called after a successful API call.
     */
    suspend fun decrementCredit(): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception("User not logged in"))
        return try {
            val userDocRef = firestore.collection("users").document(uid)
            // Use the new field name here
            userDocRef.update("llmCurrentCredit", FieldValue.increment(-1)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Adds a specific number of credits to the user's total.
     */
    suspend fun purchaseCredits(amount: Int): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception("User not logged in"))
        return try {
            val userDocRef = firestore.collection("users").document(uid)
            // Use the new field names here
            userDocRef.update(
                "llmCurrentCredit", FieldValue.increment(amount.toLong()),
                "llmTotalCredit", FieldValue.increment(amount.toLong())
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resets the user's credits back to the free tier amount after the wait period.
     */
    suspend fun applyTimedRefill(): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception("User not logged in"))
        return try {
            val userDocRef = firestore.collection("users").document(uid)
            // Use the new field names here
            val updateData = mapOf(
                "llmCurrentCredit" to CreditSystemConfig.FREE_TIER_CREDITS,
                "llmTotalCredit" to CreditSystemConfig.FREE_TIER_CREDITS,
                "llmLastRefillTimestamp" to System.currentTimeMillis()
            )
            userDocRef.update(updateData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    /**
     * Checks if the user's credit document exists in Firestore. If not, it creates it
     * with the default free tier credits and returns that new state. If it already
     * exists, it simply returns the current state.
     *
     * This ensures a user always has their initial credits available on the first check.
     *
     * @return A Result containing the up-to-date UserCredits on success, or an Exception on failure.
     */
    suspend fun setupFreeTierIfNeeded(): Result<UserCredits> {
        val uid = userId ?: return Result.failure(Exception("User not logged in"))

        return try {
            val userDocRef = firestore.collection("users").document(uid)
            val doc = userDocRef.get().await()

            if (!doc.exists() || !doc.contains("llmCurrentCredit")) {
                // --- THIS IS THE CORRECTED LOGIC ---
                // 1. User is new or doesn't have credit fields. Create the free tier data.
                Log.d("CreditsRepository", "User document missing credit fields. Setting up free tier.")
                val freeTierCredits = UserCredits(
                    current = CreditSystemConfig.FREE_TIER_CREDITS,
                    total = CreditSystemConfig.FREE_TIER_CREDITS,
                    lastRefillTimestamp = 0L
                )

                // 2. Create the map to save to Firestore.
                val freeTierData = mapOf(
                    "llmCurrentCredit" to freeTierCredits.current,
                    "llmTotalCredit" to freeTierCredits.total,
                    "llmLastRefillTimestamp" to freeTierCredits.lastRefillTimestamp
                )

                // 3. Save this data to Firestore.
                userDocRef.set(freeTierData, SetOptions.merge()).await()

                // 4. IMPORTANT: Immediately return the new freeTierCredits object.
                //    The app doesn't have to wait for the Firestore listener to get this initial state.
                Result.success(freeTierCredits)

            } else {
                // User already exists and has credits, just return their current state.
                Log.d("CreditsRepository", "User already has credit fields.")
                val existingCredits = doc.toObject(UserCreditsFirestore::class.java)!!.toUserCredits()
                Result.success(existingCredits)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}