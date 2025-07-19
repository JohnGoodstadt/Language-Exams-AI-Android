package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

// In a new file, e.g., data/LlmModel.kt

// Each enum constant is an object with its own properties.
//@Serializable
//enum class LlmModel(
//    val id: String,          // The official ID for the API call
//    val title: String,       // The user-friendly name for the UI
//    val pricePerMillionInputTokens: Float, // Example: price for 1M input tokens in USD
//    val isDefault: Boolean = false
//) {
//    // Define your available models here
//    //private val availableModels = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano")
//    GPT41("gpt-4.1", "GPT-4.1", 8.00f),
//    GPT41Mini("gpt-4.1-mini", "GPT-4.1 Mini", 01.60f),
//    GPT41Nano("gpt-4-1-nano", "GPT-4.1 Nano", 0.40f);
//
//    // A helper function to find an enum by its string ID, useful for initialization
//    companion object {
//        fun fromId(id: String): LlmModel? = entries.find { it.id == id }
//    }
//}

@Serializable // <-- This annotation is essential
data class LlmModelInfo(
    val id: String,
    val title: String,
    val pricePerMillionInputTokens: Float,
    val isDefault: Boolean = false // It's good practice to have a default value
)