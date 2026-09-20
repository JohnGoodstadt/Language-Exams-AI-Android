package com.goodstadt.john.language.exams.packages.Conjugations

import com.goodstadt.john.language.exams.models.Format7or10Section
import com.goodstadt.john.language.exams.models.Format7or10Word
import com.goodstadt.john.language.exams.packages.ReferencePronouns.PronounCategory

/**
 * Builds a fill-in-the-blank quiz for ONE conjugation TENSE at RUNTIME from the fileFormat-6 teaching
 * category (a tense such as Präsens / Present), so repeat attempts show a fresh set. Sibling of
 * [com.goodstadt.john.language.exams.packages.ReferencePronouns.PronounQuizGenerator]; the output is the
 * same fileFormat-7 shape ([Format7or10Section]) fed to the shared quiz screen via `loadPrebuiltQuiz`.
 *
 * For each example sentence it blanks the **person-conjugated word** — the FIRST word of the sentence's
 * `green` list, i.e. the part that changes with the subject (bin/bist/…, or the auxiliary werde/habe/…
 * in a compound tense). The distractors are the SAME slot's forms for the OTHER persons, taken from the
 * tense's own paradigm (the "How it works" pattern's `forms`).
 *
 * **Blind-swap guard (the whole safety story).** Different persons often share a form — wir/sie both
 * "haben", ich/er both "war". If a distractor equalled the answer the question would have two correct
 * options. So distractors are de-duplicated by text (case-insensitively) against the answer and each
 * other; a person whose form matches the answer is simply dropped. Because the subject pronoun is left
 * in the sentence, exactly one paradigm form fits the blank, so the remaining distractors are always
 * wrong. A candidate that can't supply at least one distinct distractor is skipped.
 */
object ConjugationsQuizGenerator {

    private data class Candidate(val sentence: String, val answer: String)

    /**
     * Whether a fill-in-the-blank quiz is worth offering for this tense: true only when the paradigm has
     * **≥3 distinct person-forms**, so a question can present a real choice (answer + ≥2 wrong options).
     * Tenses whose form is identical (or nearly so) across persons — most English tenses (*will be*,
     * *would be*, and the have/has-only perfects) — return false, and the screen hides the quiz button.
     * Every German tense, and English present *be* (am/are/is), pass.
     */
    fun isQuizzable(category: PronounCategory): Boolean =
        distinctPersonForms(category).size >= 3

    private fun distinctPersonForms(category: PronounCategory): List<String> =
        category.patterns
            .flatMap { it.forms }
            .mapNotNull { it.text.trim().takeIf { t -> t.isNotBlank() } }
            .map { firstWord(it).lowercase() }
            .distinct()

    fun generate(category: PronounCategory, count: Int = 10): List<Format7or10Section> {
        // Distractor pool: the person-varying token of every paradigm form in this tense (first word of
        // each form's text — the auxiliary in compound tenses, the verb itself in simple ones).
        val personForms: List<String> = category.patterns
            .flatMap { it.forms }
            .mapNotNull { it.text.trim().takeIf { t -> t.isNotBlank() } }
            .map { firstWord(it) }

        val candidates = category.patterns.flatMap { p ->
            p.sections.flatMap { section ->
                section.sentences.mapNotNull { sentence ->
                    val answer = sentence.green.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    if (!containsWord(sentence.text, answer)) return@mapNotNull null
                    Candidate(sentence = sentence.text, answer = answer)
                }
            }
        }.distinctBy { "${it.sentence}|${it.answer}" }.shuffled()

        var page = 0
        return candidates.mapNotNull { c ->
            val distractors = distractorsFor(c.answer, personForms) ?: return@mapNotNull null
            page += 1
            val words = (listOf(c.answer) + distractors)
                .mapIndexed { index, word -> Format7or10Word(word = word, ok = index == 0) }
            Format7or10Section(
                title = "",
                page = page,
                sentence = blankOut(c.sentence, c.answer),
                explain = category.note.ifBlank { "Pick the form that matches the subject." },
                summary = category.label,
                level = "",
                category = category.label,
                subArea = category.id,
                subLabel = category.label,
                words = words
            )
        }.take(count)
    }

    /**
     * Up to three distinct wrong options from the tense's paradigm (a different person's form), never
     * equal to the answer or to each other, case-matched to the answer. Null if none can be supplied.
     */
    private fun distractorsFor(answer: String, pool: List<String>): List<String>? {
        val used = linkedSetOf(answer.lowercase())
        val out = mutableListOf<String>()
        for (text in pool.shuffled()) {
            if (out.size >= 3) break
            if (text.isBlank()) continue
            val key = text.lowercase()
            if (key in used) continue
            used.add(key)
            out.add(matchCase(text, answer))
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** Give [text] the same leading-letter case as [answer] (so a distractor never reveals the answer). */
    private fun matchCase(text: String, answer: String): String {
        val upper = answer.firstOrNull()?.isUpperCase() == true
        return text.replaceFirstChar { if (upper) it.uppercaseChar() else it.lowercaseChar() }
    }

    /** The first whitespace-delimited word of [text]. */
    private fun firstWord(text: String): String = text.trim().split(Regex("\\s+")).first()

    /** True if [word] appears as a whole space-delimited token in [sentence] (ignoring edge punctuation). */
    private fun containsWord(sentence: String, word: String): Boolean =
        sentence.split(" ").any { core(it).equals(word, ignoreCase = false) }

    /** Replace the first whole-word occurrence of [word] with "_", preserving edge punctuation. */
    private fun blankOut(sentence: String, word: String): String {
        var replaced = false
        return sentence.split(" ").joinToString(" ") { token ->
            if (!replaced && core(token) == word) {
                replaced = true
                token.replaceFirst(word, "_")
            } else token
        }
    }

    /** The token's letters, with leading/trailing punctuation stripped (Char.isLetter keeps ö/ä/ü/ß). */
    private fun core(token: String): String = token.trim { !it.isLetter() }
}
