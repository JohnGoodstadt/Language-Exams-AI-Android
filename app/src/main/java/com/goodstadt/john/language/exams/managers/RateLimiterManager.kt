package com.goodstadt.john.language.exams.managers

import android.content.Context
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.BuildConfig.TEST_RATE_LIMITING

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import dagger.hilt.components.SingletonComponent

const val HOURLY_LIMIT = 40
const val DAILY_LIMIT = 150

@Module
@InstallIn(SingletonComponent::class)
object RateLimiterModule {

    @Provides
    @Singleton
    fun provideSimpleRateLimiter(
        @ApplicationContext context: Context
    ): SimpleRateLimiter {



        if (BuildConfig.DEBUG){
//        if (false){
            return SimpleRateLimiter( //called from app Injection using Hilt
                context = context,
                hourlyLimit = 4,
                dailyLimit = 10,
                schemeID = "schemeDebug",
                name = "schemeDebug", //same as main prod scheme. so no download is done on app start
                description = "Built in DEBUG  Limiter"
            )
        }else {
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

