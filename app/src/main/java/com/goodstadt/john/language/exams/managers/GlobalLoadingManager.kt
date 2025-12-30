package com.goodstadt.john.language.exams.managers

import android.content.Context
import android.media.MediaPlayer
import com.goodstadt.john.language.exams.R
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
    fun playSuccessSound(context: Context) {
        val mp = MediaPlayer.create(context, R.raw.success_chime)
        mp.start()
        mp.setOnCompletionListener { it.release() }
    }
}