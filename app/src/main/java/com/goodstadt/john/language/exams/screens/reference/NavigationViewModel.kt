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
    var pendingReferenceTabId: String? = null

    fun requestNavigation(target: SideQuestNavTarget) {
        viewModelScope.launch {
            // 1. If it's a Reference/Quiz target, save the ID
            if (target is SideQuestNavTarget.Reference) {
                pendingReferenceTabId = target.tabId
            }
            // If you use SideQuestNavTarget.Quiz explicitly:
//            else if (target is SideQuestNavTarget.Quiz) {
//                pendingReferenceTabId = "quiz"
//            }
            _navigationEvent.send(target)
        }
    }

    // Call this after we successfully navigated so we don't loop
//    fun consumeNavigation() {
//        _pendingNavigation.value = null
//    }
}