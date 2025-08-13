package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

// This is the root object of the JSON response
@Serializable
data class VoicesResponse(
    val voices: List<GoogleVoiceInfo>
)

// This represents a single voice object in the list
@Serializable
data class GoogleVoiceInfo(
    val languageCodes: List<String>,
    val name: String,
    val ssmlGender: String, // e.g., "FEMALE", "MALE", "NEUTRAL"
    val naturalSampleRateHertz: Int
)