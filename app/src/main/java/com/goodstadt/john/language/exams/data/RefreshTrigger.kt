package com.goodstadt.john.language.exams.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A simple, injectable singleton that acts as an event bus for triggering
 * data refreshes across unrelated ViewModels.
 */
@Singleton
class RefreshTrigger @Inject constructor() {

    // The state is just a simple counter. We don't care about the value,
    // only that it changes.
    private val _progressMapRefreshTrigger = MutableStateFlow(0)
    val progressMapRefreshTrigger = _progressMapRefreshTrigger.asStateFlow()

    /**
     * Call this function to notify any listeners that the progress map data is stale
     * and needs to be refreshed.
     */
    fun triggerProgressMapRefresh() {
        _progressMapRefreshTrigger.update { it + 1 }
    }
}