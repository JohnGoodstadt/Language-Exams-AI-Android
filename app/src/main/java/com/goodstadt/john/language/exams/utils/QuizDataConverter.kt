package com.goodstadt.john.language.exams.utils

import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesList
import com.goodstadt.john.language.exams.models.TestMyselfListRoot
import com.goodstadt.john.language.exams.models.TestMyselfSections
import com.goodstadt.john.language.exams.models.TestMyselfWordsState
import kotlin.random.Random
import android.content.Context

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
    /**
     * Transforms a list of Categories into an array of TestMyselfSections for a multiple-choice quiz.
     *
     * This quiz presents a word and its definition, and asks the user to choose
     * between a "correct" sentence (`lockedClause`) and an "incorrect" sentence (`weakenedClause`).
     *
     * @param sourceData The list of Categories containing the adjective data.
     * @param limit The maximum number of questions to generate.
     * @return A list of `TestMyselfSections` ready to be used by the Quiz UI.
     */
    fun generateAdjectivesQuiz(sourceData: List<Category>, limit: Int = 10): List<TestMyselfSections> {

        // 1. Flatten all `Format0Word` objects from all categories into a single list.
        val allWords = sourceData.flatMap { it.words }

        // 2. Filter for valid quiz entries and shuffle them for randomness.
        //    A valid entry must have a non-empty lockedClause and weakenedClause.
        val generatedQuestions = allWords
            .filter { it.lockedClause.isNotBlank() && it.weakenedClause.isNotBlank() }
            .shuffled()
            // 3. Take up to the specified limit of questions.
            .take(limit)
            // 4. Loop through the selected entries and transform them into the quiz format.
            .mapIndexed { index, entry ->

                // a) Build the answer options.
                val quizOptions = listOf(
                    // The Correct Answer (the lockedClause)
                    TestMyselfWordsState(
                        word = entry.lockedClause,
                        ok = true
                    ),
                    // The Incorrect Answer (the weakenedClause)
                    TestMyselfWordsState(
                        word = entry.weakenedClause,
                        ok = false
                    )
                ).shuffled() // Shuffle the "correct" and "incorrect" options

                // b) Build the final `TestMyselfSections` object for this question.
                TestMyselfSections(
                    title = "Choose the sentence most closely described by ${entry.word}.",
                    page = index,       // A zero-based page index
                    sentence = entry.word,
                    explain = entry.definition, // The DEFINITION is now the lookup bottom sheet
                    summary = "Choose the correct sentence that uses '${entry.word}'.",
                    words = quizOptions
                )
            }

        return generatedQuestions
    }


    fun randomSectionsFromAllLists(
        root: TestMyselfListRoot,
        count: Int = 10,
        random: Random = Random.Default
    ): List<TestMyselfSections> {
        val allSections: List<TestMyselfSections> =
            root.data.flatMap { it.sections }

        if (allSections.isEmpty()) return emptyList()

        val n = minOf(count, allSections.size)
        return allSections.shuffled(random).take(n)
    }


    fun readWordPairsJSONForQuiz(
        context: Context,
        filename: String,
        count: Int = 10
    ): List<TestMyselfSections> {

       // val fileName = "QuizSheetWordPairs-en.json" // stored under assets/Quizzes/
        val root: TestMyselfListRoot = readTestMyselfDataFromAssets(context, filename)
            ?: return emptyList()

        return randomSectionsFromAllLists(root, count)
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