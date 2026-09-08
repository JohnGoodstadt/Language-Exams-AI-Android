package com.goodstadt.john.language.exams.packages.Translate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.TranslateLang
import com.goodstadt.john.language.exams.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Translate sheet: German <-> English translation via [TranslationRepository], with the target
 * spoken through the existing [AudioPlaybackRepository] when it's German.
 */
@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val translationRepository: TranslationRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val billingRepository: BillingRepository
) : ViewModel() {

    var sourceText by mutableStateOf("")
        private set
    var targetText by mutableStateOf("")
        private set

    /** Which language the SOURCE box is in; the target is the other one. Defaults German -> English. */
    var sourceLang by mutableStateOf(TranslateLang.GERMAN)
        private set
    val targetLang: TranslateLang
        get() = if (sourceLang == TranslateLang.GERMAN) TranslateLang.ENGLISH else TranslateLang.GERMAN

    var isTranslating by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onSourceChange(text: String) {
        sourceText = text
        errorMessage = null
    }

    /**
     * Pre-fill the source box when the sheet opens - e.g. with the last sentence the user played on the
     * vocab tabs (blank if none). Resets the result and points the source at the app's own language, since
     * played sentences are in that language. Called each time the sheet is shown.
     */
    fun prefillSource(text: String) {
        sourceText = text
        targetText = ""
        errorMessage = null
        sourceLang = if (BuildConfig.LANGUAGE_ID == "en") TranslateLang.ENGLISH else TranslateLang.GERMAN
    }

    /** Swap direction, carrying the texts across too (like Google Translate's swap). */
    fun swapDirection() {
        sourceLang = targetLang
        val previousSource = sourceText
        sourceText = targetText
        targetText = previousSource
        errorMessage = null
    }

    fun translate() {
        val text = sourceText.trim()
        if (text.isBlank()) {
            targetText = ""
            return
        }
        viewModelScope.launch {
            isTranslating = true
            errorMessage = null
            val result = translationRepository.translate(text, sourceLang, targetLang)
            isTranslating = false
            result
                .onSuccess { targetText = it }
                .onFailure { errorMessage = it.message ?: "Translation failed" }
        }
    }

    /** Only offer speech when the target text is German (the flavour's TTS voice is German). */
    val canSpeakTarget: Boolean
        get() = targetLang == TranslateLang.GERMAN && targetText.isNotBlank()

    /** Speak the (German) target text using the existing audio pipeline. */
    fun speakTarget() {
        if (!canSpeakTarget) return
        viewModelScope.launch {
            audioPlaybackRepository.playTrackAndGetStatus(
                sentence = targetText,
                level = "Translate",
                isPremiumUser = billingRepository.isPurchased.value,
                useRateLimiting = false // debug tool for now; don't gate behind rate limits
            )
        }
    }
}
