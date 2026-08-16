package com.goodstadt.john.language.exams.data

/**
 * Chinese (zh flavour) grammar catalogue. Chinese ships no grammar quizzes, so this is empty; it
 * exists only so the shared main code that references [GrammarCatalog] compiles for the zh flavour.
 * Shared [GrammarCategory] / [GrammarRow] types live in main.
 */
object GrammarCatalog {

    val categories: List<GrammarCategory> = emptyList()

    val rows: List<GrammarRow> =
        categories.flatMap { c -> c.levels.map { GrammarRow(c.displayName, it, c.fileKey, c.shortLabel) } }

    fun fileKeyFor(category: String): String? =
        categories.firstOrNull { it.displayName == category }?.fileKey

    fun displayNameFor(fileKey: String): String =
        categories.firstOrNull { it.fileKey == fileKey }?.displayName ?: fileKey
}
