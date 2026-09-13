package com.goodstadt.john.language.exams.packages.ReferencePronouns

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class PronounSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var localeTag = Locale.getDefault().toLanguageTag()
    private var speechRate = 0.88f

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        applyConfiguration()
    }

    fun configure(locale: String, rate: Float) {
        localeTag = locale
        speechRate = rate
        applyConfiguration()
    }

    fun speak(sentence: String) {
        if (!ready || sentence.isBlank()) return
        engine?.speak(
            sentence,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "pronoun-reference-single",
        )
    }

    fun speakPattern(sentences: List<String>, pauseMs: Int) {
        if (!ready || sentences.isEmpty()) return
        engine?.stop()
        sentences.forEachIndexed { index, sentence ->
            engine?.speak(
                sentence,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "pronoun-reference-pattern-$index",
            )
            if (index < sentences.lastIndex) {
                engine?.playSilentUtterance(
                    pauseMs.toLong(),
                    TextToSpeech.QUEUE_ADD,
                    "pronoun-reference-pause-$index",
                )
            }
        }
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun applyConfiguration() {
        if (!ready) return
        engine?.language = Locale.forLanguageTag(localeTag)
        engine?.setSpeechRate(speechRate)
    }
}
