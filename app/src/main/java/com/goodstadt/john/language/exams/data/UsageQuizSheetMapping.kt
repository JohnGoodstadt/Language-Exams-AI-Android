package com.goodstadt.john.language.exams.data

import com.goodstadt.john.language.exams.BuildConfig

/**
 * Translates a UsageQuiz sheet between its bundled ASSET filename and its logical FIRESTORE doc name.
 *
 *   asset   : Quizzes/UsageQuiz/UsageQuiz<n><Level>-<lang>.json  (e.g. UsageQuiz1A1-de.json)
 *   logical : GermanUsageQuiz<n><Level>                          (e.g. GermanUsageQuiz1A1)
 *
 * Unlike SectionQuiz/Grammar, UsageQuiz already carries its area ("UsageQuiz") and level in the
 * filename, so the logical name is simply "German" + the base filename. The "UsageQuiz" area keeps
 * these clash-free from other content types in the flat `sheets/<name>` collection, and the base is
 * unique per file. UsageQuiz sheets are fileFormat 7/10, handled by the shared format-7/10 routine.
 * Mirrors [SectionQuizSheetMapping]. NB: "German" reflects the current de content.
 */
object UsageQuizSheetMapping {

    /** "UsageQuiz1A1-de.json" -> "GermanUsageQuiz1A1". */
    fun normalizeToLogicalName(fileNameOrKey: String): String {
        val base = fileNameOrKey.removeSuffix(".json").replace(Regex("-[a-z]{2}$"), "") // "UsageQuiz1A1"
        return "German$base"
    }

    /**
     * "GermanUsageQuiz1A1" -> "Quizzes/UsageQuiz/UsageQuiz1A1-de.json". Download bundle fallback.
     * NB: a few B2 files ship as -en; the app-side download should try -en if the -<flavour> file is absent.
     */
    fun mapLogicalToResourceName(logicalName: String): String {
        val base = logicalName.removePrefix("German") // "UsageQuiz1A1"
        return "Quizzes/UsageQuiz/$base-${BuildConfig.FLAVOR}.json"
    }
}
