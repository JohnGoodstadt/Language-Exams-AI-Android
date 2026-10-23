package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * fileFormat-9 ("podcast" / multi-speaker dialogue script). A sheet is a list of SCENES, each a list of
 * LINES spoken by a SPEAKER (who maps to a VOICE). Mirrors the iOS Format9File and the bundled
 * German and English podcast resources, with text stored under two-letter language codes.
 */

@Serializable
data class Format9File(
    val fileformat: Int,
    val location: Int,
    val sheetname: String,
    val title: String,
    val updatedDate: Int,
    val description: String,
    val language: String,
    val translationLanguage: String,
    val level: String,
    val storyContext: Format9StoryContext,
    val playback: Format9Playback,
    val voices: List<Format9Voice>,
    val speakers: List<Format9Speaker>,
    val scenes: List<Format9Scene>
) {
    val spokenLanguageCode: String get() = baseLanguageCode(language)
    val translationLanguageCode: String get() = baseLanguageCode(translationLanguage)

    fun spokenText(line: Format9Line): String = line.textForLanguage(spokenLanguageCode)

    fun displayText(line: Format9Line, showTranslation: Boolean): String {
        val spoken = spokenText(line)
        return if (showTranslation) line.textForLanguage(translationLanguageCode).ifBlank { spoken } else spoken
    }

    /** Display text using an EXPLICIT translation language code (the user's choice); falls back to the
     *  spoken text when translation is off, the code is blank, or this line has no text for it. */
    fun displayText(line: Format9Line, translationCode: String, showTranslation: Boolean): String {
        val spoken = spokenText(line)
        return if (showTranslation && translationCode.isNotBlank())
            line.textForLanguage(translationCode).ifBlank { spoken } else spoken
    }

    /** The language codes (in de/en/es/zh/pt/hi order) that this script has text for in at least one line,
     *  excluding the spoken language — the translations the user can choose between. */
    val availableTranslationLanguageCodes: List<String> get() {
        val spoken = spokenLanguageCode
        return listOf("de", "en", "es", "zh", "pt", "hi").filter { code ->
            code != spoken && scenes.any { scene -> scene.lines.any { it.textForLanguage(code).isNotBlank() } }
        }
    }
}

@Serializable
data class Format9StoryContext(
    val origin: String,
    val destination: String,
    val changeAt: List<String>,
    val venue: String,
    val event: String,
    val fictional: Boolean,
    val note: String
)

@Serializable
data class Format9Playback(
    val pauseBetweenLinesMs: Int,
    val pauseBetweenScenesMs: Int
)

@Serializable
data class Format9Voice(
    val id: String,
    val description: String
)

@Serializable
data class Format9Speaker(
    val id: String,
    val name: String,
    val role: String,
    val voiceId: String
)

@Serializable
data class Format9Scene(
    val id: String,
    val sortorder: Int,
    val title: String,
    val place: String,
    val timeOfDay: String,
    val summary: String,
    val lines: List<Format9Line>
)

@Serializable
data class Format9Line(
    val id: String,
    val sortorder: Int,
    val speakerId: String,
    val de: String,
    val en: String,
    val es: String,
    val zh: String,
    val pt: String,
    val hi: String
) {
    /** Accept bare codes and locale identifiers, e.g. de-DE, en-GB and pt_BR. */
    fun textForLanguage(languageCode: String): String = when (baseLanguageCode(languageCode)) {
        "de" -> de
        "en" -> en
        "es" -> es
        "zh" -> zh
        "pt" -> pt
        "hi" -> hi
        else -> ""
    }
}

private fun baseLanguageCode(identifier: String): String =
    identifier.trim().replace('_', '-').substringBefore('-').lowercase(Locale.ROOT)

/*
@Serializable
data class Format9File(
    val fileformat: Int = 0,                 // Must be 9
    val location: Int = 0,
    val sheetname: String = "",
    val title: String = "",
    val updatedDate: Int = 0,
    val description: String = "",
    val language: String = "",               // e.g. "de-DE"
    val translationLanguage: String = "",    // e.g. "en"
    val level: String = "",                  // e.g. "A2-B1"
    val storyContext: Format9StoryContext = Format9StoryContext(),
    val playback: Format9Playback = Format9Playback(),
    val voices: List<Format9Voice> = emptyList(),
    val speakers: List<Format9Speaker> = emptyList(),
    val scenes: List<Format9Scene> = emptyList()
)

@Serializable
data class Format9StoryContext(
    val origin: String = "",
    val destination: String = "",
    val changeAt: List<String> = emptyList(),
    val venue: String = "",
    val event: String = "",
    val fictional: Boolean = false,
    val note: String = ""
)

@Serializable
data class Format9Playback(
    val pauseBetweenLinesMs: Int = 0,
    val pauseBetweenScenesMs: Int = 0
)

@Serializable
data class Format9Voice(
    val id: String = "",
    val description: String = ""
)

@Serializable
data class Format9Speaker(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val voiceId: String = ""                 // the actor's Google TTS voice key, e.g. "de-DE-Chirp-HD-F"
)

@Serializable
data class Format9Scene(
    val id: String = "",
    val sortorder: Int = 0,
    val title: String = "",
    val place: String = "",
    val timeOfDay: String = "",
    val summary: String = "",
    val lines: List<Format9Line> = emptyList()
)

@Serializable
data class Format9Line(
    val id: String = "",
    val sortorder: Int = 0,
    val speakerId: String = "",              // -> Format9Speaker.id
    val german: String = "",                 // sentence to display/play
    val english: String = ""                 // translation
)

 */
