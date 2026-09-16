package com.goodstadt.john.language.exams.packages.ReferencePronouns

import com.goodstadt.john.language.exams.models.Format7or10Section
import com.goodstadt.john.language.exams.models.Format7or10Word

/**
 * Builds a fill-in-the-blank quiz for one pronoun CATEGORY at RUNTIME from the base reference content
 * (a [PronounCategory] of the fileFormat-6 sheet), so repeat attempts don't show the identical fixed
 * questions the authored quiz file has.
 *
 * For each example sentence it blanks the focused pronoun (the last word in the sentence's `green` list)
 * and offers it against the OTHER cases of the **same pattern** (e.g. dieser → diesen / diesem), always
 * case-matched to the answer. Distractors deliberately come only from the answer's own pattern — a
 * different case of the same word is always grammatically wrong in the slot, so there is never a second
 * correct answer. (Pulling forms from other patterns would be unsafe: for possessives "Ihr Kind schläft"
 * and "Sein Kind schläft" are both correct, because the noun fixes the ending but not the owner.)
 *
 * Questions are spread across the category's sub-categories (patterns) by round-robin, and both the pick
 * and the order are shuffled, so the set differs each time. Each question is tagged with the pattern it
 * tests (`subArea`/`subLabel`) for per-pattern strength tracking.
 *
 * A question is emitted only when the owning pattern has two distinct alternative forms. Patterns/
 * categories that can't supply them (possessives and reflexives are too syncretic; the da-/wo- compounds
 * have no form chain) yield too few questions, and the caller falls back to the authored quiz.
 */
object PronounQuizGenerator {

    private data class Candidate(
        val patternId: String,
        val patternLabel: String,
        val note: String,
        val sentence: String,
        val answer: String,
        val ownForms: List<String>
    )

    fun generate(category: PronounCategory, count: Int = 10): List<Format7or10Section> {
        // Candidates grouped by the SECTION's pattern (so distribution is by sub-category chip).
        val perPattern: Map<String, List<Candidate>> = category.patterns.associate { p ->
            val candidates = p.sections.flatMap { section ->
                section.sentences.mapNotNull { sentence ->
                    val target = sentence.green.lastOrNull()?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null           // challenge sentences have no green -> skip
                    if (!containsWord(sentence.text, target)) return@mapNotNull null
                    // Only quiz a form that belongs to a declension chain: its OTHER forms are then safe
                    // wrong answers (a different case of the same word). A form that's in no chain (a
                    // possessive acc/dat ending, niemand, a da-word, "was" as a question) is skipped -
                    // there we can't guarantee a single correct answer, so the authored quiz is used.
                    val owner = category.patterns
                        .firstOrNull { pp -> pp.forms.any { it.text.equals(target, ignoreCase = true) } }
                        ?: return@mapNotNull null
                    Candidate(
                        patternId = owner.id,
                        patternLabel = owner.chip.ifBlank { owner.id },
                        note = owner.note,
                        sentence = sentence.text,
                        answer = target,
                        ownForms = owner.forms.map { it.text }.filter { it.isNotBlank() }
                    )
                }
            }.shuffled()
            p.id to candidates
        }

        // Round-robin across patterns for an even spread; drop duplicate (sentence, answer) pairs.
        val picked = distribute(perPattern, count).distinctBy { "${it.sentence}|${it.answer}" }

        var page = 0
        return picked.mapNotNull { c ->
            val distractors = distractorsFor(c.answer, c.ownForms) ?: return@mapNotNull null
            page += 1
            val words = (listOf(c.answer) + distractors)
                .mapIndexed { index, word -> Format7or10Word(word = word, ok = index == 0) }
            Format7or10Section(
                title = "",
                page = page,
                sentence = blankOut(c.sentence, c.answer),
                explain = c.note.ifBlank { "Choose the form that matches the noun's gender, number and case." },
                summary = c.patternLabel,
                level = "B1",
                category = category.label,
                subArea = c.patternId,
                subLabel = c.patternLabel,
                words = words
            )
        }.shuffled()
    }

    /** Take candidates one pattern at a time, cycling, until [count] is reached or all are exhausted. */
    private fun distribute(perPattern: Map<String, List<Candidate>>, count: Int): List<Candidate> {
        val queues = perPattern.values.map { it.toMutableList() }.filter { it.isNotEmpty() }
        val out = mutableListOf<Candidate>()
        var progressed = true
        while (out.size < count && progressed) {
            progressed = false
            for (queue in queues) {
                if (queue.isNotEmpty()) {
                    out.add(queue.removeAt(0))
                    progressed = true
                    if (out.size >= count) break
                }
            }
        }
        return out
    }

    /**
     * Two distinct wrong options taken ONLY from the answer's own pattern (a different case of the same
     * word — always wrong in the slot), case-matched. Null if the pattern can't supply two, so the caller
     * drops the question. Never uses other patterns' forms: those can be a valid second answer.
     */
    private fun distractorsFor(answer: String, ownForms: List<String>): List<String>? {
        val used = linkedSetOf(answer.lowercase())
        val out = mutableListOf<String>()
        for (text in ownForms.shuffled()) {
            if (out.size >= 2) break
            if (text.isBlank()) continue
            val key = text.lowercase()
            if (key in used) continue
            used.add(key)
            out.add(matchCase(text, answer))
        }
        return out.takeIf { it.size == 2 }
    }

    /** Give [text] the same leading-letter case as [answer] (so distractors don't reveal the answer). */
    private fun matchCase(text: String, answer: String): String {
        val upper = answer.firstOrNull()?.isUpperCase() == true
        return text.replaceFirstChar { if (upper) it.uppercaseChar() else it.lowercaseChar() }
    }

    /** True if [word] appears as a whole space-delimited token in [sentence] (ignoring edge punctuation). */
    private fun containsWord(sentence: String, word: String): Boolean =
        sentence.split(" ").any { core(it) == word }

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
