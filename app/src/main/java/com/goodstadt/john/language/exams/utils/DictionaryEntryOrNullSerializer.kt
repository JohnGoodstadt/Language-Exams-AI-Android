package com.goodstadt.john.language.exams.utils

import com.goodstadt.john.language.exams.packages.dailydictionary.DictionaryEntry
import kotlinx.serialization.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

object DictionaryEntryOrNullSerializer : KSerializer<DictionaryEntry?> {

    override val descriptor = DictionaryEntry.serializer().descriptor

    override fun deserialize(decoder: Decoder): DictionaryEntry? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()

        return if (element is JsonObject) {
            jsonDecoder.json.decodeFromJsonElement(
                DictionaryEntry.serializer(),
                element
            )
        } else {
            // String / null / anything else → treat as not set
            null
        }
    }

    override fun serialize(encoder: Encoder, value: DictionaryEntry?) {
        val jsonEncoder = encoder as? JsonEncoder ?: return

        if (value == null) {
            jsonEncoder.encodeNull()
        } else {
            jsonEncoder.encodeJsonElement(
                jsonEncoder.json.encodeToJsonElement(
                    DictionaryEntry.serializer(),
                    value
                )
            )
        }
    }
}