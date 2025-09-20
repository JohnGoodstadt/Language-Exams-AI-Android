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


@Module
@InstallIn(SingletonComponent::class)
object RateLimiterModule {

    @Provides
    @Singleton
    fun provideSimpleRateLimiter(
        @ApplicationContext context: Context
    ): SimpleRateLimiter {
        return SimpleRateLimiter( //called from app Injection using Hilt
                context = context,
                hourlyLimit = 40,
                dailyLimit = 100,
                schemeID = "scheme1",
                name = "scheme1", //same as main prod scheme. so no download is done on app start
                description = "Built in Limiter"
        )
    }
}

/*
object RateLimiterManager {
    private lateinit var rateLimiter: SimpleRateLimiter

    fun initialize(context: Context) {
        val appContext = context.applicationContext

        rateLimiter = SimpleRateLimiter( //called from ivar use
                context = appContext,
                hourlyLimit = 50, //Maybe 200ish words in A1 + reference + Paragraph
                dailyLimit = 200,
                schemeID = "scheme1",
                name = "scheme1", //same as main prod scheme. so no download is done on app start
                description = "Unit Test Rate Limiter"
        )
    }

    fun updateScheme(hourlyLimit:Int,
                     dailyLimit :Int,
                     schemeID:String,
                     name:String,
                     description:String){

        rateLimiter.hourlyLimit = hourlyLimit
        rateLimiter.dailyLimit = dailyLimit
        rateLimiter.schemeID = schemeID
        rateLimiter.name = name
        rateLimiter.description = description


    }
    fun updateToMinimalScheme() {
        if (DEBUG && TEST_RATE_LIMITING) { //Never in prod
            rateLimiter.hourlyLimit = 10
            rateLimiter.dailyLimit = 20
            rateLimiter.schemeID = "MinimalScheme"
            rateLimiter.name = "Minimal Test Rate Limiter"
            rateLimiter.description = "Test Rate Limiter using smallest values"
        }
    }
    fun getInstance(): SimpleRateLimiter {
        if (!this::rateLimiter.isInitialized) {
            throw IllegalStateException("RateLimiterManager has not been initialized")
        }
        return rateLimiter
    }
}

 */
