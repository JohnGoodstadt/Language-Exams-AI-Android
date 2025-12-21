package com.goodstadt.john.language.exams.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsHelper {

    // Calculates if this is Day 1, Day 2, etc.
    fun getUserDayNumber(context: Context): Int {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installTime = packageInfo.firstInstallTime
            val now = System.currentTimeMillis()

            // Calculate days diff
            val diff = now - installTime
            val days = (diff / (1000 * 60 * 60 * 24)).toInt()

            return days + 1 // "Day 1" is nicer to read than "Day 0"
        } catch (e: Exception) {
            return 0 // Unknown
        }
    }

    // Metric 1: Log when they hit the wall
    fun logRateLimitHit(context: Context, limitType: String, currentCount: Int) {
        val dayNum = getUserDayNumber(context)

        val params = Bundle().apply {
            putString("limit_type", limitType) // "hourly" or "daily"
            putInt("count_at_hit", currentCount)
            putInt("user_day", dayNum) // Critical: Are Day 2 users hitting this?
        }

        FirebaseAnalytics.getInstance(context).logEvent("rate_limit_hit", params)
    }

    // Metric 2: Log Daily Volume (Call this on App Background/Session End)
    fun logDailyUsageSnapshot(context: Context, totalPlaysToday: Int, isPremium: Boolean) {
        val dayNum = getUserDayNumber(context)

        val params = Bundle().apply {
            putInt("total_daily_plays", totalPlaysToday)
            putInt("user_day", dayNum)
            putString("user_status", if(isPremium) "premium" else "free")

            // Did they naturally exceed the limits?
            putString("exceeded_150_threshold", if(totalPlaysToday >= 150) "true" else "false")
        }

        FirebaseAnalytics.getInstance(context).logEvent("daily_usage_snapshot", params)
    }
    // In AnalyticsService or Extension
    fun logPaywallResponse(context: Context,result: String, source: String) {
        val params = Bundle().apply {
            putString("result", result) // "accepted" or "rejected"
            putString("source", source) // "limit_daily" or "limit_hourly"
        }
        FirebaseAnalytics.getInstance(context).logEvent("paywall_response", params)
    }
}