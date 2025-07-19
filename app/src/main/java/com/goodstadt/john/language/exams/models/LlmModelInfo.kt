package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

@Serializable // <-- This annotation is essential
data class LlmModelInfo(
    val id: String,
    val title: String,
    val pricePerMillionInputTokens: Float,
    val isDefault: Boolean = false // It's good practice to have a default value
)