package com.goodstadt.john.language.exams.packages.Format8

import com.goodstadt.john.language.exams.models.Format7or10Section
import com.goodstadt.john.language.exams.models.Format7or10Word
import com.goodstadt.john.language.exams.models.Format8File
import java.util.Locale

/** Mirrors the Swift generator: skip entries without variants and blanks with more than one answer. */
object Format8QuizGenerator {
    private data class Candidate(val blank: String, val answer: String, val pool: List<String>, val summary: String)

    fun generate(file: Format8File, count: Int = 10): List<Format7or10Section> {
        if (count <= 0) return emptyList()
        val candidates = file.sections.flatMap { it.entries }.flatMap { entry ->
            val pool = entry.examples.map { it.key.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) }
            if (pool.size < 3) return@flatMap emptyList()
            val examples = entry.examples.mapNotNull { example ->
                val key = example.key.trim()
                if (key.isEmpty()) return@mapNotNull null
                val match = Format8Presentation.tokenRegex(key).find(example.german) ?: return@mapNotNull null
                example.german.replaceRange(match.range, "_") to key
            }
            val answers = examples.groupBy({ it.first }, { it.second.lowercase(Locale.ROOT) })
            examples.filter { answers[it.first]!!.distinct().size == 1 }.map { (blank, key) ->
                Candidate(blank, key, pool, entry.header)
            }
        }.distinctBy { it.blank.lowercase(Locale.ROOT) }.shuffled()
        return candidates.mapNotNull { candidate ->
            val wrong = candidate.pool.filterNot { it.equals(candidate.answer, ignoreCase = true) }
                .shuffled().take(3).map { text ->
                    if (candidate.answer.first().isUpperCase()) text.replaceFirstChar { it.uppercaseChar() }
                    else text.replaceFirstChar { it.lowercaseChar() }
                }
            if (wrong.isEmpty()) return@mapNotNull null
            candidate to (listOf(Format7or10Word(candidate.answer, true)) + wrong.map { Format7or10Word(it, false) }).shuffled()
        }.take(count).mapIndexed { index, (candidate, words) ->
            Format7or10Section(title = "", page = index + 1, sentence = candidate.blank,
                explain = "Pick the word that fits the sentence.", summary = candidate.summary, words = words)
        }
    }
}
