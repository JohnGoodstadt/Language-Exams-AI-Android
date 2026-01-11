package com.goodstadt.john.language.exams.utils

import com.goodstadt.john.language.exams.models.HeaderWordsSentencesList
import com.goodstadt.john.language.exams.models.TestMyselfSections
import com.goodstadt.john.language.exams.models.TestMyselfWordsState

object QuizDataConverter {

    /**
     * Converts "Sounds the Same" Reference Data into "Correct Sentence" Quiz format.
     * Logic: Creates a distractor sentence by swapping the homophone pairs.
     */
    fun generateHomophoneSwapQuiz(
        sourceData: List<HeaderWordsSentencesList>,
        limit: Int = 10
    ): List<TestMyselfSections> {

        val generatedQuestions = mutableListOf<TestMyselfSections>()
        var pageCounter = 0

        // 1. Flatten all entries and shuffle
        val allEntries = sourceData.flatMap { it.wordsAndSentences }.shuffled()

        for (entry in allEntries) {
            if (generatedQuestions.size >= limit) break

            // 2. Parse the pair (e.g., "hoard, horde")
            // Split by comma, trim whitespace, remove empty strings
            val words = entry.word.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            // We need exactly 2 words to perform a clean swap logic
            if (words.size != 2) continue

            val wordA = words[0]
            val wordB = words[1]

            // Check if the sentence actually contains at least one of them
            val sentence = entry.sentence
            // contains(..., ignoreCase = true) is the Kotlin equivalent
            val hasWordA = sentence.contains(wordA, ignoreCase = true)
            val hasWordB = sentence.contains(wordB, ignoreCase = true)

            if (!hasWordA && !hasWordB) continue

            // 3. Create the "Wrong" Sentence by swapping
            // We use a unique placeholder to avoid collision during the swap
            val placeholder = "##SWAP_MARKER##"

            var wrongSentence = sentence

            // Step A: Replace Word A with Placeholder (Case Insensitive, Word Boundary)
            wrongSentence = replaceWord(wrongSentence, wordA, placeholder)

            // Step B: Replace Word B with Word A
            wrongSentence = replaceWord(wrongSentence, wordB, wordA)

            // Step C: Replace Placeholder with Word B
            // (Simple replace is fine here as placeholder is unique)
            wrongSentence = wrongSentence.replace(placeholder, wordB)

            // Edge case: If the swap resulted in an identical string, skip it
            if (wrongSentence == sentence) continue

            // 4. Build Options
            val quizOptions = mutableListOf<TestMyselfWordsState>()

            // Correct Answer (Original Sentence)
            quizOptions.add(
                TestMyselfWordsState(
                    word = sentence, // The sentence goes in the 'word' field as requested
                    ok = true
                )
            )

            // Incorrect Answer (Swapped Sentence)
            quizOptions.add(
                TestMyselfWordsState(
                    word = wrongSentence,
                    ok = false
                )
            )

            // 5. Build Section
            val section = TestMyselfSections(
                title = entry.word,       // "hoard, horde"
                page = pageCounter,
                sentence = "",            // Empty as requested
                explain = entry.definition, // The definition
                summary = "Select the correct sentence",
                words = quizOptions.shuffled() // Shuffle so correct answer isn't always top
            )

            generatedQuestions.add(section)
            pageCounter++
        }

        return generatedQuestions
    }

    // MARK: - Helper: Word Boundary Replacement
    /**
     * Replaces whole words only, case insensitive.
     * Equivalent to Swift's NSRegularExpression logic with \b boundaries.
     */
    private fun replaceWord(text: String, target: String, replacement: String): String {
        try {
            // (?i) enables case insensitivity
            // \b matches word boundaries
            // Regex.escape ensures characters like '+' or '?' in the word don't break Regex
            val pattern = "(?i)\\b${Regex.escape(target)}\\b"
            return text.replace(Regex(pattern), replacement)
        } catch (e: Exception) {
            return text // Fail safe
        }
    }
}