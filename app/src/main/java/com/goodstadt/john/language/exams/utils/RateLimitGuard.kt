package com.goodstadt.john.language.exams.utils

import android.content.Context
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
//import com.goodstadt.john.language.exams.data.repository.UserPreferencesRepository
import com.goodstadt.john.language.exams.utils.AnalyticsHelper
//import com.goodstadt.john.language.exams.utils.SimpleRateLimiter
//import com.goodstadt.john.language.exams.utils.SimpleRateLimiter.FailReason
import com.goodstadt.john.language.exams.utils.calcIsTodayNotAFreePassDay
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// The result of the check
sealed interface RateLimitResult {
    object Allowed : RateLimitResult
    data class Blocked(val reason: SimpleRateLimiter.FailReason) : RateLimitResult
}

@Singleton
class RateLimitGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rateLimiter: SimpleRateLimiter,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    /**
     * Checks if the user is allowed to proceed.
     * Side Effects: Logs to Analytics and Timber if blocked.
     */
    suspend fun checkIsAllowed(isPremiumUser: Boolean): RateLimitResult {

        if (isPremiumUser) { //quick exit
            return RateLimitResult.Allowed
        }

        val todayIsNotAFreePassDay = calcIsTodayNotAFreePassDay(userPreferencesRepository)

        // 1. If Premium or Free Day, allow immediately
        if (!todayIsNotAFreePassDay) {
            return RateLimitResult.Allowed
        }

        // 2. Check the Rate Limiter
        if (rateLimiter.doIForbidCall()) {
            val failType = rateLimiter.canMakeCallWithResult()

            Timber.v("Rate Limit Hit: API=${failType.canICallAPI}, Reason=${failType.failReason}, Wait=${failType.timeLeftToWait}")

            // 3. Log Analytics (Side Effect)
            val limitType = if (failType.failReason == SimpleRateLimiter.FailReason.DAILY) "daily" else "hourly"
            // Assuming constants 150/50 exist, or hardcode them for logging context
            val limitCount = if (limitType == "daily") 150 else 50

            AnalyticsHelper.logRateLimitHit(
                context = context,
                limitType = limitType,
                currentCount = limitCount
            )

            // 4. Return Blocked status so ViewModel can update UI
            return RateLimitResult.Blocked(failType.failReason ?: SimpleRateLimiter.FailReason.HOURLY)
        }

        return RateLimitResult.Allowed
    }
}