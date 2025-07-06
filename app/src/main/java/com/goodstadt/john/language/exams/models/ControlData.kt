package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

// Note: The top-level object in your JSON should be an array of LanguagesControl,
// so we will parse it as a List<LanguagesControl>.

@Serializable
data class LanguagesControlFile(
    val id: String, // The "languages" id from the JSON
    val date: String,
    val name: String,
    val codes: List<LanguageCodeDetails>
)

@Serializable
data class LanguageCodeDetails(
    val code: String,
    val appleLocaleCode: String,
    val name: String,
    val shortname: String,
    val googlevoiceprefix: String,
    val defaultMaleVoice: String,
    val defaultFemaleVoice: String,
    val description: String,
    var currentSkillLevel: String, // 'var' because the Swift struct had it as a var
    val romanized: Boolean,
    val isTarget: Boolean,
    val isApp: Boolean,
    val exams: List<ExamDetails>
)

@Serializable
data class ExamDetails(
    val displayName: String,
    val json: String,
    val skillLevel: String
)