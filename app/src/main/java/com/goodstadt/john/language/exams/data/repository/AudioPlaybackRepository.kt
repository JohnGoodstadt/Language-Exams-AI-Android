package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statFBCloudHitCount
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
    private val loadingManager: GlobalLoadingManager
) {

    /**
     * The Single Source of Truth for playing audio.
     * 1. Checks Rate Limits
     * 2. Executes Waterfall (Disk -> Cloud -> TTS)
     * 3. Updates History (Red Dots)
     * 4. Updates XP & Graphs
     */
    suspend fun playTrackAndGetResult(
        sentence: String,
        level: String,          // e.g. "A1", "B1", "Reference"
        sheetName: String = "", // e.g. "EnglishConjugationsToBe" (Required for Graph stats)
        isPremiumUser: Boolean = false
    ): Boolean {

        // --- 1. Rate Limiting Check ---
        // (Logic copied from your previous snippets)
        val todayIsNotAFreePassDay = calcIsTodayNotAFreePassDay(userPreferencesRepository)

        // Only block API calls if not premium and not on a free pass day
        // Note: contentRepository handles the actual "Is this file on disk?" check.
        // If it's on disk, we shouldn't block. But here we do a pre-check.
        // For strict correctness, the Repo could return "Cached" without hitting this limit,
        // but checking here prevents abuse.
        if (!isPremiumUser && todayIsNotAFreePassDay) {
            if (rateLimiter.doIForbidCall()) {
                Timber.tag("AudioPlayback").w("Rate limit exceeded.")
                // Note: The UI showing the BottomSheet should be handled by the ViewModel
                // checking rateLimiter status before calling this, or handling a specific failure result.
                return false
            }
        }

        // --- 2. Prepare Data ---
        val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
        val currentLanguageCode = userPreferencesRepository.selectedLanguageCodeFlow.first()
        val uniqueSentenceId = FirebaseAudioService.generateUnifiedFilename(sentence, currentVoiceName)

        // --- 3. Execute Playback (The Waterfall) ---
        val result = contentRepository.playTextToSpeechAndSaveToCache(
            text = sentence,
            uniqueSentenceId = uniqueSentenceId,
            voiceName = currentVoiceName,
            languageCode = currentLanguageCode
        )

        // --- 4. Handle Side Effects (Stats & History) ---
        when (result) {
            is PlaybackResult.PlayedFromNetworkAndCached,
            is PlaybackResult.PlayedFromLocalCache -> {

                // A. Generate ID
                val contentID = FirebaseAudioService.generateContentID(sentence)

                // B. Check if this is a "First Time" listen (Voice Agnostic)
                // We check !isHeard BEFORE we mark it heard.
                val isFirstTime = !historyManager.isHeard(level, contentID)

                // C. Update History (The Red Dot Source of Truth)
                // This triggers the StateFlow that ViewModels observe
                //This can inc twice as later also does it
                //historyManager.markSentenceHeard(level, contentID)

                // D. Update XP & Graphs (Only on first listen)
                if (isFirstTime) {
                    // XP
                    xpManager.registerAction(XpActionType.HearNewSentence,specificLevel = level)

                    // Legacy Stats
                    ttsStatsRepository.incProgressSize(userPreferencesRepository.selectedSkillLevelFlow.first())

                    // Graph Stats (Side Quest Sheet)
                    // Only update if we have a valid sheet name (Reference tabs)
                    if (sheetName.isNotEmpty()) {
                        val currentStats = audioCacheManager.getReferenceStats(sheetName)
                        audioCacheManager.updateReferenceStats(
                            key = sheetName,
                            heard = currentStats.heard + 1,
                            total = currentStats.total // Assumes total was set on load
                        )
                    }

                    // Cost Tracking (Only if network used)
                    if (result is PlaybackResult.PlayedFromNetworkAndCached) {
                        if (todayIsNotAFreePassDay) { rateLimiter.recordCall() }
                        ttsStatsRepository.updateTTSStatsWithCosts(sentence, currentVoiceName)

                    } else {
                        ttsStatsRepository.updateTTSStatsWithoutCosts()
                        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statFBCloudHitCount)
                    }

                } else {
                    // Replay Logic
                    xpManager.registerAction(XpActionType.ReplaySentence,specificLevel = level)
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                }
            }

            is PlaybackResult.Failure -> {
                Timber.e(result.exception, "AudioPlaybackRepository: Failure")
            }

            PlaybackResult.CacheNotFound -> {
                // Should not happen if waterfall logic works
                Timber.e("AudioPlaybackRepository: Cache not found logic error")
            }
        }

        // --- 5. Return Simple Boolean ---
        return when (result) {
            is PlaybackResult.PlayedFromLocalCache,
            is PlaybackResult.PlayedFromNetworkAndCached -> true
            else -> false
        }
    }

    suspend fun playTrackAndGetStatus(
        sentence: String,
        level: String,
        sheetName:String = "", //for reference tab - stats for sheet otherwise must be vocab tabs 1,2,3
        isPremiumUser: Boolean = false
    ): AudioPlaybackStatus {

        val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
        val uniqueSentenceId = FirebaseAudioService.generateUnifiedFilename(sentence, currentVoiceName)

        val loadingJob = CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(500)
            loadingManager.show()
        }

        // ---------------------------------------------------------
        // 1. CHECK LOCAL DISK (Free & Fast)
        // ---------------------------------------------------------
        if (contentRepository.playFromLocalCacheIfExists(uniqueSentenceId)) {
            // Stats logic for replay...
            handleSuccess(sentence, level, sheetName = sheetName)
            Timber.v("🔊 Waterfall L1: Playing from Local Disk,  Yippee!!: $uniqueSentenceId")
            loadingJob.cancel()
            loadingManager.hide()
            return AudioPlaybackStatus.PlayedFromLocalCache
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
                Timber.v("☁️ Waterfall L2: Downloaded from Cloud Storage. Yippee!")
                return AudioPlaybackStatus.PlayedFromCloudStorage
            }
        }

        try {


            // ---------------------------------------------------------
            // 3. RATE LIMIT CHECK (Before spending money)
            // ---------------------------------------------------------
            val todayIsNotAFreePassDay = calcIsTodayNotAFreePassDay(userPreferencesRepository)

            if (!isPremiumUser && todayIsNotAFreePassDay) {
                if (rateLimiter.doIForbidCall()) {
                    val failType = rateLimiter.canMakeCallWithResult()

                    // Log Analytics
                    val limitType =
                        if (failType.failReason == SimpleRateLimiter.FailReason.DAILY) "daily" else "hourly"
                    AnalyticsHelper.logRateLimitHit(context, limitType, 0)

                    loadingJob.cancel()
                    loadingManager.hide()

                    // Return Blocked Status
                    return AudioPlaybackStatus.RateLimited(
                        failType.failReason ?: SimpleRateLimiter.FailReason.HOURLY
                    )
                }
            }

            // ---------------------------------------------------------
            // 4. GOOGLE TTS (Paid)
            // ---------------------------------------------------------
            val currentLanguageCode = userPreferencesRepository.selectedLanguageCodeFlow.first()

            Timber.v("🗣️ Waterfall L3: Calling Google TTS")
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
                if (todayIsNotAFreePassDay) {
                    rateLimiter.recordCall()
                }
                AudioPlaybackStatus.PlayedFromTTSAPI
            } else {
                AudioPlaybackStatus.Failure
            }

        } catch (e: Exception) {
            Timber.e(e, "AudioPlaybackRepository: Error during playback waterfall")

            // Critical: Stop the spinner so the UI doesn't freeze
            loadingJob.cancel()
            loadingManager.hide()

            return AudioPlaybackStatus.Failure
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
    // MARK: - Unified Success Handler


}

