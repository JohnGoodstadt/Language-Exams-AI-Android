package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.rateLimitDailyViewCount
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.rateLimitHourlyViewCount
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPBoughtCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPBuyCancelledCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPDailyHitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPHourlyHitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPSheetDisplayedCount
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

data class RateLimitUiState(
    // ... your existing properties: isLoading, categories, etc.

    // --- ADD NEW RATE LIMITER STATE ---
//    val currentHourlyLimit: Int = 0,
    val hourlyLimit: Int = 0,
//    val callsMadeToday: Int = 0,
    val dailyLimit: Int = 0
)

@HiltViewModel
class RateLimitSheetViewModel  @Inject constructor(
    private val ttsStatsRepository : TTSStatsRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val billingRepository: BillingRepository
): ViewModel() {

    private val _uiState = MutableStateFlow(RateLimitUiState())
    val uiState = _uiState.asStateFlow()

    val productDetails = billingRepository.productDetails

    init {
        updateRateLimiterState()
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statIAPSheetDisplayedCount)

    }
    fun formattedPrice() : String {

        Timber.w("Product Details: ${productDetails.value?.let { "${it.name} - ${it.oneTimePurchaseOfferDetails?.formattedPrice}" } ?: "None"}")

        productDetails.value?.let {
            return it.oneTimePurchaseOfferDetails?.formattedPrice ?: "Unknown Price"
        }

        return "Unknown Price"
    }
    fun incStatForDaily() {
        ttsStatsRepository.incUserStatCount(rateLimitDailyViewCount)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statIAPDailyHitCount)
    }
    fun incStatForHourly() {
        ttsStatsRepository.incUserStatCount(rateLimitHourlyViewCount)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statIAPHourlyHitCount)
    }
    fun currentHourlyTimeLeftToWait() : Long? {
        return rateLimiter.currentHourlyTimeLeftToWait
    }
    private fun updateRateLimiterState() {
        _uiState.update {
            it.copy(
                hourlyLimit = rateLimiter.hourlyLimit,
                dailyLimit = rateLimiter.dailyLimit
            )
        }
    }


    // ✅ NEW: Helper to format time for the UI
    fun getFormattedTimeLeft(isDaily: Boolean): String {
        val secondsRemaining = 0//rateLimiter.timeLeftToWait // Accessing property from SimpleRateLimiter

        if (secondsRemaining <= 0) return "Ready now"

        val hours = secondsRemaining / 3600
        val minutes = (secondsRemaining % 3600) / 60

        return if (isDaily) {
            // Logic for daily reset (usually wait until tomorrow)
            if (hours > 0) "$hours hrs $minutes mins" else "$minutes mins"
        } else {
            // Logic for hourly
            "$minutes mins"
        }
    }
}