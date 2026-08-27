package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.rateLimitDailyViewCount
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.rateLimitHourlyViewCount
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPBuyCancelledCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPDailyHitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPGoUnlimitedOnClickCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPHourlyHitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPSheetDisplayedCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statIAPWaitForResetOnClickCount
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PremiumUpgradeSheetUiState(
    val hourlyLimit: Int = 0,
    val dailyLimit: Int = 0
)

@HiltViewModel
class PremiumUpgradeSheetViewModel  @Inject constructor(
    private val ttsStatsRepository : TTSStatsRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val billingRepository: BillingRepository
): ViewModel(), DefaultLifecycleObserver {

    private val _uiState = MutableStateFlow(PremiumUpgradeSheetUiState())
    val uiState = _uiState.asStateFlow()
    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    val priceFlow: StateFlow<String> = billingRepository.productPrice
        .map { it ?: "Unknown Price" } // Fallback if null (or use a loading spinner)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

//    val productDetails = billingRepository.productDetails

    init {
        billingRepository.startConnection()
        updateRateLimiterState()
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statIAPSheetDisplayedCount)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER,statIAPSheetDisplayedCount)

    }


    fun buyPremiumButtonPressed(activity: Activity) {
        viewModelScope.launch { billingRepository.launchPurchase(activity) }
    }

    fun incStatForDaily() {
        ttsStatsRepository.incUserStatCount(rateLimitDailyViewCount)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statIAPDailyHitCount)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statIAPDailyHitCount)
    }
    fun incStatForHourly() {
        ttsStatsRepository.incUserStatCount(rateLimitHourlyViewCount)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statIAPHourlyHitCount)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statIAPHourlyHitCount)
    }
    fun incIAPCancel() {
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statIAPBuyCancelledCount)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statIAPBuyCancelledCount)
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

    fun flushStats() {
        if (ttsStatsRepository.isMarchOrApril2026()) {
            viewModelScope.launch {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }
        }
    }

    fun incGoUnlimited() {
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statIAPGoUnlimitedOnClickCount)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statIAPGoUnlimitedOnClickCount)
    }
    fun incWaitForReset() {
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statIAPWaitForResetOnClickCount)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statIAPWaitForResetOnClickCount)
    }



}