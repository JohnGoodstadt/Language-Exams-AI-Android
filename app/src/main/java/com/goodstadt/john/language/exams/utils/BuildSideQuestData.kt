package com.goodstadt.john.language.exams.uti

import com.goodstadt.john.language.exams.screens.reference.SideQuestData

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.models.AppUIManifest
import com.goodstadt.john.language.exams.models.ReferenceCategory
import com.goodstadt.john.language.exams.models.ReferenceSubItem

fun buildSideQuestData(manager: AudioCacheManager, manifest: AppUIManifest?): SideQuestData {

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
    )

    // 3. ✅ NEW: PAIRS (Tab ID: "PairsGroup")
    // This matches your Remote Config 'sheetRegistry' key for the grouped screen
//    val pairsTabId = "PairsGroup"
//
//    val pairs = ReferenceCategory(
//        title = "Word Pairs",
//        // CompareArrows is perfect for "X vs Y"
//        icon = Icons.AutoMirrored.Filled.CompareArrows,
//        description = "Commonly confused words. Learn the subtle differences.",
//        items = listOf(
//            makeSubItem("Good vs Well",   "EnglishGoodVsWell",   tabId = pairsTabId),
//            makeSubItem("Say vs Tell",    "EnglishSayVsTell",    tabId = pairsTabId),
//            makeSubItem("Speak vs Talk",  "EnglishSpeakVsTalk",  tabId = pairsTabId),
//            makeSubItem("Hear vs Listen", "EnglishHearVsListen", tabId = pairsTabId)
//        )
//    )

    // 3. ✅ DYNAMIC PAIRS GROUP
    val pairsTabId = "PairsGroup" // This must match the key in sheetRegistry
    var pairsItems: List<ReferenceSubItem> = emptyList()

    // A. Try Remote Config
    if (manifest != null) {
        val pairsDef = manifest.sheetRegistry[pairsTabId]
        val subTabs = pairsDef?.subTabs

        if (!subTabs.isNullOrEmpty()) {
            pairsItems = subTabs.mapNotNull { tab ->
                val docId = tab.firestoreDocumentId
                if (docId != null) {
                    makeSubItem(tab.title, docId, pairsTabId)
                } else null
            }
        }
    }

    // B. Fallback (If remote config missing)
    if (pairsItems.isEmpty()) {
        pairsItems = listOf(
            makeSubItem("Good vs Well",   "EnglishGoodVsWell",   pairsTabId),
            makeSubItem("Say vs Tell",    "EnglishSayVsTell",    pairsTabId),
            makeSubItem("Speak vs Talk",  "EnglishSpeakVsTalk",  pairsTabId),
            makeSubItem("Hear vs Listen", "EnglishHearVsListen", pairsTabId)
        )
    }

    val pairs = ReferenceCategory(
        title = "Word Pairs",
        icon = Icons.AutoMirrored.Filled.CompareArrows,
        description = "Commonly confused words. Learn the subtle differences.",
        items = pairsItems // ✅ Use dynamic list
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
        )//,
//        ReferenceCategory(
//            title = "Good vs Well",
//            icon = Icons.Default.CheckCircle,
//            description = "Common confusion between adjectives and adverbs.",
//            items = listOf(makeSubItem("Main", "EnglishGoodVsWell","EnglishGoodVsWell"))
//        )
    )

    return SideQuestData(conjugations, adjectives, pairs,quickRefs)
}