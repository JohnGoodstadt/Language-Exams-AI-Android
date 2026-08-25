package com.goodstadt.john.language.exams.data

import com.goodstadt.john.language.exams.BuildConfig

/**
 * Translates a ReadinessAudit sheet between its bundled ASSET filename and its logical FIRESTORE doc
 * name, so the app's download path can fall back to the bundled JSON when a sheet isn't (yet) on
 * Firestore.
 *
 *   asset   : Quizzes/ReadinessAudit/<Base>-<lang>.json   (e.g. .../AuditA2-1-de.json, BaselineAuditA2-1-en.json)
 *   logical : <Lang><Base>                                (e.g. GermanAuditA2-1, EnglishBaselineAuditA2-1)
 *
 * The audit sheets are fileFormat 10, handled by the shared format-7/10 routine. The "Audit" /
 * "BaselineAudit" stem already keeps them clash-free in the flat `sheets/<name>` collection; the
 * language prefix is per-FLAVOUR (de -> "German", en -> "English", …) to match every other sheet type.
 * Mirrors [UsageQuizSheetMapping]; matches the doc names produced by the Upload JSON "Baseline Quiz"
 * section.
 */
object ReadinessAuditSheetMapping {

    private const val FOLDER = "Quizzes/ReadinessAudit"

    /** Firestore logical-name language prefix for the current flavour. */
    private val languagePrefix: String = when (BuildConfig.FLAVOR) {
        "de" -> "German"
        "en" -> "English"
        "zh" -> "Chinese"
        else -> "German"
    }

    /** "AuditA2-1-de.json" (or bare base) -> "GermanAuditA2-1". */
    fun normalizeToLogicalName(fileNameOrBase: String): String {
        val base = fileNameOrBase.removeSuffix(".json").replace(Regex("-[a-z]{2}$"), "") // "AuditA2-1"
        return "$languagePrefix$base"
    }

    /**
     * "GermanAuditA2-1" -> "Quizzes/ReadinessAudit/AuditA2-1-de.json". Used as the download bundle
     * fallback; null if the language prefix is missing.
     */
    fun mapLogicalToResourceName(logicalName: String): String? {
        if (!logicalName.startsWith(languagePrefix)) return null
        val base = logicalName.removePrefix(languagePrefix) // "AuditA2-1"
        if (base.isBlank()) return null
        return "$FOLDER/$base-${BuildConfig.FLAVOR}.json"
    }
}
