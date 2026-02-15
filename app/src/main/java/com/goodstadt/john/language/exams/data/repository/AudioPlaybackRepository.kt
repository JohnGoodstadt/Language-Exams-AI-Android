package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import com.goodstadt.john.language.exams.data.AudioPlayerService
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statFBCloudHitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statFBCloudMissCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statLocalCacheHitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statLocalCacheMissCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statRateLimiterDayForbidCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statRateLimiterForbidCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statRateLimiterHourForbidCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statTTSFailureCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statTTSSuccessCount
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.GlobalLoadingManager
import com.goodstadt.john.language.exams.managers.HistorySyncManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter


import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.utils.AnalyticsHelper
import com.goodstadt.john.language.exams.utils.calcIsTodayNotAFreePassDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlaybackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentRepository: ContentRepository,
    private val historyManager: HistorySyncManager,
    private val xpManager: XPManager,
    private val audioCacheManager: AudioCacheManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val ttsStatsRepository: TTSStatsRepository,
    private val loadingManager: GlobalLoadingManager,
    private val audioPlayerService: AudioPlayerService
) {

    /**
     * The Single Source of Truth for playing audio.
     * 1. Checks Rate Limits
     * 2. Executes Waterfall (Disk -> Cloud -> TTS)
     * 3. Updates History (Red Dots)
     * 4. Updates XP & Graphs
     */

    suspend fun playTrackAndGetStatus(
        sentence: String,
        level: String,
        sheetName:String = "", //for reference tab - stats for sheet otherwise must be vocab tabs 1,2,3
        isPremiumUser: Boolean = false
    ): AudioPlaybackStatus {

        val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
        val uniqueSentenceId = FirebaseAudioService.generateUnifiedFilename(sentence, currentVoiceName)



        // ---------------------------------------------------------
        // 1. CHECK LOCAL DISK (Free & Fast)
        // ---------------------------------------------------------
        if (contentRepository.playFromLocalCacheIfExists(uniqueSentenceId))
        {
            // Stats logic for replay...
            handleSuccess(sentence, level, sheetName = sheetName)
            Timber.v("🔊 Waterfall L1: Playing from Local Disk,  Yippee!! '${sentence.take(21)}'")
          //  loadingJob.cancel()
//            loadingManager.hide()
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statLocalCacheHitCount)
            ttsStatsRepository.updateTTSStatsWithoutCosts() //MP3PlayedCount
            return AudioPlaybackStatus.PlayedFromLocalCache
        } else {
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statLocalCacheMissCount)
        }

        //only show if not locally cached
        val loadingJob = CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(500)
            loadingManager.show()
        }

        // ---------------------------------------------------------
        // 3. RATE LIMIT CHECK (Before spending money)
        // ---------------------------------------------------------
        //AI Recommends ignore install day free
        //val todayIsNotAFreePassDay = calcIsTodayNotAFreePassDay(userPreferencesRepository)

        //if (!isPremiumUser && todayIsNotAFreePassDay) {
        if (!isPremiumUser) {
            if (rateLimiter.doIForbidCall()) {
                val failType = rateLimiter.canMakeCallWithResult()

                // Log Analytics
                val limitType = if (failType.failReason == SimpleRateLimiter.FailReason.DAILY) "daily" else "hourly"
                AnalyticsHelper.logRateLimitHit(context, limitType, 0)

                loadingJob.cancel()
                loadingManager.hide()

                when (failType.failReason) {
                    SimpleRateLimiter.FailReason.DAILY -> {ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statRateLimiterDayForbidCount)}
                    SimpleRateLimiter.FailReason.HOURLY -> {ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statRateLimiterHourForbidCount)}
                    null -> {}
                }
                ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statRateLimiterForbidCount)
                ttsStatsRepository.updateTTSStatsWithoutCosts() //MP3PlayedCount
                // Return Blocked Status
                return AudioPlaybackStatus.RateLimited(failType.failReason ?: SimpleRateLimiter.FailReason.HOURLY)
            }
        }


        // ---------------------------------------------------------
        // 2. CHECK CLOUD STORAGE (Free-ish)
        // Only if user has heard it before (History Check)
        // ---------------------------------------------------------

        val contentID = FirebaseAudioService.generateContentID(sentence)
        val isHeard = historyManager.isHeard(level, contentID)

        if (isHeard) { //enforce user has already heard it so check storage - i.e. business logic not code logic
            if (contentRepository.playFromCloudStorageIfExists(uniqueSentenceId)) {
                loadingJob.cancel()
                loadingManager.hide()

                handleSuccess(sentence, level, sheetName)
                Timber.v("☁️ Waterfall L2: Downloaded from Cloud Storage. Yippee! '${sentence.take(21)}'")
                ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statFBCloudHitCount)
                return AudioPlaybackStatus.PlayedFromCloudStorage
            }else{
                ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statFBCloudMissCount)
            }
        }

        try {


            // ---------------------------------------------------------
            // 4. GOOGLE TTS (Paid)
            // ---------------------------------------------------------
            val currentLanguageCode = userPreferencesRepository.selectedLanguageCodeFlow.first()

            Timber.v("🗣️ Waterfall L3: Calling Google TTS '${sentence.take(21)}'")
            val result = contentRepository.generateAndPlayTTS(
                text = sentence,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = currentVoiceName,
                languageCode = currentLanguageCode
            )

            loadingJob.cancel()
            loadingManager.hide()

            return if (result is PlaybackResultSplit.PlayedFromGoogleTTS) {
                handleSuccess(sentence, level, sheetName)

                // Record Cost & Usage
//                if (todayIsNotAFreePassDay) { //AI Reccommends ignore install day free
                    rateLimiter.recordCall()
//                }
                ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statTTSSuccessCount)
                val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
                ttsStatsRepository.updateTTSStatsWithCosts(sentence,currentVoiceName) //MP3PlayedCount
                AudioPlaybackStatus.PlayedFromTTSAPI
            } else {
                ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statTTSFailureCount)
                AudioPlaybackStatus.Failure
            }

        } catch (e: Exception) {
            Timber.e(e, "AudioPlaybackRepository: Error during playback waterfall")

            // Critical: Stop the spinner so the UI doesn't freeze
            loadingJob.cancel()
            loadingManager.hide()
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statTTSFailureCount)
            return AudioPlaybackStatus.Failure
        }

    }
    suspend fun playTrackSimply(
        sentence: String,
        currentVoiceName : String
    ) : Boolean {

        val uniqueSentenceId = FirebaseAudioService.generateUnifiedFilename(sentence, currentVoiceName)


        // ---------------------------------------------------------
        // 1. CHECK LOCAL DISK (Free & Fast)
        // ---------------------------------------------------------
        if (contentRepository.playFromLocalCacheIfExists(uniqueSentenceId))
        {

            Timber.v("🔊 Waterfall L1: Playing from Local Disk,  Yippee!! '${sentence.take(21)}'")
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statLocalCacheHitCount)
            return true //AudioPlaybackStatus.PlayedFromLocalCache
        } else {
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statLocalCacheMissCount)
        }

        //only show if not locally cached
        val loadingJob = CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(500)
            loadingManager.show()
        }

        // ---------------------------------------------------------
        // 2. CHECK CLOUD STORAGE (Free-ish)
        // Only if user has heard it before (History Check)
        // ---------------------------------------------------------

        val contentID = FirebaseAudioService.generateContentID(sentence)
        //val isHeard = historyManager.isHeard(level, contentID)


        if (contentRepository.playFromCloudStorageIfExists(uniqueSentenceId)) {
            loadingJob.cancel()
            loadingManager.hide()

            Timber.v("☁️ Waterfall L2: Downloaded from Cloud Storage. Yippee! '${sentence.take(21)}'")
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statFBCloudHitCount)
            return true//AudioPlaybackStatus.PlayedFromCloudStorage
        } else {
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statFBCloudMissCount)
        }


        try {

            // ---------------------------------------------------------
            // 4. GOOGLE TTS (Paid)
            // ---------------------------------------------------------
            val currentLanguageCode = userPreferencesRepository.selectedLanguageCodeFlow.first()

            Timber.v("🗣️ Waterfall L3: Calling Google TTS '${sentence.take(21)}'")
            val result = contentRepository.generateAndPlayTTS(
                text = sentence,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = currentVoiceName,
                languageCode = currentLanguageCode
            )

            loadingJob.cancel()
            loadingManager.hide()

            return true
//            return if (result is PlaybackResultSplit.PlayedFromGoogleTTS) {
//                handleSuccess(sentence, level, sheetName)
//
//                // Record Cost & Usage
//                if (todayIsNotAFreePassDay) {
//                    rateLimiter.recordCall()
//                }
//                ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statTTSSuccessCount)
//                AudioPlaybackStatus.PlayedFromTTSAPI
//            } else {
//                ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statTTSFailureCount)
//                AudioPlaybackStatus.Failure
//            }

        } catch (e: Exception) {
            Timber.e(e, "AudioPlaybackRepository: Error during playback waterfall")

            // Critical: Stop the spinner so the UI doesn't freeze
            loadingJob.cancel()
            loadingManager.hide()
//            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statTTSFailureCount)
            return false//AudioPlaybackStatus.Failure
        }

    }
    private fun handleSuccess(sentence: String, level: String, sheetName: String) {
        val contentID = FirebaseAudioService.generateContentID(sentence)

        // 1. Check if First Time (Voice Agnostic)
        // We check before marking it, so we know if we should award XP/Stats
        val isFirstTime = !historyManager.isHeard(level, contentID)

        // 2. Update History (The Red Dot Source of Truth)
        historyManager.markSentenceHeard(level, contentID)

        // 3. Update XP
        if (isFirstTime) {
            xpManager.registerAction(XpActionType.HearNewSentence)

            // 4. ✅ CONDITIONAL REFERENCE UPDATE
            // If a sheetName is provided, we update the Side Quest Graph immediately.
            if (sheetName.isNotEmpty()) {
                val currentStats = audioCacheManager.getReferenceStats(sheetName)
                audioCacheManager.updateReferenceStats(
                    key = sheetName,
                    heard = currentStats.heard + 1,
                    total = currentStats.total // Assumes total was set on load
                )
            }

        } else {
            xpManager.registerAction(XpActionType.ReplaySentence)
        }
    }




    private fun handleSuccessAlsoObsolete(sentence: String, level: String, isNew: Boolean) {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        historyManager.markSentenceHeard(level, contentID)

        if (isNew) xpManager.registerAction(XpActionType.HearNewSentence)
        else xpManager.registerAction(XpActionType.ReplaySentence)
    }
    private fun handleSuccessObsolete(sentence: String, level: String, sheetName: String, isNew: Boolean) {
        val contentID = FirebaseAudioService.generateContentID(sentence)

        // 1. Update History (Red Dots)
        historyManager.markSentenceHeard(level, contentID)

        // 2. Update XP
        if (isNew) {
            xpManager.registerAction(XpActionType.HearNewSentence)

            // 3. Update Side Quest Graph (Only if it's a new Reference item)
            // We check sheetName to ensure we aren't updating Main Quest tabs here (they use TabNumber)
            if (sheetName.isNotEmpty()) {
                val currentStats = audioCacheManager.getReferenceStats(sheetName)
                audioCacheManager.updateReferenceStats(
                    key = sheetName,
                    heard = currentStats.heard + 1,
                    total = currentStats.total
                )
            }
        } else {
            xpManager.registerAction(XpActionType.ReplaySentence)
        }
    }

    fun stopPlayback() {
        audioPlayerService.stopPlayback()
    }
    // MARK: - Unified Success Handler


}

