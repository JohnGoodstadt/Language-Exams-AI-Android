package com.goodstadt.john.language.exams.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// --- Constants (Easy to move to Remote Config later) ---
object TtsCreditSystemConfig {
    const val FREE_TIER_CREDITS = 100
    const val REFILL_AMOUNT = 100
}

// --- Data class to represent the user's TTS credit state ---
data class TtsUserCredits(
    val current: Int,
    val total: Int
)

@Singleton
class TtsCreditsRepository @Inject constructor(
    // It depends only on your existing repository for storage
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository: TTSStatsRepository,
) {
    /**
     * A Flow that exposes the current TTS credits from DataStore.
     * ViewModels will collect this to keep the UI in sync.
     */
    val ttsCreditsFlow: Flow<TtsUserCredits> = userPreferencesRepository.ttsUserCreditsFlow

    /**
     * Checks if the user has TTS credits. If this is their first use,
     * it initializes their account with the default free tier credits.
     * This should be called once when the app starts or a ViewModel is created.
     */
    suspend fun setupFreeTierIfNeeded() {
        // .first() gets the current value from the flow
        val currentTotal = userPreferencesRepository.ttsTotalCreditsFlow.first()

        // If the total is 0, it means they've never been set up.
        if (currentTotal == 0) {
            Log.d("TtsCreditsRepo", "First use detected. Setting up free TTS credits.")
            userPreferencesRepository.saveTtsCredits(
                current = TtsCreditSystemConfig.FREE_TIER_CREDITS,
                total = TtsCreditSystemConfig.FREE_TIER_CREDITS
            )
        }
    }

    /**
     * Decrements the user's current TTS credit count by 1.
     * If the count reaches zero, it automatically refills the credits.
     */
    suspend fun decrementCredit() {
        val currentCredits = ttsCreditsFlow.first().current


        if (currentCredits > 0) {
//        if (false) {
            // If the user has credits, just decrement.
            userPreferencesRepository.saveTtsCurrentCredits(currentCredits - 1)
            Log.d("TtsCreditsRepo", "TTS Credits remaining:$currentCredits")
            //this will inc not update
           // ttsStatsRepository.updateUserTTSCurrentTokenCount(currentCredits)
        } else {
            // User is out of credits. Time to handle the "do something" logic.
            Log.d("TtsCreditsRepo", "User out of TTS credits. Applying automatic refill.")
            ttsStatsRepository.incUserTTSTotalTokenCount(TtsCreditSystemConfig.REFILL_AMOUNT)
            // --- THIS IS THE PLACEHOLDER FOR YOUR FUTURE LOGIC ---
            // For now, we just top up with another 100 credits.
            // Later, you could show a dialog, an ad, or an IAP flow here.

            val currentTotal = userPreferencesRepository.ttsTotalCreditsFlow.first()
            userPreferencesRepository.saveTtsCredits(
                current = TtsCreditSystemConfig.REFILL_AMOUNT,
                total = currentTotal + TtsCreditSystemConfig.REFILL_AMOUNT
            )
        }
    }
}