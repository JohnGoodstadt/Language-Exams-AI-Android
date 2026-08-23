package com.goodstadt.john.language.exams.data

import com.goodstadt.john.language.exams.BuildConfig

/**
 * Translates a grammar sheet between its bundled ASSET filename and its logical FIRESTORE doc name, so
 * the app's download path can fall back to the bundled JSON when a sheet isn't (yet) on Firestore.
 *
 *   asset   : Quizzes/Grammar/<level>/Grammar<Key>-<lang>.json  (e.g. .../A1/GrammarModalVerbs-de.json)
 *   logical : German<Level><Key>                                (e.g. GermanA1ModalVerbs)
 *
 * The LEVEL is embedded in the logical name (German**A1**ModalVerbs) so grammar sheets stay in the flat
 * `sheets/<name>` collection with the same read code as every other sheet, an A1/A2/B1/B2 title can't
 * clash across levels, and same-level names sort together in the console. This is the fileFormat-7/10
 * equivalent of LanguageConfig.normalizeToLogicalName / mapLogicalToResourceName (which serve res/raw
 * vocab).
 *
 * The language prefix is per-FLAVOUR (de -> "German", en -> "English", …) to match how every other
 * sheet type is named in each flavour's Firestore project (GermanA1Vocab / EnglishA1Vocab,
 * GermanA1Adjectives / EnglishA1Adjectives, …). So de grammar stays "GermanA1ModalVerbs" (already
 * uploaded) while en grammar becomes "EnglishA1ModalVerbs".
 */
object GrammarSheetMapping {

    private val LEVELS = listOf("A1", "A2", "B1", "B2")

    /** Firestore logical-name language prefix for the current flavour (matches the vocab/reference sheets). */
    private val languagePrefix: String = when (BuildConfig.FLAVOR) {
        "de" -> "German"
        "en" -> "English"
        "zh" -> "Chinese"
        else -> "German"
    }

    /**
     * "GrammarModalVerbs-<lang>.json" (or bare key) + level "A1" -> "<Lang>A1ModalVerbs"
     * (e.g. de -> "GermanA1ModalVerbs", en -> "EnglishA1ModalVerbs").
     */
    fun normalizeToLogicalName(fileNameOrKey: String, level: String): String =
        "$languagePrefix$level${grammarKey(fileNameOrKey)}"

    /**
     * "<Lang>A1ModalVerbs" -> the bundled asset path "Quizzes/Grammar/A1/GrammarModalVerbs-<lang>.json",
     * or null if the level prefix is missing/unrecognised. Used as the download bundle fallback.
     */
    fun mapLogicalToResourceName(logicalName: String): String? {
        val rest = logicalName.removePrefix(languagePrefix)  // "A1ModalVerbs"
        val level = LEVELS.firstOrNull { rest.startsWith(it) } ?: return null
        val key = rest.removePrefix(level)                   // "ModalVerbs"
        return "Quizzes/Grammar/$level/Grammar$key-${BuildConfig.FLAVOR}.json"
    }

    /** "GrammarModalVerbs-de.json" -> "ModalVerbs" (drop .json, -de/-en suffix and the "Grammar" prefix). */
    private fun grammarKey(fileNameOrKey: String): String {
        val s = fileNameOrKey.removeSuffix(".json").replace(Regex("-[a-z]{2}$"), "")
        return if (s.startsWith("Grammar")) s.removePrefix("Grammar") else s
    }
}
