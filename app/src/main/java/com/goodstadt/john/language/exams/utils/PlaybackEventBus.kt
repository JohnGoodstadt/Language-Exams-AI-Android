package com.goodstadt.john.language.exams.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class PlaybackEvent {
    data class Completed(val filenameOrId: String? = null) : PlaybackEvent()
    data class Failed(val reason: String? = null, val filenameOrId: String? = null) : PlaybackEvent()
    data class Stopped(val filenameOrId: String? = null) : PlaybackEvent()
}

@Singleton
class PlaybackEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<PlaybackEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    fun tryEmit(event: PlaybackEvent) {
        _events.tryEmit(event) // non-suspending, safe from MediaPlayer callbacks
    }
}
