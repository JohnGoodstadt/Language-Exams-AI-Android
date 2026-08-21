package com.goodstadt.john.language.exams.data

import com.goodstadt.john.language.exams.BuildConfig

/**
 * Translates a SectionQuiz sheet between its bundled ASSET filename and its logical FIRESTORE doc name.
 *
 *   asset   : Quizzes/SectionQuiz/<level>/WordQuiz<Key>-<lang>.json  (e.g. .../A1/WordQuizAdjectives1-de.json)
 *   logical : GermanSectionSheet<Level><Key>                         (e.g. GermanSectionSheetA1Adjectives1)
 *
 * The name is AREA-first ("SectionSheet") then LEVEL, so:
 *  - the AREA keeps section quizzes from clashing with other content types in the flat `sheets/<name>`
 *    collection, and sorts all section sheets together in the console;
 *  - the LEVEL then groups a level's sheets and stops A1/A2 sharing a topic+number from clashing.
 * Uniqueness only needs to hold within (area + level), which the source folders guarantee.
 * Mirrors [GrammarSheetMapping]. NB: "German" reflects the current de content.
 */
object SectionQuizSheetMapping {

    private const val AREA = "SectionSheet"
    private val LEVELS = listOf("A1", "A2", "B1", "B2")

    /** "WordQuizAdjectives1-de.json" (or bare key) + level "A1" -> "GermanSectionSheetA1Adjectives1". */
    fun normalizeToLogicalName(fileNameOrKey: String, level: String): String =
        "German$AREA$level${sectionKey(fileNameOrKey)}"

    /**
     * "GermanSectionSheetA1Adjectives1" -> "Quizzes/SectionQuiz/A1/WordQuizAdjectives1-de.json", or
     * null if the area/level prefix is missing/unrecognised. Used as the download bundle fallback.
     */
    fun mapLogicalToResourceName(logicalName: String): String? {
        val afterGerman = logicalName.removePrefix("German")           // "SectionSheetA1Adjectives1"
        if (!afterGerman.startsWith(AREA)) return null
        val afterArea = afterGerman.removePrefix(AREA)                 // "A1Adjectives1"
        val level = LEVELS.firstOrNull { afterArea.startsWith(it) } ?: return null
        val key = afterArea.removePrefix(level)                        // "Adjectives1"
        return "Quizzes/SectionQuiz/$level/WordQuiz$key-${BuildConfig.FLAVOR}.json"
    }

    /** "WordQuizAdjectives1-de.json" -> "Adjectives1" (drop .json, -de/-en and the "WordQuiz" prefix). */
    private fun sectionKey(fileNameOrKey: String): String {
        val s = fileNameOrKey.removeSuffix(".json").replace(Regex("-[a-z]{2}$"), "")
        return if (s.startsWith("WordQuiz")) s.removePrefix("WordQuiz") else s
    }
}
