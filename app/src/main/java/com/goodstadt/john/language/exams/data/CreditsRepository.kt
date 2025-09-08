package com.goodstadt.john.language.exams.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// --- The data classes and config object remain the same ---


object CreditSystemConfig {
    const val FREE_TIER_CREDITS = 4//20
    const val BOUGHT_TIER_CREDITS = 4 //10
    const val WAIT_PERIOD_MINUTES = 3L //20
}

data class UserCredits(
    val current: Int = 0,
    val total: Int = 0,
//    val lastRefillTimestamp: Long = 0L,
    val llmNextCreditRefill: Timestamp = Timestamp(0,0)
)

@Singleton
class CreditsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private val usersCollection = "users"
    private val llmCurrentCreditField = "llmCurrentCredit"
    private val llmTotalCreditField = "llmTotalCredit"
    private val llmNextCreditRefillField = "llmNextCreditRefill"

    private val userId: String?
        get() = auth.currentUser?.uid

    // --- NEW: A private, in-memory state holder for the credits ---
    // It's nullable to represent the "not yet loaded" state.
    private val _userCredits = MutableStateFlow<UserCredits?>(null)
    private val _freeTierCredits = MutableStateFlow(CreditSystemConfig.FREE_TIER_CREDITS)
    val freeTierCredits: StateFlow<Int> = _freeTierCredits.asStateFlow()
    private val _nextCreditRefillDate = MutableStateFlow<Date?>(null)
    val nextCreditRefillDate: StateFlow<Date?> = _nextCreditRefillDate.asStateFlow()
    private val _isInWaitPeriod = MutableStateFlow(false)
    val isInWaitPeriod: StateFlow<Boolean> = _isInWaitPeriod.asStateFlow()

    /**
     * A Flow that exposes the in-memory user credits.
     * The ViewModel will collect this. It filters out the initial null value.
     */
    val userCreditsFlow: Flow<UserCredits> = _userCredits.asStateFlow().filterNotNull()

    /**
     * A private data class that exactly matches the field names in the Firestore document.
     */
    @IgnoreExtraProperties
    private data class UserCreditsFirestore(
        val llmCurrentCredit: Int = 0,
        val llmTotalCredit: Int = 0,
//        val llmLastRefillTimestamp: Long = 0L,
        val llmNextCreditRefill: Timestamp =  Timestamp(0,0)
    ) {
        fun toUserCredits() = UserCredits(
            current = llmCurrentCredit,
            total = llmTotalCredit,
//            lastRefillTimestamp = llmLastRefillTimestamp,
            llmNextCreditRefill = llmNextCreditRefill
        )
    }
    /** Loads user credit info — can be called from viewModelScope.launch {} in UI */
    suspend fun loadCredits() {
        val userId = auth.currentUser?.uid ?: return

        try {
            val doc = firestore.collection(usersCollection)
                .document(userId)
                .get()
                .await() // This is suspendable; needs 'kotlinx-coroutines-play-services'

            val data = doc.data ?: emptyMap<String, Any>()
            val credits = (data[llmCurrentCreditField] as? Long)?.toInt() ?: CreditSystemConfig.FREE_TIER_CREDITS
            val refillTimestamp = data[llmNextCreditRefillField] as? Timestamp

            _freeTierCredits.value = credits

            if (refillTimestamp != null) {
                val refillDate = refillTimestamp.toDate()
                _nextCreditRefillDate.value = refillDate
                if (Date().before(refillDate)) {
                    _isInWaitPeriod.value = true
                } else {
                    _isInWaitPeriod.value = false
                    setCredits(CreditSystemConfig.FREE_TIER_CREDITS)
                    clearRefillDateInDB(userId)
                }
            } else {
                _isInWaitPeriod.value = false
                _nextCreditRefillDate.value = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

            if (!doc.exists() || !doc.contains(llmCurrentCreditField)) {
                Timber.d("User document missing credit fields. Setting up free tier.")
                val freeTierCredits = UserCredits(
                    current = CreditSystemConfig.FREE_TIER_CREDITS,
                    total = CreditSystemConfig.FREE_TIER_CREDITS
                )

                val freeTierData = mapOf(
                    llmCurrentCreditField to freeTierCredits.current,
                    "llmTotalCredit" to freeTierCredits.total
                )

                userDocRef.set(freeTierData, SetOptions.merge()).await()
                // Update the local, in-memory state
                _userCredits.value = freeTierCredits

            } else {
                Timber.d("User already has credit fields. Loading into memory.")
                val existingCredits = doc.toObject(UserCreditsFirestore::class.java)!!.toUserCredits()
                // Update the local, in-memory state
                _userCredits.value = existingCredits
                _nextCreditRefillDate.value = existingCredits.llmNextCreditRefill.toDate()
                Timber.d("existingCredits:$existingCredits")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.localizedMessage?.let { Timber.e(it) }
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
            userDocRef.update(llmCurrentCreditField, FieldValue.increment(-1),
                "llmPromptTokens",FieldValue.increment(promptTokens.toLong()),
                "llmTotalTokens",FieldValue.increment(completionTokens.toLong())).await()

            val newCredit = _userCredits.value?.current?.minus(1) ?: 0

            // On success, manually update our in-memory state
            Timber.v("decrementCredit current credits A: ${newCredit}")
            _userCredits.update { it?.copy(current = newCredit) }

            if (newCredit == 0) {
                startWaitPeriod()
            }

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
//            userDocRef.update(
//                "llmCurrentCredit", FieldValue.increment(amount.toLong()),
//                "llmTotalCredit", FieldValue.increment(amount.toLong())
//            ).await()

            val current = (_userCredits.value?.current ?: 0) + amount
           // val total = (_userCredits.value?.total ?: 0) + amount

            userDocRef.update(
                llmCurrentCreditField,current.toLong(),
                "llmTotalCredit", current.toLong() //total same as current
            ).await()

            // On success, manually update our in-memory state
//            _userCredits.update { it?.copy(
//                current = it.current + amount,
//                total = it.total + amount
//            )}

            _userCredits.update { it?.copy(
                current = current,
                total =  current
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
                llmCurrentCreditField to CreditSystemConfig.FREE_TIER_CREDITS,
                "llmTotalCredit" to CreditSystemConfig.FREE_TIER_CREDITS
//                "llmLastRefillTimestamp" to timestamp
            )
            userDocRef.update(updateData).await()

            // On success, manually update our in-memory state
            _userCredits.update { it?.copy(
                current = CreditSystemConfig.FREE_TIER_CREDITS,
                total = CreditSystemConfig.FREE_TIER_CREDITS
//                lastRefillTimestamp = timestamp,
//                llmNextCreditRefill = timestamp
            )}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun applyRefill(): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception("User not logged in"))

        return try {
            val userDocRef = firestore.collection("users").document(uid)
            val timestamp = System.currentTimeMillis()
            val updateData = mapOf(
                llmCurrentCreditField to CreditSystemConfig.FREE_TIER_CREDITS,
                "llmTotalCredit" to CreditSystemConfig.FREE_TIER_CREDITS
//                "llmLastRefillTimestamp" to timestamp
            )
            userDocRef.update(updateData).await()

            // On success, manually update our in-memory state
            _userCredits.update { it?.copy(
                current = CreditSystemConfig.FREE_TIER_CREDITS,
                total = CreditSystemConfig.FREE_TIER_CREDITS,
                llmNextCreditRefill = Timestamp(0,0)
            )}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // ... inside CreditsRepository class ...

    /**
     * Checks if the user is eligible for a timed refill (i.e., they are out of credits
     * and the wait period has passed). If eligible, it resets their credits to the free
     * tier amount and updates the last refill timestamp.
     *
     * @return A Result containing 'true' if a refill was successfully applied,
     *         'false' if the user was not eligible, or an Exception on failure.
     */

    //from swift
    suspend fun setCredits(newCredits: Int) {
        _freeTierCredits.value = newCredits
        _userCredits.value = UserCredits( //keep in line with vieModel
            current = CreditSystemConfig.FREE_TIER_CREDITS,
            total = CreditSystemConfig.FREE_TIER_CREDITS
        )
        saveCredits(newCredits)
    }
    private suspend fun saveCredits(credits: Int) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection(usersCollection).document(userId)
            .update(
                mapOf(
                    llmCurrentCreditField to credits,
                    llmTotalCreditField to FieldValue.increment(credits.toLong())
                )
            )
            .await()
    }
    /** Deducts a credit or starts wait if none left */
    suspend fun  deductCredit() {
        val currentCredits = _freeTierCredits.value
        if (currentCredits <= 0) return
        val newCredits = currentCredits - 1
        _freeTierCredits.value = newCredits
        saveCreditsOnly(newCredits)
        if (newCredits == 0) {
            startWaitPeriod()
        }
    }
    private suspend fun clearRefillDateInDB(userId: String) {
        firestore.collection(usersCollection).document(userId)
            .update(llmNextCreditRefillField, FieldValue.delete())
            .await()
    }
    private suspend fun saveCreditsOnly(credits: Int) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection(usersCollection)
            .document(userId)
            .set(mapOf(llmCurrentCreditField to credits), SetOptions.merge())
            .await()
    }
    suspend fun startWaitPeriod() {

        val userId = auth.currentUser?.uid ?: return
        val targetDate = Date(System.currentTimeMillis() + CreditSystemConfig.WAIT_PERIOD_MINUTES * 60 * 1000)
        Timber.d("startWaitPeriod().targetDate : $targetDate")
        _nextCreditRefillDate.value = targetDate
        _isInWaitPeriod.value = true

       // val FRED = 999
        firestore.collection(usersCollection).document(userId)
            .set(mapOf(llmNextCreditRefillField to Timestamp(targetDate)), SetOptions.merge())
            .await()
    }
    fun setNextCreditRefillDate(date:Date){
       _nextCreditRefillDate.value = date
    }
    suspend fun clearWaitPeriod() {
        val userId = auth.currentUser?.uid ?: return
        _isInWaitPeriod.value = false
        _nextCreditRefillDate.value = null
        setCredits(CreditSystemConfig.FREE_TIER_CREDITS)

        firestore.collection(usersCollection).document(userId)
            .update(llmNextCreditRefillField, FieldValue.delete())
            .await()
    }
}