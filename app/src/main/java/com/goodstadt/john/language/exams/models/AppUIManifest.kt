package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

// Using @Serializable on all of them allows for easy parsing with kotlinx.serialization

// 1. The root object for the entire manifest
@Serializable
data class AppUIManifest(
    val sheetRegistry: Map<String, SheetDefinition> = emptyMap(),
    val layouts: Layouts = Layouts()
)

// 2. The definition for a single piece of content in the registry
@Serializable
data class SheetDefinition(
    val title: String,
    val screenType: String, // We'll use strings for now, can be an enum later
    val dataType: String,   // We'll use strings for now, can be an enum later
    val firestoreDocumentId: String? = null,
    val subTabs: List<SubTabDefinition>? = null
)

// 3. The definition for a sub-tab within a grouped sheet
@Serializable
data class SubTabDefinition(
    val title: String,
    val firestoreDocumentId: String,
    val dataType: String
)

// 4. A container for all the different UI layouts
@Serializable
data class Layouts(
    val referenceTab: LayoutDefinition = LayoutDefinition(),
    val meTab: LayoutDefinition = LayoutDefinition()
)

// 5. The definition for a single layout (an ordered list of IDs)
@Serializable
data class LayoutDefinition(
    val order: List<String> = emptyList()
)