package com.goodstadt.john.language.exams.models

import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Date

/**
 * The final, assembled domain model for a "Format2" sheet.
 */
@Serializable
data class Format2File(
    val fileformat: Int = 0,
    val location: Int = 0,
    val sheetname: String = "",
    val title: String = "",
    val description: String = "",
    val updatedDate: Int = 0,
    val data: List<Format2Level> = emptyList()
)

/**
 * Represents a single hierarchical level or section within a Format2 file.
 */
@Serializable
data class Format2Level(
    val description: String = "",
    val sortorder: Int = 0,
    val wordsAndSentences: List<Format2Entry> = emptyList()
)

/**
 * Represents a single word entry within a level.
 */
@Serializable
data class Format2Entry(
    val word: String = "",
    val definition: String = "",
    val sentences: List<Format2Sentence> = emptyList()
)

/**
 * Represents a single sentence.
 */
@Serializable
data class Format2Sentence(
    val sentence: String = ""
)


/**
* Represents the root document of a Format2 sheet in Firestore.
* Does not contain the sub-collection data.
*/
@Serializable
data class SheetHeaderFormat2DTO(
    val fileformat: Int = 0,
    val sheetname: String = "",
    val title: String = "",
    // Note: For Firestore's automatic `toObject()` with timestamps,
    // it's often easier to use `java.util.Date`.
    @Serializable(with = DateSerializer::class)
    @ServerTimestamp
    val updatedDate: Date? = null,
    val location: Int = 0,
    val description: String = ""
)

/**
 * Represents a single document from the flattened 'wordsAndSentences' sub-collection.
 */
@Serializable
data class Format2WordAndSentenceDTO(
    val title: String = "",
    val description: String = "",
    val sortorder: Int = 0,
    val explanation: String = "",
    val word: String = "",
    val definition: String = "",
    val sentences: List<String> = emptyList()
)

object DateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)

    // This converts a Date object TO a Long for serialization
    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeLong(value.time)
    }

    // This converts a Long FROM the JSON into a Date object
    override fun deserialize(decoder: Decoder): Date {
        return Date(decoder.decodeLong())
    }
}