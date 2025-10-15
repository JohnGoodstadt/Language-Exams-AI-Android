package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

@Serializable
data class TabsManifest(
    val tabs: List<TabDefinition>
)

@Serializable
data class TabDefinition(
    val id: String,
    val title: String,
    val type: String,
    val firestoreDocumentId: String? = null
)