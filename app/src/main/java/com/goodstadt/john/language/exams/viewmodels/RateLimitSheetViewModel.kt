package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.rateLimitDailyViewCount
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.rateLimitHourlyViewCount
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RateLimitSheetViewModel  @Inject constructor(
    private val ttsStatsRepository : TTSStatsRepository
): ViewModel() {

    fun incStatForDaily() {
       // statsManager.inc(StatsManager.fsDOC.TTSStats, fb.rateLimitDailyViewCount)
        ttsStatsRepository.incUserStatCount(rateLimitDailyViewCount)
    }
    fun incStatForHourly() {
        //fsUpdateStatsPropertyCount(fb.rateLimitHourlyViewCount)
        ttsStatsRepository.incUserStatCount(rateLimitHourlyViewCount)

    }

}