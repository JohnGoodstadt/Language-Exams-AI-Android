package com.goodstadt.john.language.exams.utils

import com.goodstadt.john.language.exams.data.Gender
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// This object teaches kotlinx.serialization how to handle the Gender enum
object GenderAsIntSerializer : KSerializer<Gender> {
    // Describes the data type in the JSON. It's an Int.
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Gender", PrimitiveKind.INT)

    // How to READ from JSON (Int) and create an Enum (Gender)
    override fun deserialize(decoder: Decoder): Gender {
        val intValue = decoder.decodeInt()
        return when (intValue) {
            0 -> Gender.FEMALE
            1 -> Gender.MALE
            else -> Gender.UNKNOWN
        }
    }

    // How to WRITE an Enum (Gender) to JSON (Int)
    // (Useful if you ever need to generate this JSON)
    override fun serialize(encoder: Encoder, value: Gender) {
        val intValue = when (value) {
            Gender.FEMALE -> 0
            Gender.MALE -> 1
            Gender.UNKNOWN -> -1 // Or another default value
        }
        encoder.encodeInt(intValue)
    }
}