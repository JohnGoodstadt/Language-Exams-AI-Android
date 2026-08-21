package com.goodstadt.john.language.exams.data

import com.goodstadt.john.language.exams.BuildConfig

/**
 * Translates a grammar sheet between its bundled ASSET filename and its logical FIRESTORE doc name, so
 * the app's download path can fall back to the bundled JSON when a sheet isn't (yet) on Firestore.
 *
 *   asset   : Quizzes/Grammar/<level>/Grammar<Key>-<lang>.json   (e.g. .../A1/GrammarModalVerbs-de.json)
 *   logical : German<Key>                                         (e.g. GermanModalVerbs)
 *
 * The level (A1/A2/B1/B2) is not encoded in the logical name, so [mapLogicalToResourceName] recovers it
 * from [GrammarCatalog]. This is the fileFormat-7/10 equivalent of LanguageConfig.normalizeToLogicalName /
 * mapLogicalToResourceName (which serve the res/raw vocab files). NB: German naming reflects the current
 * de content; generalise the "German" prefix if other flavours publish grammar sheets.
 */
object GrammarSheetMapping {

    /** "GrammarModalVerbs-de.json" (or bare "GrammarModalVerbs-de") -> "GermanModalVerbs". */
    fun normalizeToLogicalName(fileNameOrKey: String): String {
        val s = fileNameOrKey.removeSuffix(".json").replace(Regex("-[a-z]{2}$"), "") // drop .json + -de/-en
        return if (s.startsWith("Grammar")) "German" + s.removePrefix("Grammar") else s
    }

    /**
     * "GermanModalVerbs" -> the bundled asset path, e.g. "Quizzes/Grammar/A1/GrammarModalVerbs-de.json",
     * or null if the key is not in [GrammarCatalog] (level unknown). Used as the download bundle fallback.
     */
    fun mapLogicalToResourceName(logicalName: String): String? {
        val fileKey = logicalName.removePrefix("German")
        val level = GrammarCatalog.rows.firstOrNull { it.fileKey == fileKey }?.level ?: return null
        return "Quizzes/Grammar/$level/Grammar$fileKey-${BuildConfig.FLAVOR}.json"
    }
}
