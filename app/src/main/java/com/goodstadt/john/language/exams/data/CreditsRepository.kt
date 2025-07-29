package com.goodstadt.john.language.exams.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// --- The data classes and config object remain the same ---
object CreditSystemConfig {
    const val FREE_TIER_CREDITS = 20
    const val WAIT_PERIOD_HOURS = 1
}

data class UserCredits(
    val current: Int = 0,
    val total: Int = 0,
    val lastRefillTimestamp: Long = 0L
)

@Singleton
class CreditsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val userId: String?
        get() = auth.currentUser?.uid

    // --- NEW: A private, in-memory state holder for the credits ---
    // It's nullable to represent the "not yet loaded" state.
    private val _userCredits = MutableStateFlow<UserCredits?>(null)

    /**
     * A Flow that exposes the in-memory user credits.
     * The ViewModel will collect this. It filters out the initial null value.
     */
    val userCreditsFlow: Flow<UserCredits> = _userCredits.asStateFlow().filterNotNull()

    /**
     * A private data class that exactly matches the field names in the Firestore document.
     */
    private data class UserCreditsFirestore(
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
     * Performs a ONE-TIME fetch from Firestore to initialize the in-memory state.
     * If the user is new, it creates their free tier credits in Firestore and in memory.
     * This should be called once when the relevant ViewModel is initialized.
     */
    suspend fun initialFetchAndSetupCredits(): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception("User not logged in"))

        return try {
            val userDocRef = firestore.collection("users").document(uid)
            val doc = userDocRef.get().await() // One-time fetch

            if (!doc.exists() || !doc.contains("llmCurrentCredit")) {
                Log.d("CreditsRepository", "User document missing credit fields. Setting up free tier.")
                val freeTierCredits = UserCredits(
                    current = CreditSystemConfig.FREE_TIER_CREDITS,
                    total = CreditSystemConfig.FREE_TIER_CREDITS
                )

                val freeTierData = mapOf(
                    "llmCurrentCredit" to freeTierCredits.current,
                    "llmTotalCredit" to freeTierCredits.total,
                    "llmLastRefillTimestamp" to 0L
                )

                userDocRef.set(freeTierData, SetOptions.merge()).await()
                // Update the local, in-memory state
                _userCredits.value = freeTierCredits

            } else {
                Log.d("CreditsRepository", "User already has credit fields. Loading into memory.")
                val existingCredits = doc.toObject(UserCreditsFirestore::class.java)!!.toUserCredits()
                // Update the local, in-memory state
                _userCredits.value = existingCredits
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decrements the credit count. Updates Firestore first, then updates the local in-memory state on success.
     */
    suspend fun decrementCredit(promptTokens:Int,completionTokens :Int,totalTokensUsed:Int): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception("User not logged in"))

        return try {
            val userDocRef = firestore.collection("users").document(uid)
            userDocRef.update("llmCurrentCredit", FieldValue.increment(-1),
                "llmPromptTokens",FieldValue.increment(promptTokens.toLong()),
                "llmTotalTokens",FieldValue.increment(completionTokens.toLong())).await()

            // On success, manually update our in-memory state
            _userCredits.update { it?.copy(current = it.current - 1) }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Adds purchased credits. Updates Firestore first, then updates the local in-memory state on success.
     */
    suspend fun purchaseCredits(amount: Int): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception("User not logged in"))
        return try {
            val userDocRef = firestore.collection("users").document(uid)
            userDocRef.update(
                "llmCurrentCredit", FieldValue.increment(amount.toLong()),
                "llmTotalCredit", FieldValue.increment(amount.toLong())
            ).await()

            // On success, manually update our in-memory state
            _userCredits.update { it?.copy(
                current = it.current + amount,
                total = it.total + amount
            )}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Applies a timed refill. Updates Firestore first, then updates the local in-memory state on success.
     */
    suspend fun applyTimedRefill(): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception("User not logged in"))
        return try {
            val userDocRef = firestore.collection("users").document(uid)
            val timestamp = System.currentTimeMillis()
            val updateData = mapOf(
                "llmCurrentCredit" to CreditSystemConfig.FREE_TIER_CREDITS,
                "llmTotalCredit" to CreditSystemConfig.FREE_TIER_CREDITS,
                "llmLastRefillTimestamp" to timestamp
            )
            userDocRef.update(updateData).await()

            // On success, manually update our in-memory state
            _userCredits.update { it?.copy(
                current = CreditSystemConfig.FREE_TIER_CREDITS,
                total = CreditSystemConfig.FREE_TIER_CREDITS,
                lastRefillTimestamp = timestamp
            )}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}