package com.goodstadt.john.language.exams.managers

import android.content.Context
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.config.DebugFlags
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/* AI reccomend smaller numbers + no day 1 free
const val HOURLY_LIMIT = 40
const val DAILY_LIMIT = 150

 */
const val HOURLY_LIMIT = 30
const val DAILY_LIMIT = 80

@Module
@InstallIn(SingletonComponent::class)
object RateLimiterModule {

    @Provides
    @Singleton
    fun provideSimpleRateLimiter(
        @ApplicationContext context: Context
    ): SimpleRateLimiter {



        // Release ALWAYS uses the live limits. In DEBUG, DebugFlags.RATE_LIMIT_TEST swaps in low test
        // limits so the paywall triggers after a few plays - flip the flag instead of editing this module.
        if (BuildConfig.DEBUG && DebugFlags.RATE_LIMIT_TEST) {
            return SimpleRateLimiter(
                context = context,
                hourlyLimit = DebugFlags.RATE_LIMIT_TEST_HOURLY,
                dailyLimit = DebugFlags.RATE_LIMIT_TEST_DAILY,
                schemeID = "schemeDebug",
                name = "schemeDebug",
                description = "DEBUG test limiter (low limits)"
            )
        } else {
            return SimpleRateLimiter( //called from app Injection using Hilt
                context = context,
                hourlyLimit = HOURLY_LIMIT,
                dailyLimit = DAILY_LIMIT,
                schemeID = "scheme1",
                name = "scheme1", //same as main prod scheme. so no download is done on app start
                description = "Built in Limiter"
            )
        }

    }
}

