package com.goodstadt.john.language.exams.models

import com.goodstadt.john.language.exams.utils.ScreenTypeSerializer
import com.goodstadt.john.language.exams.utils.SheetDataTypeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the different types of data models that can be decoded.
 * The @SerialName annotation maps the JSON string to the enum case.
 */
@Serializable(with = SheetDataTypeSerializer::class) // ✅ Tell it to use our serializer
enum class SheetDataType(val serialName: String) {
    VOCAB_FILE("VocabFile"),
    FORMAT_1("Format1"),
    FORMAT_2("Format2"),
    FORMAT_3("Format3"),
    GRAMMAR_FILE("GrammarFile"),
    FIXED("fixed"),
    DICTIONARY("dictionary"),
    UNKNOWN("unknown") // ✅ ADD the unknown case
}

/**
 * Represents the different types of screens the app can navigate to.
 */
@Serializable(with = ScreenTypeSerializer::class) // ✅ Tell it to use our serializer
enum class ScreenType(val serialName: String) {
    FIXED_SCREEN("FixedScreen"),
    VOCAB_SCREEN("VocabScreen"),
    GROUPED_VOCAB_SCREEN("GroupedVocabScreen"),
    FORMAT_1_SCREEN("Format1Screen"),
    FORMAT_2_SCREEN("Format2Screen"),
    FORMAT_3_SCREEN("Format3Screen"),
    GRAMMAR_SCREEN("GrammarScreen"),
    GROUPED_FORMAT_2_SCREEN("GroupedFormat2Screen"),
    GROUPED_FORMAT_3_SCREEN("GroupedFormat3Screen"),
    DICTIONARY("Dictionary"),
    UNKNOWN("unknown") // ✅ ADD the unknown case
}