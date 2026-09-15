package com.goodstadt.john.language.exams.packages.ReferencePronouns

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** Styling helpers for the pattern-focused Pronouns screen. Pronoun words are highlighted in cyan. */
object PronounsClaudeStyle {

    /** The one accent used to highlight pronoun words (in the chain and in sentences). */
    val HIGHLIGHT = Color.Cyan

    fun caseLabel(case: String): String = when (case.trim().lowercase()) {
        "nom", "nominative" -> "Nominative"
        "acc", "accusative" -> "Accusative"
        "dat", "dative" -> "Dative"
        "gen", "genitive" -> "Genitive"
        else -> case
    }

    /** Short case tag, e.g. "Nom" / "Akk" / "Dat". */
    fun caseShort(case: String): String = when (case.trim().lowercase()) {
        "nom", "nominative" -> "Nom"
        "acc", "accusative" -> "Akk"
        "dat", "dative" -> "Dat"
        "gen", "genitive" -> "Gen"
        else -> case
    }

    /**
     * Build [sentence] with every whole-word occurrence of any word in [green] coloured green (bold).
     * Whole-word so "sie" isn't matched inside "diese"; German letters (ö/ä/ü/ß) count as letters.
     */
    fun highlightGreenWords(sentence: String, green: List<String>): AnnotatedString {
        val words = green.map { it.trim() }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return AnnotatedString(sentence)

        // Collect all [start, end) ranges to colour, then paint them in order.
        val ranges = ArrayList<IntRange>()
        for (w in words) {
            var from = 0
            while (from <= sentence.length - w.length) {
                val idx = sentence.indexOf(w, from)
                if (idx == -1) break
                val end = idx + w.length
                val leftOk = idx == 0 || !sentence[idx - 1].isLetter()
                val rightOk = end == sentence.length || !sentence[end].isLetter()
                if (leftOk && rightOk) ranges.add(idx until end)
                from = idx + 1
            }
        }
        if (ranges.isEmpty()) return AnnotatedString(sentence)
        ranges.sortBy { it.first }

        return buildAnnotatedString {
            var cursor = 0
            for (r in ranges) {
                if (r.first < cursor) continue // skip overlaps
                append(sentence.substring(cursor, r.first))
                withStyle(SpanStyle(color = HIGHLIGHT, fontWeight = FontWeight.Bold)) {
                    append(sentence.substring(r.first, r.last + 1))
                }
                cursor = r.last + 1
            }
            if (cursor < sentence.length) append(sentence.substring(cursor))
        }
    }
}
