package com.goodstadt.john.language.exams.utils

import com.goodstadt.john.language.exams.models.ScreenType
import com.goodstadt.john.language.exams.models.SheetDataType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import timber.log.Timber

// --- 1. Custom Serializer for ScreenType ---

object ScreenTypeSerializer : KSerializer<ScreenType> {
    // The descriptor tells the library that this serializes to a simple String.
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ScreenType", PrimitiveKind.STRING)

    // This function handles SAVING the enum back to a string (encoding)
    override fun serialize(encoder: Encoder, value: ScreenType) {
        encoder.encodeString(value.serialName)
    }

    // ✅ THIS IS THE CORE LOGIC for LOADING the enum from a string (decoding)
    override fun deserialize(decoder: Decoder): ScreenType {
        val rawValue = decoder.decodeString()
        // Find the enum case that matches the raw string value.
        return ScreenType.values().find { it.serialName == rawValue }
            ?: run {
                // 💥 If no case matches (e.g., a typo like "FixdScreen"),
                // log a warning and return the .UNKNOWN fallback.
                Timber.w("Unknown ScreenType '$rawValue' found in JSON. Falling back to UNKNOWN.")
                ScreenType.UNKNOWN
            }
    }
}

// --- 2. Custom Serializer for SheetDataType ---

object SheetDataTypeSerializer : KSerializer<SheetDataType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SheetDataType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SheetDataType) {
        encoder.encodeString(value.serialName)
    }

    override fun deserialize(decoder: Decoder): SheetDataType {
        val rawValue = decoder.decodeString()
        return SheetDataType.values().find { it.serialName == rawValue }
            ?: run {
                Timber.w("Unknown SheetDataType '$rawValue' found in JSON. Falling back to UNKNOWN.")
                SheetDataType.UNKNOWN
            }
    }
}