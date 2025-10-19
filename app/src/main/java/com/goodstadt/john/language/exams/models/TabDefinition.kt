package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

@Serializable
data class TabsManifest(
    val tabs: List<TabDefinition>
)

@Serializable
data class SubTabDefinition(
    val title: String,
    val firestoreDocumentId: String
)

@Serializable
data class TabDefinition(
    val id: String,
    val title: String,
    val type: String, // e.g., "fixed_view", "dynamic_sheet", "grouped_sheet"

    // Stays nullable for simple tabs that don't have a direct document
    val firestoreDocumentId: String? = null,

    // ADDED: A nullable list to hold sub-tabs for the "grouped_sheet" type.
    // It is crucial that this is nullable (`? = null`) so that the JSON parser
    // doesn't crash when it encounters a tab that is NOT a grouped tab.
    val subTabs: List<SubTabDefinition>? = null
)
