package com.goodstadt.john.language.exams.packages.Translate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.TranslateLang
import com.goodstadt.john.language.exams.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Backs the Translate sheet. One side is the **app language** ([appLang], fixed per flavour — German for
 * `de`, English for `en` — and the one with a TTS voice); the other is the learner's own **target
 * language** ([userLang]), which they pick from [targetOptions]. The choice is defaulted from the phone
 * locale, remembered across opens (DataStore), and either language can be the source via [swapDirection].
 */
@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val translationRepository: TranslationRepository,
    private val translationCache: TranslationCache,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val billingRepository: BillingRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    /** The flavour's own language — always has a TTS voice, and can't be the [userLang] target. */
    val appLang: TranslateLang =
        if (BuildConfig.LANGUAGE_ID == "en") TranslateLang.ENGLISH else TranslateLang.GERMAN

    /** Languages the user can translate INTO (everything in the table except the app's own language). */
    val targetOptions: List<TranslateLang> = TranslateLang.entries.filter { it != appLang }

    var sourceText by mutableStateOf("")
        private set
    var targetText by mutableStateOf("")
        private set

    /** The learner's chosen language. Seeded from the phone locale, then overridden by a saved choice. */
    var userLang by mutableStateOf(localeDefault() ?: targetOptions.first())
        private set

    // true = source is the app language (default; played sentences are in it), target is [userLang].
    private var sourceIsApp by mutableStateOf(true)

    val sourceLang: TranslateLang get() = if (sourceIsApp) appLang else userLang
    val targetLang: TranslateLang get() = if (sourceIsApp) userLang else appLang

    var isTranslating by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        // Restore the remembered target choice (if any); otherwise the locale default above stands.
        viewModelScope.launch {
            TranslateLang.fromCode(userPreferencesRepository.translateTargetLangFlow.first())
                ?.takeIf { it != appLang }
                ?.let { userLang = it }
        }
    }

    /** The phone's language as a table entry, unless it's the app language; null if not in the table. */
    private fun localeDefault(): TranslateLang? =
        TranslateLang.fromCode(Locale.getDefault().language)?.takeIf { it != appLang }

    fun onSourceChange(text: String) {
        sourceText = text
        errorMessage = null
    }

    /** Choose the target language (persisted for next time). Ignored if it's the app's own language. */
    fun chooseUserLang(lang: TranslateLang) {
        if (lang == appLang || lang == userLang) return
        userLang = lang
        targetText = "" // the previous result was for the old language
        errorMessage = null
        viewModelScope.launch { userPreferencesRepository.setTranslateTargetLang(lang.code) }
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
        sourceIsApp = true
    }

    /** Swap direction, carrying the texts across too (like Google Translate's swap). */
    fun swapDirection() {
        sourceIsApp = !sourceIsApp
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

            // Local cache first: an instant hit avoids a paid API call and works offline.
            val cached = translationCache.get(sourceLang, targetLang, text)
            if (cached != null) {
                targetText = cached
                isTranslating = false
                return@launch
            }

            val result = translationRepository.translate(text, sourceLang, targetLang)
            isTranslating = false
            result
                .onSuccess {
                    targetText = it
                    // Remember it for quick/offline retrieval next time.
                    translationCache.put(sourceLang, targetLang, text, it)
                }
                .onFailure { errorMessage = it.message ?: "Translation failed" }
        }
    }

    /** Only offer speech when the target is the app's own language — that's the flavour's TTS voice. */
    val canSpeakTarget: Boolean
        get() = targetLang == appLang && targetText.isNotBlank()

    /** Speak the (app-language) target text using the existing audio pipeline. */
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
