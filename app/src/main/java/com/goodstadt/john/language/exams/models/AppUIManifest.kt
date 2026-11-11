package com.goodstadt.john.language.exams.models

import kotlinx.serialization.SerialName
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
// Fields default rather than being required, so one entry written for the other platform (e.g. an iOS
// grouped screen with no parent sheetDataType) can't fail the whole manifest.
data class SheetDefinition(
    val title: String = "",
    val screenType: ScreenType = ScreenType.UNKNOWN,
    val sheetDataType: SheetDataType = SheetDataType.UNKNOWN,
    val firestoreDocumentId: String? = null,
    val subTabs: List<SubTabDefinition>? = null,
    val requiredLocale: String? = null //for Spanish/Mandarin specific sheets
)

// 3. The definition for a sub-tab within a grouped sheet
@Serializable
data class SubTabDefinition(
    val title: String,
    val firestoreDocumentId: String,
    val sheetDataType: SheetDataType = SheetDataType.UNKNOWN
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