package com.goodstadt.john.language.exams.data

import com.goodstadt.john.language.exams.BuildConfig

/**
 * Translates a Reference-tab fileFormat-7 quiz (currently "Adjectives") between its logical FIRESTORE doc
 * name and its bundled ASSET path, so the same download path used by the Grammar quiz can serve a Reference
 * sub-tab's quiz too.
 *
 *   logical : <Lang>Reference<Key><Level>Quiz          (e.g. GermanReferenceAdjectivesA1Quiz)
 *   asset   : Quizzes/Reference/<logicalName>.json      (e.g. Quizzes/Reference/GermanReferenceAdjectivesA1Quiz.json)
 *
 * The bundled filename stem IS the Firestore doc name (the upload sets the doc name to the file stem), so
 * unlike Grammar/Section sheets there is no separate "-de" asset name to normalise - the logical name and
 * the bundle stem are the same string.
 *
 * The language prefix is per-FLAVOUR (de -> "German", en -> "English", …) to match how every other sheet
 * type is named in each flavour's Firestore project.
 */
object ReferenceQuizSheetMapping {

    private val LEVELS = listOf("A1", "A2", "B1", "B2")
    private val LANG_PREFIXES = listOf("German", "English", "Chinese")

    /**
     * Reference groups (by their language-independent KEY, e.g. "Adjectives") that ship a fileFormat-7 quiz,
     * per flavour. Keys are English (they come from the sub-tab's firestoreDocumentId, which is English even
     * when the display title is localised, e.g. "Adjektive"). Only flavours whose bundle actually contains
     * the Quizzes/Reference JSON (and whose Firestore holds the docs) are enabled, so a group with no quiz
     * keeps its in-app generated quiz. Add a flavour/group here once its files ship.
     */
    private val GROUPS_WITH_QUIZ: Set<String> = when (BuildConfig.FLAVOR) {
        "de" -> setOf("Adjectives")
        else -> emptySet()
    }

    /** Firestore logical-name language prefix for the current flavour (matches the vocab/reference sheets). */
    private val languagePrefix: String = when (BuildConfig.FLAVOR) {
        "de" -> "German"
        "en" -> "English"
        "zh" -> "Chinese"
        else -> "German"
    }

    /** True if the group with language-independent [groupKey] (e.g. "Adjectives") has a Reference quiz JSON. */
    fun hasQuiz(groupKey: String): Boolean = groupKey in GROUPS_WITH_QUIZ

    /**
     * The language-independent group KEY from a sub-tab's doc id: "GermanA1Adjectives" -> "Adjectives".
     * Strips the flavour language prefix then the A1/A2/B1/B2 level. Null if the id isn't shaped that way.
     */
    fun keyFromDocId(docId: String?): String? {
        if (docId == null) return null
        val prefix = LANG_PREFIXES.firstOrNull { docId.startsWith(it) } ?: return null
        val afterPrefix = docId.removePrefix(prefix)            // "A1Adjectives"
        val level = LEVELS.firstOrNull { afterPrefix.startsWith(it) } ?: return null
        return afterPrefix.removePrefix(level).takeIf { it.isNotEmpty() } // "Adjectives"
    }

    /** The skill level from a sub-tab's doc id: "GermanA1Adjectives" -> "A1". Null if none present. */
    fun levelFromDocId(docId: String?): String? {
        if (docId == null) return null
        val afterPrefix = LANG_PREFIXES.firstOrNull { docId.startsWith(it) }
            ?.let { docId.removePrefix(it) } ?: return null
        return LEVELS.firstOrNull { afterPrefix.startsWith(it) }
    }

    /**
     * "Adjectives" + level "A1" -> "<Lang>ReferenceAdjectivesA1Quiz"
     * (e.g. de -> "GermanReferenceAdjectivesA1Quiz").
     */
    fun logicalName(groupKey: String, level: String): String =
        "${languagePrefix}Reference${groupKey}${level}Quiz"

    /** "<Lang>ReferenceAdjectivesA1Quiz" -> the bundled asset path used as the download fallback. */
    fun mapLogicalToResourceName(logicalName: String): String =
        "Quizzes/Reference/$logicalName.json"

    /** True if [logicalName] is a Reference quiz doc name (routes the bundle fallback to the Reference folder). */
    fun isReferenceQuiz(logicalName: String): Boolean =
        logicalName.contains("Reference") && logicalName.endsWith("Quiz")
}
