package com.goodstadt.john.language.exams.packages.Speeches

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.TranslateLang
import com.goodstadt.john.language.exams.data.repository.TranslationRepository
import com.goodstadt.john.language.exams.managers.AccessPolicy
import com.goodstadt.john.language.exams.managers.SequencePlayer
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.text.BreakIterator
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Text a learner prepares in advance and rehearses ("Sprechen Sie Deutsch?", a job interview, a toast).
 * Each speech keeps both languages. [syncedSpoken] / [syncedNative] are the pair from the last translation,
 * so switching language only calls the (paid) API when the side being shown has really changed, and
 * switching back never replaces hand-corrected text. Fields have defaults so older saved data still loads.
 * Mirrors the iOS PreparedSpeech (SavedPracticeScreen.swift).
 */
@Serializable
data class PreparedSpeech(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val spokenText: String = "",   // the language the app teaches and can speak (German in the de flavour)
    val nativeText: String = "",   // the learner's own language
    val syncedSpoken: String = "",
    val syncedNative: String = "",
    val boxHeight: Float = 180f,   // dp
    /** Locked speeches can be read, translated and played but not edited or deleted. */
    val isLocked: Boolean = false
)

/**
 * Ready-made answers shown below the learner's own speeches. Read-only: no delete, no lock, and both
 * languages are given here, so switching language never calls the translation API.
 * Edit or add entries here. The ids must stay fixed (they key playback).
 */
data class PreparedSpeechExample(
    val id: String,
    val spokenTitle: String,   // title shown with the app's language
    val nativeTitle: String,   // title shown with the learner's language
    val spoken: String,
    val native: String
) {
    fun asSpeech() = PreparedSpeech(
        id = id, title = spokenTitle, spokenText = spoken, nativeText = native,
        syncedSpoken = spoken, syncedNative = native, isLocked = true
    )

    companion object {
        /** de flavour: an English speaker learning German. */
        private val german = listOf(
            PreparedSpeechExample(
                id = "6E0F2D3A-0001-4C1B-9A51-5A9E1D0B7C01",
                spokenTitle = "Sprechen Sie Deutsch? – Kurze Antwort",
                nativeTitle = "Do you speak German? – Short answer",
                spoken = "Ja, ein bisschen. Ich lerne noch Deutsch. Können Sie bitte langsam sprechen?",
                native = "Yes, a little. I’m still learning German. Could you please speak slowly?"
            ),
            PreparedSpeechExample(
                id = "6E0F2D3A-0002-4C1B-9A51-5A9E1D0B7C02",
                spokenTitle = "Sprechen Sie Deutsch? – Ausführliche Antwort",
                nativeTitle = "Do you speak German? – Longer answer",
                spoken = "Ja, ein bisschen. Ich lerne seit einiger Zeit Deutsch. Ich verstehe schon viel, aber das Sprechen ist noch schwierig für mich. Manchmal brauche ich etwas Zeit, um die richtigen Wörter zu finden. Können Sie bitte langsam sprechen?",
                native = "Yes, a little. I’ve been learning German for a while. I already understand quite a lot, but speaking is still difficult for me. Sometimes I need a little time to find the right words. Could you please speak slowly?"
            )
        )

        /** en flavour: for now, a native German speaker learning English (the reverse of above). */
        private val english = listOf(
            PreparedSpeechExample(
                id = "6E0F2D3A-0101-4C1B-9A51-5A9E1D0B7C11",
                spokenTitle = "Do you speak English? – Short answer",
                nativeTitle = "Sprechen Sie Englisch? – Kurze Antwort",
                spoken = "Yes, a little. I’m still learning English. Could you please speak slowly?",
                native = "Ja, ein bisschen. Ich lerne noch Englisch. Können Sie bitte langsam sprechen?"
            ),
            PreparedSpeechExample(
                id = "6E0F2D3A-0102-4C1B-9A51-5A9E1D0B7C12",
                spokenTitle = "Do you speak English? – Longer answer",
                nativeTitle = "Sprechen Sie Englisch? – Ausführliche Antwort",
                spoken = "Yes, a little. I’ve been learning English for a while. I already understand quite a lot, but speaking is still difficult for me. Sometimes I need a little time to find the right words. Could you please speak slowly?",
                native = "Ja, ein bisschen. Ich lerne seit einiger Zeit Englisch. Ich verstehe schon viel, aber das Sprechen ist noch schwierig für mich. Manchmal brauche ich etwas Zeit, um die richtigen Wörter zu finden. Können Sie bitte langsam sprechen?"
            )
        )

        val all: List<PreparedSpeechExample> get() = if (BuildConfig.LANGUAGE_ID == "en") english else german
    }
}

@HiltViewModel
class SpeechesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val translation: TranslationRepository,
    private val sequence: SequencePlayer,
    private val policy: AccessPolicy,
    private val billing: BillingRepository,
    private val limiter: SimpleRateLimiter
) : ViewModel() {

    enum class Prompt { DAILY, HOURLY }

    companion object {
        private const val PREFS = "prepared_speeches"
        private const val KEY = "speeches_json"
        private const val OWNER = "speech:"
    }

    /** The app's language (spoken and played). */
    val spokenLang: TranslateLang = if (BuildConfig.LANGUAGE_ID == "en") TranslateLang.ENGLISH else TranslateLang.GERMAN
    /** The learner's own language: English in de; German in en for now (must match the ready-made answers). */
    val nativeLang: TranslateLang = if (spokenLang == TranslateLang.ENGLISH) TranslateLang.GERMAN else TranslateLang.ENGLISH

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _speeches = MutableStateFlow(load())
    val speeches = _speeches.asStateFlow()

    /** Not saved: only the box height can change, and it resets each time. */
    private val _examples = MutableStateFlow(PreparedSpeechExample.all.map { it.asSpeech() })
    val examples = _examples.asStateFlow()

    val playingKey = sequence.playingKey
    val isPaused = sequence.isPaused

    private val _prompt = MutableStateFlow<Prompt?>(null)
    val prompt = _prompt.asStateFlow()

    fun nativeTitle(exampleId: String): String? = PreparedSpeechExample.all.firstOrNull { it.id == exampleId }?.nativeTitle
    fun isExample(id: String) = PreparedSpeechExample.all.any { it.id == id }
    fun playKey(id: String) = OWNER + id

    // --- Storage (SharedPreferences, this device only) ---

    private fun load(): List<PreparedSpeech> {
        val saved = prefs.getString(KEY, null)?.let {
            try { json.decodeFromString<List<PreparedSpeech>>(it) } catch (e: Exception) {
                Timber.e(e, "Speeches: could not read saved speeches"); null
            }
        }.orEmpty()
        return saved.ifEmpty { listOf(PreparedSpeech()) }
    }

    private fun save(list: List<PreparedSpeech>) {
        _speeches.value = list
        prefs.edit().putString(KEY, json.encodeToString(list)).apply()
    }

    /** Update a speech (or a ready-made answer's box height) by id. */
    fun update(id: String, change: (PreparedSpeech) -> PreparedSpeech) {
        if (isExample(id)) {
            _examples.value = _examples.value.map { if (it.id == id) change(it) else it }
            return
        }
        val before = _speeches.value.firstOrNull { it.id == id } ?: return
        val after = change(before)
        // Text changed under a running sequence would no longer match what is being heard.
        if (after.spokenText != before.spokenText && sequence.playingKey.value == playKey(id)) sequence.stop()
        save(_speeches.value.map { if (it.id == id) after else it })
    }

    /** New speech at the top; the existing ones are locked so they can't be changed by accident. */
    fun add() = save(listOf(PreparedSpeech()) + _speeches.value.map { it.copy(isLocked = true) })

    /** Removing the last speech leaves one blank pair of boxes, so the page is never empty. */
    fun delete(id: String) {
        if (sequence.playingKey.value == playKey(id)) sequence.stop()
        save(_speeches.value.filterNot { it.id == id }.ifEmpty { listOf(PreparedSpeech()) })
    }

    /**
     * Translate the side being shown into the other, if it has changed since the last translation. Returns
     * true when the card should switch to the other side (also when nothing needed translating), false on
     * failure (e.g. offline), with nothing lost.
     */
    suspend fun prepareSwitch(id: String, showingSpoken: Boolean): Boolean {
        val speech = (_speeches.value + _examples.value).firstOrNull { it.id == id } ?: return false
        if (sequence.playingKey.value == playKey(id)) sequence.stop()
        val source = if (showingSpoken) speech.spokenText else speech.nativeText
        val target = if (showingSpoken) speech.nativeText else speech.spokenText
        val synced = if (showingSpoken) speech.syncedSpoken else speech.syncedNative
        if (source.isBlank() || (source == synced && target.isNotEmpty())) return true

        val from = if (showingSpoken) spokenLang else nativeLang
        val to = if (showingSpoken) nativeLang else spokenLang
        val result = translation.translate(source, from, to).getOrNull() ?: return false
        update(id) {
            val spoken = if (showingSpoken) source else result
            val native = if (showingSpoken) result else source
            it.copy(spokenText = spoken, nativeText = native, syncedSpoken = spoken, syncedNative = native)
        }
        return true
    }

    // --- Playback: sentence by sentence through the shared player (TTS length limit; editing one sentence
    // only re-generates that one; car / lock-screen controls work) ---

    fun playOrPause(speech: PreparedSpeech) {
        val key = playKey(speech.id)
        if (sequence.playingKey.value == key) {
            if (sequence.isPaused.value) sequence.resume() else sequence.pause()
            return
        }
        val sentences = sentences(speech.spokenText)
        if (sentences.isEmpty()) return
        sequence.toggle(
            key = key,
            sentences = sentences,
            level = "Speeches",
            sheetName = "Speeches",
            isPremiumUser = policy.isPremium,
            preflight = {
                if (!policy.isPremium && limiter.doIForbidCall()) {
                    val reason = limiter.canMakeCallWithResult().failReason ?: SimpleRateLimiter.FailReason.HOURLY
                    _prompt.value = if (reason == SimpleRateLimiter.FailReason.DAILY) Prompt.DAILY else Prompt.HOURLY
                    false
                } else true
            },
            title = speech.title.ifBlank { null }
        )
    }

    private fun sentences(text: String): List<String> {
        val locale = if (spokenLang == TranslateLang.GERMAN) Locale.GERMAN else Locale.ENGLISH
        val it = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        val result = mutableListOf<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            text.substring(start, end).trim().takeIf { s -> s.isNotEmpty() }?.let(result::add)
            start = end
            end = it.next()
        }
        return result
    }

    fun stopPlayback() {
        if (sequence.playingKey.value?.startsWith(OWNER) == true) sequence.stop()
    }

    fun dismissPrompt() { _prompt.value = null }
    fun buyPremium(activity: Activity) { viewModelScope.launch { billing.launchPurchase(activity) } }

    override fun onCleared() { stopPlayback(); super.onCleared() }
}
