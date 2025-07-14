package com.goodstadt.john.language.exams.viewmodels

/**
 * A sealed interface to represent the different states of audio playback.
 * This is used by ViewModels to communicate the playback status to the UI.
 */
sealed interface PlaybackState {
    /** The player is currently idle, not playing anything. */
    data object Idle : PlaybackState

    /** The player is actively playing a track, identified by its unique ID. */
    data class Playing(val sentenceId: String) : PlaybackState

    /** An error occurred during playback. */
    data class Error(val message: String) : PlaybackState
}