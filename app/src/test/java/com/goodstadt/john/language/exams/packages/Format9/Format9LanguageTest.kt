package com.goodstadt.john.language.exams.packages.Format9

import com.goodstadt.john.language.exams.data.api.languageCodeForVoice
import com.goodstadt.john.language.exams.models.Format9File
import com.goodstadt.john.language.exams.models.Format9Line
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

internal fun podcastFixture(flavour: String): Format9File = Json { ignoreUnknownKeys = true }
    .decodeFromString(podcastFixtureText(flavour))

internal fun podcastFixtureText(flavour: String): String {
    val resource = if (flavour == "de") "german_podcast_football_trip_stuttgart" else "english_podcast_football_trip_manchester"
    val relative = "src/$flavour/res/raw/$resource.json"
    return requireNotNull(listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }).readText()
}

class Format9LanguageTest {
    @Test fun bothCurrentResourcesDecodeLosslesslyAndChooseTheSheetLanguage() {
        val json = Json { ignoreUnknownKeys = true }
        for ((flavour, count) in listOf("de" to 198, "en" to 213)) {
            val source = podcastFixtureText(flavour)
            val file = json.decodeFromString<Format9File>(source)
            val lines = file.scenes.flatMap { it.lines }
            assertEquals(12, file.scenes.size)
            assertEquals(count, lines.size)
            assertEquals(flavour, file.spokenLanguageCode)
            for (line in lines) {
                assertEquals(if (flavour == "de") line.de else line.en, file.spokenText(line))
                assertEquals(file.spokenText(line), file.displayText(line, false))
                assertEquals(if (flavour == "de") line.en else line.de, file.displayText(line, true))
            }
            assertEquals(json.parseToJsonElement(source), json.parseToJsonElement(json.encodeToString(file)))
        }
    }

    @Test fun localeVariantsAndAllSixLanguageFieldsResolveWithoutUsingTheDeviceLocale() {
        val line = Format9Line("line", 1, "alex", "Hallo", "Hello", "Hola", "你好", "Olá", "नमस्ते")
        for ((code, expected) in listOf(" DE-de " to "Hallo", "en_GB" to "Hello", "es-MX" to "Hola",
            "zh-Hans" to "你好", "pt_BR" to "Olá", "hi-IN" to "नमस्ते", "fr-FR" to "", "" to "")) {
            assertEquals(code, expected, line.textForLanguage(code))
        }
        val file = podcastFixture("de").copy(translationLanguage = "es")
        val untranslated = file.scenes.first().lines.first().copy(es = "")
        assertEquals(untranslated.de, file.displayText(untranslated, true))
        assertEquals(untranslated.de, file.spokenText(untranslated))
    }

    @Test fun theEnglishGuestExchangeInTheGermanStoryKeepsItsOwnEnglishVoices() {
        val file = podcastFixture("de")
        val speakers = file.speakers.associateBy { it.id }
        val englishLines = file.scenes.flatMap { it.lines }.filter { it.speakerId in setOf("alex_en", "young_woman_en") }
        assertEquals(8, englishLines.size)
        assertTrue(englishLines.all { file.spokenText(it) == it.de && speakers.getValue(it.speakerId).voiceId.startsWith("en-US-") })
    }

    @Test fun googleRequestLocaleMatchesTheActorVoiceIncludingChineseAndEnglishDialects() {
        assertEquals("en-US", languageCodeForVoice("en-US-Chirp-HD-D", "de-DE"))
        assertEquals("en-GB", languageCodeForVoice("en-GB-Chirp3-HD-Charon", "en-US"))
        assertEquals("de-DE", languageCodeForVoice("de-DE-Chirp3-HD-Charon", "en-GB"))
        assertEquals("cmn-CN", languageCodeForVoice("cmn-CN-Wavenet-B", "zh-CN"))
        assertEquals("de-DE", languageCodeForVoice("unknown", "de-DE"))
    }
}
