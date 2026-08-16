package com.goodstadt.john.language.exams.data

// GrammarCategory / GrammarRow are the SHARED grammar-catalogue types used by main code.
// The `object GrammarCatalog` that lists the categories is per-flavour (en/de/zh), living in
// each flavour's config/ directory - like SectionQuizKeyMap - so each language has its own set.

/**
 * One canonical grammar category and the CEFR level it is taught at.
 *
 * [fileKey] is the language-independent token in the asset filename
 * `Quizzes/Grammar/<level>/Grammar<fileKey>-<lang>.json` (e.g. "PresentSimple"). The English
 * [displayName] is what lands in the (category|level) tally and MUST match the JSON `category`
 * field exactly. [shortLabel] is a compact label for tight UI on small devices (e.g. a horizontal
 * picker) - unique within each level; where two would collide they are numbered ("Present 1",
 * "Present 2").
 */
data class GrammarCategory(
    val displayName: String,
    val fileKey: String,
    val shortLabel: String,
    val levels: List<String>
)

/** A single (category, level) cell of the grammar grid - one practisable quiz. */
data class GrammarRow(
    val category: String,
    val level: String,
    val fileKey: String,
    val shortLabel: String
)
