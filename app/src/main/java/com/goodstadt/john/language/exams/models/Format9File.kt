package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

/**
 * fileFormat-9 ("podcast" / multi-speaker dialogue script). A sheet is a list of SCENES, each a list of
 * LINES spoken by a SPEAKER (who maps to a VOICE). Mirrors the iOS Format9File and the bundled
 * german_podcast_football_trip_stuttgart_*.json. All fields default so a partial sheet still parses.
 */
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
