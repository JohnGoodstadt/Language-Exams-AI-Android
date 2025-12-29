package com.goodstadt.john.language.exams.data.repository

import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statFBCloudHitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statLocalMP3HitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statTTSSuccessCount
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.HistorySyncManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter


import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.utils.calcIsTodayNotAFreePassDay
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlaybackRepository @Inject constructor(
    private val contentRepository: ContentRepository,
    private val historyManager: HistorySyncManager,
    private val xpManager: XPManager,
    private val audioCacheManager: AudioCacheManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val ttsStatsRepository: TTSStatsRepository
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
        val uniqueSentenceId =
            FirebaseAudioService.generateUnifiedFilename(sentence, currentVoiceName)

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
                    xpManager.registerAction(XpActionType.HearNewSentence)

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
                    xpManager.registerAction(XpActionType.ReplaySentence)
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
}