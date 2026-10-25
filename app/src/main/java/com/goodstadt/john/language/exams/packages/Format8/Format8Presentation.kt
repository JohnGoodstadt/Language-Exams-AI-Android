package com.goodstadt.john.language.exams.packages.Format8

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.goodstadt.john.language.exams.models.Format8File
import java.text.BreakIterator
import java.util.Locale

/** Display transformations only; stored examples and audio text remain unchanged. */
object Format8Presentation {
    val highlightColor = Color(0xFF008B83)
    private val abbreviations = listOf(
        "(singular)" to "(s)", "(plural)" to "(p)", "(formal)" to "(f)",
        "(Masculine Accusative)" to "(m.a.)", "(Masculine Dative)" to "(m.d.)"
    )
    fun compactEnglish(text: String): String = abbreviations.fold(text) { result, (from, to) ->
        result.replace(from, to, ignoreCase = true)
    }

    fun displaySentence(text: String, locale: Locale = Locale.GERMAN): String {
        val trimmed = text.trim()
        val annotation = Regex("\\s*\\([^()]*\\)$").find(trimmed)
        val sentence = if (annotation != null) trimmed.substring(0, annotation.range.first) else trimmed
        if (!sentence.endsWith(".") || sentence.endsWith("..")) return trimmed
        val iterator = BreakIterator.getSentenceInstance(locale).apply { setText(sentence) }
        var start = iterator.first()
        var count = 0
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (sentence.substring(start, end).any { it.isLetterOrDigit() }) count++
            start = end
            end = iterator.next()
        }
        return if (count == 1) sentence.dropLast(1) + (annotation?.value ?: "") else trimmed
    }

    fun tokenRegex(word: String): Regex = Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(word)}(?![\\p{L}\\p{N}_])")

    fun highlight(text: String, terms: Collection<String>, underline: Boolean = false): AnnotatedString = buildAnnotatedString {
        append(text)
        val deco = if (underline) TextDecoration.Underline else null
        terms.filter { it.isNotEmpty() }.distinct().forEach { term ->
            tokenRegex(term).findAll(text).forEach { match ->
                addStyle(SpanStyle(color = highlightColor, textDecoration = deco), match.range.first, match.range.last + 1)
            }
        }
    }

    fun introduction(file: Format8File): AnnotatedString {
        val entries = file.sections.flatMap { it.entries }
        val terms = mutableSetOf("ich", "du", "er", "sie", "es", "wir", "ihr", "Sie", "mich", "dich", "ihn", "uns", "euch", "mir", "dir", "ihm", "ihnen", "Ihnen")
        terms += entries.flatMap { it.examples }.map { it.key }
        entries.forEach { entry ->
            entry.header.substringBefore("—").split("/").map { it.trim() }
                .filter { it.isNotEmpty() && it.all(Char::isLetter) }.forEach { terms += it }
        }
        return highlight(file.intro, terms)
    }

    fun speakAloudGapMillis(text: String): Long = 1000L + text.codePointCount(0, text.length) * 125L
}
