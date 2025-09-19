package com.goodstadt.john.language.exams.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimpleRateLimiter @Inject constructor(
    private val context: Context,
    var hourlyLimit: Int,
    var dailyLimit: Int,
    var schemeID: String,
    var name: String,
    var description: String
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("SimpleRateLimiter", Context.MODE_PRIVATE)

    private var rateLimitingOn = true // Global switch
    enum class FailReason {
        DAILY, HOURLY
    }
    data class CallResult(
        val canICallAPI: Boolean,
        val failReason: FailReason?,
        val timeLeftToWait: Long?
    )
    val printCurrentStatus: String
        get() = """
            +++++++++++++++++++++
            +name: $name
            +$description
            +rateLimitingOn?: $rateLimitingOn
            +hourly Limit: $hourlyLimit
            +daily Limit: $dailyLimit
            +hourly Counts: ${hourlyCount}
            +daily Counts: ${dailyCount}
            +++++++++++++++++++++
        """.trimIndent()

    fun printableStatus():String {
        return  """
            +name: $name
            +$description
            +rateLimitingOn: $rateLimitingOn
            +hourly Limit: $hourlyLimit
            +daily Limit: $dailyLimit
            +hourly Counts: ${hourlyCount}
            +daily Counts: ${dailyCount}
        """.trimIndent()
    }
    val currentSchemeName: String
        get() = name

    val currentDailyLimit: Int
        get() = dailyLimit

    val currentHourlyLimit: Int
        get() = hourlyLimit

    val currentDailyCount: Int
        get() = dailyCount

    val currentHourlyCount: Int
        get() = hourlyCount

    val currentHourlyTimeLeftToWait: Long?
        get() {
            return if (hourlyCount >= hourlyLimit) {
                periodStart?.let { 3600 - ((System.currentTimeMillis() - it) / 1000) }
            } else {
                null
            }
        }

    private var hourlyCount: Int
        get() = preferences.getInt("hourlyCount", 0)
        set(value) = preferences.edit().putInt("hourlyCount", value).apply()

    private var dailyCount: Int
        get() = preferences.getInt("dailyCount$currentDay", 0)
        set(value) = preferences.edit().putInt("dailyCount$currentDay", value).apply()

    private var periodStart: Long?
        get() = preferences.getLong("periodStart", -1L).takeIf { it != -1L }
        set(value) = preferences.edit().putLong("periodStart", value ?: -1L).apply()

    private val currentDay: Int
        get() = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    //    suspend
    fun canMakeCallWithResult():CallResult {
        if (!rateLimitingOn) return CallResult(true, null, null)

        resetIfNeeded()

        return when {
            dailyCount >= dailyLimit -> CallResult(false, FailReason.DAILY, timeUntilNextDay())
            hourlyCount >= hourlyLimit -> CallResult(false, FailReason.HOURLY, currentHourlyTimeLeftToWait)
            else -> CallResult(true, null, null)
        }
    }
//    fun canMakeCall():Boolean {
//
//        return when {
//            canMakeCallWithResult().canICallAPI -> true
//            else -> false
//        }
//    }
    fun canMakeAPICall():Boolean {

        return when {
            canMakeCallWithResult().canICallAPI -> true
            else -> false
        }
    }
    fun doIForbidCall():Boolean {
        return !canMakeAPICall()
    }
    fun canMakeCall(): Triple<Boolean, FailReason?, Long?> {
        if (!rateLimitingOn) return Triple(true, null, null)

        resetIfNeeded()

        Timber.v(printCurrentStatus)

        return when {
            dailyCount >= dailyLimit -> Triple(false, FailReason.DAILY, timeUntilNextDay())
            hourlyCount >= hourlyLimit -> Triple(false, FailReason.HOURLY, currentHourlyTimeLeftToWait)
            else -> Triple(true, null, null)
        }
    }
    fun recordCall() {
        if (periodStart == null) {
            periodStart = System.currentTimeMillis() // Start the period with the first call
        }
        hourlyCount += 1
        dailyCount += 1
    }
    /**
     * Resets the hourly and daily rate limit counters if necessary.
     *
     * This function checks if a new day has started or if the current hourly period has expired.
     * If a new day has started, it clears all saved rate limit data (hourly count, period start, and all daily counts)
     * and resets the hourly and daily counters to zero.
     * If the current hourly period (since 'periodStart') has expired (60 minutes), it resets the hourly count to zero
     * and clears the 'periodStart' time.
     * Finally, it updates the 'lastDay' preference to the current day.
     */
    private fun resetIfNeeded() {
        val savedDay = preferences.getInt("lastDay", -1)

        if (savedDay != currentDay) {
            preferences.edit().apply {
                remove("hourlyCount")
                remove("periodStart")
                for (day in 1..31) {
                    remove("dailyCount$day")
                }
                apply()
            }
            hourlyCount = 0
            dailyCount = 0
        } else if (periodStart?.let { System.currentTimeMillis() - it >= 3600 * 1000 } == true) {
            hourlyCount = 0 // Reset after 60 minutes
            periodStart = null // Clear start time
        }
        preferences.edit().putInt("lastDay", currentDay).apply()
    }

    fun resetRateLimits() {
        preferences.edit().apply {
            remove("hourlyCount")
            remove("periodStart")
            for (day in 1..31) {
                remove("dailyCount$day")
            }
            apply()
        }
        hourlyCount = 0
        dailyCount = 0
        periodStart = null
    }

    @VisibleForTesting
    fun getTimeUntilNextDayForTest(): Long {
        return timeUntilNextDay()
    }

    private fun timeUntilNextDay(): Long {
        val calendar = Calendar.getInstance()
        val startOfNextDay = calendar.apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (startOfNextDay - System.currentTimeMillis()) / 1000
    }
}
