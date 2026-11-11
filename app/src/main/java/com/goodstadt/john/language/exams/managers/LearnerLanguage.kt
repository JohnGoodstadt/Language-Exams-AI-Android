package com.goodstadt.john.language.exams.managers

import android.content.Context
import android.content.res.Resources
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.repository.TranslateLang
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The learner's own language: what the app's text should be translated INTO. Detected from the phone's
 * language settings at launch, and can be overridden in Settings ("My Language"). Any screen that translates
 * can inject this and read [language]. Not yet used by the translate code itself; that comes later.
 * Mirrors iOS LearnerLanguage (GoogleTranslateService.swift).
 */
@Singleton
class LearnerLanguage @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        /** The languages a learner can choose, in Settings order. */
        val choices = listOf(
            TranslateLang.ENGLISH, TranslateLang.GERMAN, TranslateLang.SPANISH,
            TranslateLang.ITALIAN, TranslateLang.FRENCH, TranslateLang.CHINESE
        )
        private const val PREFS = "learner_language"
        /** Only set when the user picks one in Settings; otherwise the phone setting is followed. */
        private const val KEY_CHOICE = "learner_language_choice"
        private const val KEY_ASKED = "learner_language_asked"

        /** The language the app teaches (German in the de flavour). Learners aren't offered translation into it. */
        val appLanguage: TranslateLang
            get() = if (BuildConfig.LANGUAGE_ID == "en") TranslateLang.ENGLISH else TranslateLang.GERMAN

        /**
         * The first of the phone's languages that is in [choices] and isn't the language being learned (a
         * German speaker learning German gets their next language, if any). If there is none: English, or
         * German for the English app.
         */
        fun detectFromPhone(): TranslateLang = phoneLanguageMatch()
            ?: if (appLanguage == TranslateLang.ENGLISH) TranslateLang.GERMAN else TranslateLang.ENGLISH

        /** The first phone language in [choices] that isn't the language being learned; null if none. */
        fun phoneLanguageMatch(): TranslateLang? {
            val locales = Resources.getSystem().configuration.locales
            for (i in 0 until locales.size()) {
                val lang = TranslateLang.fromCode(locales[i].language)
                if (lang != null && lang in choices && lang != appLanguage) return lang
            }
            return null
        }

        /** The languages offered when asking: everything except the language being learned. */
        val askChoices: List<TranslateLang> get() = choices.filter { it != appLanguage }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What the phone's settings give, whether or not the user has overridden it. */
    val detected: TranslateLang = detectFromPhone()

    /**
     * True when the phone gave no usable language (e.g. a German phone running the German app, as most
     * testers do), so [detected] is only a guess.
     */
    val detectedIsGuess: Boolean = phoneLanguageMatch() == null

    /**
     * Ask the learner once, the first time they translate, when the language is only a guess and they
     * haven't chosen one. Asked once only, whatever they answer.
     */
    fun needsAsking(): Boolean = !_isUserChoice.value && detectedIsGuess && !prefs.getBoolean(KEY_ASKED, false)

    /** "Not now": keep the guess and don't ask again (Settings > My Language still changes it). */
    fun markAsked() { prefs.edit().putBoolean(KEY_ASKED, true).apply() }

    private val chosen: TranslateLang? = TranslateLang.fromCode(prefs.getString(KEY_CHOICE, null))?.takeIf { it in choices }

    private val _language = MutableStateFlow(chosen ?: detected)
    /** The learner's language, for any screen that translates. */
    val language = _language.asStateFlow()

    private val _isUserChoice = MutableStateFlow(chosen != null)
    /** True when the user picked it in Settings; false when it comes from the phone. */
    val isUserChoice = _isUserChoice.asStateFlow()

    init {
        Timber.i("Learner language: ${_language.value.display} (${if (chosen != null) "chosen in Settings" else "from phone"}; phone gives ${detected.display})")
    }

    /** Pick a language in Settings, or null to follow the phone again. */
    fun choose(lang: TranslateLang?) {
        markAsked()
        if (lang != null) {
            prefs.edit().putString(KEY_CHOICE, lang.code).apply()
            _language.value = lang
            _isUserChoice.value = true
        } else {
            prefs.edit().remove(KEY_CHOICE).apply()
            _language.value = detected
            _isUserChoice.value = false
        }
    }
}
