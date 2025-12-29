package com.goodstadt.john.language.exams.uti

import com.goodstadt.john.language.exams.screens.reference.SideQuestData

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.models.ReferenceCategory
import com.goodstadt.john.language.exams.models.ReferenceSubItem

fun buildSideQuestData(manager: AudioCacheManager): SideQuestData {

    // Helper to fetch stats and create a SubItem
    fun makeSubItem(uiTitle: String, documentKey: String, tabId: String): ReferenceSubItem {
        val stats = manager.getReferenceStats(documentKey)

        // Safety: If total is 0 (file not loaded yet), defaulting to 1 avoids div/0 errors in UI bars
        val safeTotal = if (stats.total > 0) stats.total else 1

        return ReferenceSubItem(
            title = uiTitle,
            viewed = stats.heard,
            total = safeTotal,
            documentId = documentKey,
            tabId = tabId
        )
    }

    // 1. CONJUGATIONS
    val conjugationsTabId = "conjugations"
    val conjugations = ReferenceCategory(
        title = "Conjugations",
        icon = Icons.Default.Transform, // Represents changing form
        description = "Essential verb variations. Understanding 'To Be' and 'To Have' covers 40% of English usage.",
        items = listOf(
            makeSubItem("To Be",   "EnglishConjugationsToBe", tabId = conjugationsTabId),
            makeSubItem("To Have", "EnglishConjugationsToHave", tabId = conjugationsTabId),
            makeSubItem("To Do",   "EnglishConjugationsToDo", tabId = conjugationsTabId),
            makeSubItem("To Get",  "EnglishConjugationsToGet", tabId = conjugationsTabId)
        )
    )

    // 2. ADJECTIVES
    val adjectivesTabId = "AdjectivesGroup"//"AdjectivesGroup"
    val adjectives = ReferenceCategory(
        title = "Adjectives",
        icon = Icons.Default.Palette, // Represents description/color
        description = "Descriptive words ordered by complexity. Focus on Intermediate for daily conversation.",
        items = listOf(
            makeSubItem("Basic",        "EnglishA1Adjectives", tabId = "AdjectivesGroup"),
            makeSubItem("Intermediate", "EnglishA2Adjectives", tabId = "AdjectivesGroup"),
            makeSubItem("Upper",        "EnglishB1Adjectives", tabId = "AdjectivesGroup"),
            makeSubItem("Advanced",     "EnglishB2Adjectives", tabId = "AdjectivesGroup"),
        )
//                items = listOf(
//                makeSubItem("Basic",        "AdjectivesGroup", tabId = adjectivesTabId),
//        makeSubItem("Intermediate", "AdjectivesGroup", tabId = adjectivesTabId),
//        makeSubItem("Upper",        "AdjectivesGroup", tabId = adjectivesTabId),
//        makeSubItem("Advanced",     "AdjectivesGroup", tabId = adjectivesTabId),
//    )
    )

    // 3. QUICK REFERENCE (List of individual categories)

    val quickRefs = listOf(
        ReferenceCategory(
            title = "Prepositions",
            icon = Icons.Default.SwapVert,
            description = "Words like 'in', 'on', 'at'. Tricky but essential for fluency.",
            items = listOf(makeSubItem("Main", "EnglishPrepositions","EnglishPrepositions"))
        ),
        ReferenceCategory(
            title = "Sounds the Same",
            icon = Icons.Default.Hearing,
            description = "Homophones (e.g. There, Their, They're).",
            items = listOf(makeSubItem("Main", "EnglishDefinitionsFormat1","EnglishDefinitionsFormat1"))
        ),
        ReferenceCategory(
            title = "Good vs Well",
            icon = Icons.Default.CheckCircle,
            description = "Common confusion between adjectives and adverbs.",
            items = listOf(makeSubItem("Main", "EnglishGoodVsWell","EnglishGoodVsWell"))
        )
    )

    return SideQuestData(conjugations, adjectives, quickRefs)
}