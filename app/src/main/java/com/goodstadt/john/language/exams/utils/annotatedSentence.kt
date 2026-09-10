package com.goodstadt.john.language.exams.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.goodstadt.john.language.exams.ui.theme.Teal

/**
 * Conjugation tables for the verbs used on the Word Pairs ("Wortpaare") screen. Highlighting a verb in a
 * sentence can't be a plain match on the infinitive: German conjugation changes the surface form (kennen ->
 * kenne/kennst/kannte/gekannt; wissen -> weiß/wusste/gewusst; bringen -> brachte/gebracht; bitten ->
 * bat/gebeten), so "hören" never appears literally in "Ich höre Musik". Since the set of verbs on this
 * screen is fixed and small, we list every form we expect to see (present, preterite, perfect participle,
 * imperative, common subjunctive II) and highlight whichever one the sentence actually uses.
 *
 * Keyed by the infinitive as it appears in the sheet's `word` field, lower-cased. Both the umlaut spelling
 * ("hören") and the ASCII spelling ("hoeren") are registered so either form of the key resolves. Separable
 * "zuhören" shares the finite "hör…" tokens (the particle "zu" separates in a main clause) and adds its
 * joined non-finite forms (zuhören/zuzuhören/zugehört); the bare separated "zu" is deliberately NOT listed
 * because it is far too common (infinitive marker / preposition) to highlight safely.
 */
private val GERMAN_VERB_FORMS: Map<String, Set<String>> = run {
    val map = HashMap<String, Set<String>>()
    fun add(forms: Set<String>, vararg keys: String) {
        keys.forEach { map[it.lowercase()] = forms }
    }

    add(
        setOf(
            "kennen", "kenne", "kennst", "kennt",
            "kannte", "kanntest", "kannten", "kanntet",
            "gekannt", "kenn", "kennend"
        ),
        "kennen"
    )
    add(
        setOf(
            "wissen", "weiß", "weißt", "weiss", "weisst", "wisst",
            "wusste", "wusstest", "wussten", "wusstet",
            "wüsste", "wüsstest", "wüssten", "wüsstet",
            "gewusst", "wisse", "wissend"
        ),
        "wissen"
    )
    add(
        setOf(
            "fragen", "frage", "fragst", "fragt", "frägt",
            "fragte", "fragtest", "fragten", "fragtet",
            "gefragt", "frag", "fragend"
        ),
        "fragen"
    )
    add(
        setOf(
            "bitten", "bitte", "bittest", "bittet",
            "bat", "batest", "baten", "batet",
            "bäte", "bätest", "bäten", "bätet",
            "gebeten", "bittend"
        ),
        "bitten"
    )
    add(
        setOf(
            "bringen", "bringe", "bringst", "bringt",
            "brachte", "brachtest", "brachten", "brachtet",
            "brächte", "brächtest", "brächten", "brächtet",
            "gebracht", "bring", "bringend"
        ),
        "bringen"
    )
    add(
        setOf(
            "holen", "hole", "holst", "holt", "holen",
            "holte", "holtest", "holten", "holtet",
            "geholt", "hol", "holend"
        ),
        "holen"
    )
    add(
        setOf(
            "hören", "höre", "hörst", "hört",
            "hörte", "hörtest", "hörten", "hörtet",
            "gehört", "hör", "hörend"
        ),
        "hören", "hoeren"
    )
    add(
        // Separable: in a main clause the particle "zu" splits off, leaving the "hör…" tokens; in a
        // subordinate clause the verb rejoins at the end as "zuhört" etc. Cover both, plus joined non-finite.
        setOf(
            "höre", "hörst", "hört", "hören",
            "hörte", "hörtest", "hörten", "hörtet", "hör",
            "zuhöre", "zuhörst", "zuhört", "zuhören",
            "zuhörte", "zuhörtest", "zuhörten", "zuhörtet",
            "zuzuhören", "zugehört", "zuhörend"
        ),
        "zuhören", "zuhoeren"
    )

    map
}

/** Whole-word (case-insensitive) occurrences of [form] in [sentence]. A match must not be flanked by
 *  letters, so "holt" is not found inside "wiederholt" nor "bat" inside "Arbeit". Char.isLetter() covers
 *  German letters (ö, ä, ü, ß) too. */
private fun wholeWordRanges(sentence: String, form: String): List<IntRange> {
    if (form.isEmpty()) return emptyList()
    val ranges = ArrayList<IntRange>()
    var start = 0
    while (start <= sentence.length - form.length) {
        val idx = sentence.indexOf(form, start, ignoreCase = true)
        if (idx == -1) break
        val end = idx + form.length
        val leftOk = idx == 0 || !sentence[idx - 1].isLetter()
        val rightOk = end == sentence.length || !sentence[end].isLetter()
        if (leftOk && rightOk) ranges.add(idx until end)
        start = idx + 1
    }
    return ranges
}

/**
 * A Composable helper that builds an AnnotatedString, highlighting specific words
 * within a sentence. This is the Compose equivalent of your `styledSentence` for iOS.
 *
 * For a highlight token that is a known German verb (see [GERMAN_VERB_FORMS] — the Word Pairs verbs),
 * every conjugated form is matched on whole-word boundaries, so "hören" highlights "höre", "hört",
 * "gehört", etc. For any other token (e.g. the homophones on the "Sounds the Same" screen) it falls back
 * to the original case-insensitive substring match, so those callers are unchanged.
 *
 * @param sentence The full sentence to display.
 * @param wordsToHighlight A comma-separated string of words to find and style (e.g., "well, good").
 * @param highlightColor The color to apply to the highlighted words.
 * @return An `AnnotatedString` with the specified words underlined and colored.
 */
@Composable
fun annotatedSentenceByWords(
    sentence: String,
    wordsToHighlight: String,
    highlightColor: Color = Teal // Use your theme's accent color here if you have one
): AnnotatedString {
    // 1. Get a clean list of the individual words to search for.
    val words = wordsToHighlight.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    val style = SpanStyle(
        color = highlightColor,
        textDecoration = TextDecoration.Underline,
        fontWeight = FontWeight.Bold // Bolding can help with emphasis
    )

    // 2. Use the `buildAnnotatedString` builder, which is the equivalent of Swift's `AttributedString`.
    return buildAnnotatedString {
        // a) Append the original sentence text.
        append(sentence)

        // b) Loop through each word we need to find and style.
        words.forEach { word ->
            val verbForms = GERMAN_VERB_FORMS[word.lowercase()]

            if (verbForms != null) {
                // Known verb: highlight whichever conjugated form appears (whole-word only).
                verbForms.forEach { form ->
                    wholeWordRanges(sentence, form).forEach { range ->
                        addStyle(style, range.first, range.last + 1)
                    }
                }
            } else {
                // Fallback: original case-insensitive substring match (unchanged behaviour).
                var startIndex = 0
                while (startIndex < sentence.length) {
                    val index = sentence.indexOf(word, startIndex, ignoreCase = true)
                    if (index == -1) break
                    addStyle(style, index, index + word.length)
                    startIndex = index + word.length
                }
            }
        }
    }
}
