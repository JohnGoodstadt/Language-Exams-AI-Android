package com.goodstadt.john.language.exams.models

import com.goodstadt.john.language.exams.data.Gender
import kotlinx.serialization.Serializable

@Serializable
data class VoiceInfo(
    val id: String,
    val friendlyName: String,
    val gender: Gender,
)