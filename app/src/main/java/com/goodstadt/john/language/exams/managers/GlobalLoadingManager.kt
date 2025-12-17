package com.goodstadt.john.language.exams.managers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalLoadingManager @Inject constructor() {
    private val _isShowingGlobalLoading = MutableStateFlow(false)
    val isShowingGlobalLoading = _isShowingGlobalLoading.asStateFlow()

    fun show() {
        _isShowingGlobalLoading.value = true
    }

    fun hide() {
        _isShowingGlobalLoading.value = false
    }
}