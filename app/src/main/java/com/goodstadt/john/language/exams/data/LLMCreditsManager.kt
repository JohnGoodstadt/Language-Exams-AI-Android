package com.goodstadt.john.language.exams.data

import androidx.lifecycle.ViewModel
import com.goodstadt.john.language.exams.data.CreditSystemConfig.FREE_TIER_CREDITS
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val FREE_TIER_CREDITS = 4
private const val WAIT_PERIOD_MINUTES_OBSOLETE = 1L

@HiltViewModel
class LLMCreditsManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _freeTierCredits = MutableStateFlow(FREE_TIER_CREDITS)
    val freeTierCredits: StateFlow<Int> = _freeTierCredits.asStateFlow()

    private val _isInWaitPeriod = MutableStateFlow(false)
    val isInWaitPeriod: StateFlow<Boolean> = _isInWaitPeriod.asStateFlow()

    private val _nextCreditRefillDate = MutableStateFlow<Date?>(null)
    val nextCreditRefillDate: StateFlow<Date?> = _nextCreditRefillDate.asStateFlow()

    private val usersCollection = "users"
    private val llmCurrentCreditField = "llmCurrentCredit"
    private val llmTotalCreditField = "llmTotalCredit"
    private val llmNextCreditRefillField = "LLMNextCreditRefill"

    /** Loads user credit info — can be called from viewModelScope.launch {} in UI */
    suspend fun loadCredits() {
        val userId = auth.currentUser?.uid ?: return

        try {
            val doc = firestore.collection(usersCollection)
                .document(userId)
                .get()
                .await() // This is suspendable; needs 'kotlinx-coroutines-play-services'

            val data = doc.data ?: emptyMap<String, Any>()
            val credits = (data[llmCurrentCreditField] as? Long)?.toInt() ?: FREE_TIER_CREDITS
            val refillTimestamp = data[llmNextCreditRefillField] as? Timestamp

            _freeTierCredits.value = credits

            if (refillTimestamp != null) {
                val refillDate = refillTimestamp.toDate()
                _nextCreditRefillDate.value = refillDate
                if (Date().before(refillDate)) {
                    _isInWaitPeriod.value = true
                } else {
                    _isInWaitPeriod.value = false
                    setCredits(FREE_TIER_CREDITS)
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

    /** Sets the available credits and persists to DB */
    suspend fun setCredits(newCredits: Int) {
        _freeTierCredits.value = newCredits
        saveCreditsAndIncrementTotal(newCredits)
    }

    /** Deducts a credit or starts wait if none left */
    suspend fun deductCredit() {
        val currentCredits = _freeTierCredits.value
        if (currentCredits <= 0) return
        val newCredits = currentCredits - 1
        _freeTierCredits.value = newCredits
        saveCreditsOnly(newCredits)
        if (newCredits == 0) {
            startWaitPeriod()
        }
    }

    private suspend fun saveCreditsOnly(credits: Int) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection(usersCollection)
            .document(userId)
            .set(mapOf(llmCurrentCreditField to credits), SetOptions.merge())
            .await()
    }

    private suspend fun saveCreditsAndIncrementTotal(credits: Int) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection(usersCollection).document(userId)
            .update(
                mapOf(
                    llmCurrentCreditField to credits,
                    llmTotalCreditField to FieldValue.increment(1L)
                )
            )
            .await()
    }

    private suspend fun clearRefillDateInDB(userId: String) {
        firestore.collection(usersCollection).document(userId)
            .update(llmNextCreditRefillField, FieldValue.delete())
            .await()
    }

    suspend fun startWaitPeriod() {
        val userId = auth.currentUser?.uid ?: return
        val targetDate = Date(System.currentTimeMillis() + WAIT_PERIOD_MINUTES_OBSOLETE * 60 * 1000)

        _nextCreditRefillDate.value = targetDate
        _isInWaitPeriod.value = true

        firestore.collection(usersCollection).document(userId)
            .set(mapOf(llmNextCreditRefillField to Timestamp(targetDate)), SetOptions.merge())
            .await()
    }

    suspend fun clearWaitPeriod() {
        val userId = auth.currentUser?.uid ?: return
        _isInWaitPeriod.value = false
        _nextCreditRefillDate.value = null
        setCredits(FREE_TIER_CREDITS)

        firestore.collection(usersCollection).document(userId)
            .update(llmNextCreditRefillField, FieldValue.delete())
            .await()
    }

    fun secondsRemaining(now: Date = Date()): Int {
        val target = _nextCreditRefillDate.value ?: return 0
        return ((target.time - now.time) / 1000).coerceAtLeast(0).toInt()
    }

    fun formattedCountdown(now: Date = Date()): String {
        val seconds = secondsRemaining(now)
        return if (seconds >= 3600) {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            "%02dh %02dm".format(hours, minutes)
        } else {
            val minutes = seconds / 60
            val secs = seconds % 60
            "%02dm %02ds".format(minutes, secs)
        }
    }

    fun formattedRefillTime(): String? {
        val date = _nextCreditRefillDate.value ?: return null
        val formatter = SimpleDateFormat.getTimeInstance(SimpleDateFormat.MEDIUM, Locale.getDefault())
        return formatter.format(date)
    }
}