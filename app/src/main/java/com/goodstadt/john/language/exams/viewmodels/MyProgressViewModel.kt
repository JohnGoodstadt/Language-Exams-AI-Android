package com.goodstadt.john.language.exams.viewmodels

import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.screens.reference.SideQuestData
import com.goodstadt.john.language.exams.screens.reference.getReferenceData


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpState
import com.goodstadt.john.language.exams.utils.CategoryProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyProgressUiState(
    val xpState: XpState,
    val mainQuestProgress: List<CategoryProgress>,
    val sideQuestData: SideQuestData?,
    val aiCount: Int = 0,
    val aiHeard: Int = 0
)

@HiltViewModel
class MyProgressViewModel @Inject constructor(
    private val xpManager: XPManager,
    private val audioCacheManager: AudioCacheManager,
    val quizManager: QuizHistoryManager
) : ViewModel() {

    // Combine flows from Managers into one UI State
    val uiState: StateFlow<MyProgressUiState?> = combine(
        xpManager.state,
        audioCacheManager.referenceHeardCounts,
        // We also trigger on total updates to ensure Main Quest bars move
        audioCacheManager.totalExamWordsHeardOverall
    ) { xpState, refHeardMap, _ ->

        // 1. Build Main Quest Data (Tabs 1-3)
        // We filter for tabs 1-3 to separate them from reference
        val allCats = audioCacheManager.getAllCategoryProgress()
        val mainQuest = allCats
            .filter { it.tabNumber in 1..3 }
            .sortedWith(compareBy({ it.tabNumber }, { it.sortOrder }))

        // 2. Build Side Quest Data (Reference)
        val sideQuest = getReferenceData(audioCacheManager, refHeardMap)

        MyProgressUiState(
            xpState = xpState,
            mainQuestProgress = mainQuest,
            sideQuestData = sideQuest,
            aiCount = audioCacheManager.getAIParagraphCount(),
            aiHeard = audioCacheManager.getAIParagraphHeardCount()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Admin Actions
    fun hardReset() {
        xpManager.hardReset()
        quizManager.clearHistory()
        // historyManager.clearAll() // If exposed
    }

    fun getAudioCacheManager(): AudioCacheManager = audioCacheManager
    fun getXpManager(): XPManager = xpManager
}