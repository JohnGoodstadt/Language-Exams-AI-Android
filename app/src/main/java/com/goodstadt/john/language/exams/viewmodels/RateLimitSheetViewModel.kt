package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.rateLimitDailyViewCount
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.rateLimitHourlyViewCount
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
): ViewModel() {

    private val _uiState = MutableStateFlow(RateLimitUiState())
    val uiState = _uiState.asStateFlow()

    init {
        updateRateLimiterState()
    }

    fun incStatForDaily() {
        ttsStatsRepository.incUserStatCount(rateLimitDailyViewCount)
    }
    fun incStatForHourly() {
        ttsStatsRepository.incUserStatCount(rateLimitHourlyViewCount)

    }
    fun currentHourlyTimeLeftToWait() : Long? {
        return rateLimiter.currentHourlyTimeLeftToWait
    }
    private fun updateRateLimiterState() {
        _uiState.update {
            it.copy(
//                callsMadeThisHour = rateLimiter.currentHourlyCount,
                hourlyLimit = rateLimiter.hourlyLimit,
//                callsMadeToday = rateLimiter.callsMadeToday,
                dailyLimit = rateLimiter.dailyLimit
            )
        }
    }



}