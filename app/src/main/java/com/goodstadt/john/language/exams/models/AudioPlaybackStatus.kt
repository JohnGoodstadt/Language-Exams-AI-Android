package com.goodstadt.john.language.exams.models

import com.goodstadt.john.language.exams.managers.SimpleRateLimiter


sealed interface AudioPlaybackStatus {

    // Audio played successfully (source doesn't matter to the View)
    data object PlayedFromTTSAPI : AudioPlaybackStatus

    data object PlayedFromLocalCache : AudioPlaybackStatus

    data object PlayedFromCloudStorage : AudioPlaybackStatus

    // Audio failed to play (Network error, file corrupt)
    data object Failure : AudioPlaybackStatus

    // User hit the wall -> View needs to show the Upgrade Sheet
    data class RateLimited(val failReason: SimpleRateLimiter.FailReason) : AudioPlaybackStatus
}