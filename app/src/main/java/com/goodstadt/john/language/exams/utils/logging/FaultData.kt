package com.goodstadt.john.language.exams.utils.logging

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FaultContext(
    val secondaryText: String,
    val area: String
)

// Helper object to handle JSON conversion
object FaultDataEncoder {
    private val json = Json
    fun encode(secondaryText: String, area: String): String {
        val context = FaultContext(secondaryText, area)
        // We encode our context object into a JSON string
        return json.encodeToString(context)
    }
    fun decode(tag: String?): FaultContext? {
        // We will decode it in the FaultTree
        return tag?.let {
            try {
                json.decodeFromString<FaultContext>(it)
            } catch (e: Exception) {
                null
            }
        }
    }
}