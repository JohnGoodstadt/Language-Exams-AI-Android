package com.goodstadt.john.language.exams.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the different types of data models that can be decoded.
 * The @SerialName annotation maps the JSON string to the enum case.
 */
@Serializable
enum class DataType {
    @SerialName("VocabFile")
    VOCAB_FILE,

    @SerialName("Format1")
    FORMAT_1,

    @SerialName("GrammarFile")
    GRAMMAR_FILE,

    @SerialName("fixed")
    FIXED
}

/**
 * Represents the different types of screens the app can navigate to.
 */
@Serializable
enum class ScreenType {
    @SerialName("FixedScreen")
    FIXED_SCREEN,

    @SerialName("VocabScreen")
    VOCAB_SCREEN,

    @SerialName("GroupedVocabScreen")
    GROUPED_VOCAB_SCREEN,

    @SerialName("Format1Screen")
    FORMAT_1_SCREEN,

    @SerialName("GrammarScreen")
    GRAMMAR_SCREEN
}