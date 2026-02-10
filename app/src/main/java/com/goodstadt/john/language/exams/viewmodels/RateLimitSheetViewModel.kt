package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
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
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    val priceFlow: StateFlow<String> = billingRepository.productPrice
        .map { it ?: "Unknown Price" } // Fallback if null (or use a loading spinner)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val productDetails = billingRepository.productDetails

    init {
        billingRepository.startConnection()
        updateRateLimiterState()
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statIAPSheetDisplayedCount)

    }


    fun buyPremiumButtonPressed(activity: Activity) {
        viewModelScope.launch { billingRepository.launchPurchase(activity) }
    }

//    fun formattedPrice() : String {
//
//        Timber.i("Product Details: ${productDetails.value}" )
//
//        productDetails.value?.let {
//            return it.oneTimePurchaseOfferDetails?.formattedPrice ?: "Unknown Price."
//        }
//
//        Timber.wtf("Product Details: is Unknown, So no formatted Price! RateLimitSheetViewModel.formattedPrice()")
//        return "Unknown Price"
//    }
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