package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

@Serializable
data class VoiceInfo(
    val id: String,
    val friendlyName: String
)