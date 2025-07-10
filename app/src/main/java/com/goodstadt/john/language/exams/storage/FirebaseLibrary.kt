package com.goodstadt.john.language.exams.storage

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    // Add other event types here (e.g., Navigate, ShowDialog, etc.)
}