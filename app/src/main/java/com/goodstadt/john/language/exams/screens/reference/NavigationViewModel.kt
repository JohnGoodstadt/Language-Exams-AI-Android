package com.goodstadt.john.language.exams.screens.reference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestNavTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor() : ViewModel() {

    private val _navigationEvent = Channel<SideQuestNavTarget>(Channel.BUFFERED)

    // Expose as a plain Flow
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun requestNavigation(target: SideQuestNavTarget) {
        viewModelScope.launch {
            _navigationEvent.send(target)
        }
    }

    // Call this after we successfully navigated so we don't loop
//    fun consumeNavigation() {
//        _pendingNavigation.value = null
//    }
}