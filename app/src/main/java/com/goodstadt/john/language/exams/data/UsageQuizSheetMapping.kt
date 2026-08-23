package com.goodstadt.john.language.exams.data

import com.goodstadt.john.language.exams.BuildConfig

/**
 * Translates a UsageQuiz sheet between its bundled ASSET filename and its logical FIRESTORE doc name.
 *
 *   asset   : Quizzes/UsageQuiz/UsageQuiz<n><Level>-<lang>.json  (e.g. UsageQuiz1A1-de.json)
 *   logical : <Lang>UsageQuiz<n><Level>                          (e.g. GermanUsageQuiz1A1)
 *
 * Unlike SectionQuiz/Grammar, UsageQuiz already carries its area ("UsageQuiz") and level in the
 * filename, so the logical name is simply the language prefix + the base filename. The "UsageQuiz" area
 * keeps these clash-free from other content types in the flat `sheets/<name>` collection, and the base
 * is unique per file. UsageQuiz sheets are fileFormat 7/10, handled by the shared format-7/10 routine.
 *
 * The language prefix is per-FLAVOUR (de -> "German", en -> "English", …) to match how every other
 * sheet type is named in each flavour's Firestore project, so de stays "GermanUsageQuiz…" (already
 * uploaded) while en becomes "EnglishUsageQuiz…". Mirrors [SectionQuizSheetMapping].
 */
object UsageQuizSheetMapping {

    /** Firestore logical-name language prefix for the current flavour (matches the other sheet types). */
    private val languagePrefix: String = when (BuildConfig.FLAVOR) {
        "de" -> "German"
        "en" -> "English"
        "zh" -> "Chinese"
        else -> "German"
    }

    /** "UsageQuiz1A1-<lang>.json" -> "<Lang>UsageQuiz1A1" (e.g. de -> "GermanUsageQuiz1A1"). */
    fun normalizeToLogicalName(fileNameOrKey: String): String {
        val base = fileNameOrKey.removeSuffix(".json").replace(Regex("-[a-z]{2}$"), "") // "UsageQuiz1A1"
        return "$languagePrefix$base"
    }

    /**
     * "<Lang>UsageQuiz1A1" -> "Quizzes/UsageQuiz/UsageQuiz1A1-<lang>.json". Download bundle fallback.
     * NB: a few B2 files ship as -en; the app-side download should try -en if the -<flavour> file is absent.
     */
    fun mapLogicalToResourceName(logicalName: String): String {
        val base = logicalName.removePrefix(languagePrefix) // "UsageQuiz1A1"
        return "Quizzes/UsageQuiz/$base-${BuildConfig.FLAVOR}.json"
    }
}
