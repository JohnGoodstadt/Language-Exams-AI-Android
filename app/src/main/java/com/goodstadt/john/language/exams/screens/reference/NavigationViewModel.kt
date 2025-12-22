package com.goodstadt.john.language.exams.screens.reference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestNavTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor() : ViewModel() {

    // ✅ Change to MutableStateFlow so it holds the value (Sticky)
    private val _pendingNavigation = MutableStateFlow<SideQuestNavTarget?>(null)
    val pendingNavigation = _pendingNavigation.asStateFlow()

    fun requestNavigation(target: SideQuestNavTarget) {
        _pendingNavigation.value = target
    }

    // Call this after we successfully navigated so we don't loop
    fun consumeNavigation() {
        _pendingNavigation.value = null
    }
}