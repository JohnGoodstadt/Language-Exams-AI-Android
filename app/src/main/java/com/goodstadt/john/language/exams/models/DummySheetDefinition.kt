package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

@Serializable
data class DummySheetDefinition(
    val title: String,
    val message: String,
    val version: Int
)