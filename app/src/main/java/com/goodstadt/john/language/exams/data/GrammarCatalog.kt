package com.goodstadt.john.language.exams.data

/**
 * One canonical grammar category and the CEFR levels it is taught at.
 *
 * [fileKey] is the language-independent token in the asset filename
 * `Quizzes/Grammar/<level>/Grammar<fileKey>-<lang>.json` (e.g. "PresentSimple"). The English
 * [displayName] is what the Focus screen shows and what lands in the (category|level) tally, so it
 * stays identical across flavours - the `de` files reuse the same English filename/key with German
 * question content inside.
 */
data class GrammarCategory(
    val displayName: String,
    val fileKey: String,
    val levels: List<String>
)

/** A single (category, level) cell of the grammar grid - one practisable quiz. */
data class GrammarRow(
    val category: String,
    val level: String,
    val fileKey: String
)

/**
 * The canonical grammar-category list that drives the Focus "all categories" view and maps a
 * category to its asset filename. It is a code-level list (not derived from scanning the JSON)
 * precisely so a category still shows up before its quiz file has any questions.
 *
 * This is the regularised Cambridge/Oxford/Pearson/British Council/EGP-style set. Seeded here with
 * the first two tense rows (2 categories per level); extend as more grid cells get quiz files.
 */
object GrammarCatalog {

    val categories: List<GrammarCategory> = listOf(
        GrammarCategory("Present Simple", "PresentSimple", listOf("A1")),
        GrammarCategory("Past Simple", "PastSimple", listOf("A1")),
        GrammarCategory("Present Continuous", "PresentContinuous", listOf("A2")),
        GrammarCategory("Past Continuous", "PastContinuous", listOf("A2")),
        GrammarCategory("Present Perfect", "PresentPerfect", listOf("B1")),
        GrammarCategory("Past Perfect", "PastPerfect", listOf("B1")),
        GrammarCategory("Present Perfect Continuous & Aspect", "PresentPerfectContinuousAspect", listOf("B2")),
        GrammarCategory("Narrative Tenses", "NarrativeTenses", listOf("B2"))
    )

    /** Flat (category, level) rows in catalogue order, for browsing / practising. */
    val rows: List<GrammarRow> =
        categories.flatMap { c -> c.levels.map { GrammarRow(c.displayName, it, c.fileKey) } }

    /** The filename key for a display category, or null if it isn't a catalogue category. */
    fun fileKeyFor(category: String): String? =
        categories.firstOrNull { it.displayName == category }?.fileKey

    /** The display category for a filename key, or the key itself if unknown. */
    fun displayNameFor(fileKey: String): String =
        categories.firstOrNull { it.fileKey == fileKey }?.displayName ?: fileKey
}
