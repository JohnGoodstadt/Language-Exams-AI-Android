package com.goodstadt.john.language.exams.utils

import android.content.Context
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Format1Level
import com.goodstadt.john.language.exams.models.Format7or10File
import com.goodstadt.john.language.exams.models.Format7or10Section
import com.goodstadt.john.language.exams.models.Format7or10Word
import kotlin.random.Random

object QuizDataConverter {

    /**
     * Converts "Sounds the Same" Reference Data into "Correct Sentence" Quiz format.
     * Logic: Creates a distractor sentence by swapping the homophone pairs.
     */
    fun generateHomophoneSwapQuiz(
        sourceData: List<Format1Level>,
        limit: Int = 10
    ): List<Format7or10Section> {

        val generatedQuestions = mutableListOf<Format7or10Section>()
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
            val quizOptions = mutableListOf<Format7or10Word>()

            // Correct Answer (Original Sentence)
            quizOptions.add(
                Format7or10Word(
                    word = sentence, // The sentence goes in the 'word' field as requested
                    ok = true
                )
            )

            // Incorrect Answer (Swapped Sentence)
            quizOptions.add(
                Format7or10Word(
                    word = wrongSentence,
                    ok = false
                )
            )

            // 5. Build Section
            val section = Format7or10Section(
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
     * Transforms a list of Categories into an array of Format7or10Section for a multiple-choice quiz.
     *
     * This quiz presents a word and its definition, and asks the user to choose
     * between a "correct" sentence (`lockedClause`) and an "incorrect" sentence (`weakenedClause`).
     *
     * @param sourceData The list of Categories containing the adjective data.
     * @param limit The maximum number of questions to generate.
     * @return A list of `Format7or10Section` ready to be used by the Quiz UI.
     */
    fun generateAdjectivesQuiz(sourceData: List<Category>, limit: Int = 10): List<Format7or10Section> {

        // 1. Flatten all `Format0Word` objects from all categories into a single list.
        val allWords = sourceData.flatMap { it.words }

        // 2. Filter for valid quiz entries and shuffle them for randomness.
        //    A valid entry must have a non-empty lockedClause and weakenedClause.
        val generatedQuestions = allWords
            .filter {
                it.lockedClause.isNotBlank() && it.weakenedClause.isNotBlank()
            }
            .shuffled()
            // 3. Take up to the specified limit of questions.
            .take(limit)
            // 4. Loop through the selected entries and transform them into the quiz format.
            .mapIndexed { index, entry ->

                // a) Build the answer options.
                val quizOptions = listOf(
                    // The Correct Answer (the lockedClause)
                    Format7or10Word(
                        word = entry.lockedClause,
                        ok = true
                    ),
                    // The Incorrect Answer (the weakenedClause)
                    Format7or10Word(
                        word = entry.weakenedClause,
                        ok = false
                    )
                ).shuffled() // Shuffle the "correct" and "incorrect" options

                // b) Build the final `Format7or10Section` object for this question.
                Format7or10Section(
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
        root: Format7or10File,
        count: Int = 10,
        random: Random = Random.Default
    ): List<Format7or10Section> {
        val allSections: List<Format7or10Section> =
            root.data.flatMap { it.sections }

        if (allSections.isEmpty()) return emptyList()

        val n = minOf(count, allSections.size)
        return allSections.shuffled(random).take(n)
    }


    fun readWordPairsJSONForQuiz(
        context: Context,
        filename: String,
        count: Int = 10
    ): List<Format7or10Section> {

       // val fileName = "QuizSheetWordPairs-en.json" // stored under assets/Quizzes/
        val root: Format7or10File = readFormat7or10fDataFromAssets(context, filename)
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


    fun readPrepositionsQuizQuestions(
        context: Context,
        fileName: String,
        count: Int = 10
    ): List<Format7or10Section> {

        val jsonFileName = "$fileName.json" // assets/Quizzes/QuizSheetPrepositions-en.json
        val root: Format7or10File = readFormat7or10fDataFromAssets(context, jsonFileName) ?: return emptyList()

        return pickRandomQuizSections(
            root = root,
            count = count,
            renumberPagesFrom1 = true,
            shuffleAnswers = true
        )
    }


    fun pickRandomQuizSections(
        root: Format7or10File,
        count: Int = 10,
        renumberPagesFrom1: Boolean = true,
        shuffleAnswers: Boolean = true,
        random: Random = Random.Default
    ): List<Format7or10Section> {

        // 1) Flatten all sections from all Format7or10List blocks
        val allSections: List<Format7or10Section> = root.data.flatMap { it.sections }
        if (allSections.isEmpty()) return emptyList()

        // 2) Shuffle + take N (no duplicates within a single call)
        val n = minOf(count, allSections.size)
        val picked: List<Format7or10Section> = allSections.shuffled(random).take(n)

        // 3) Optionally renumber pages and shuffle answer options
        return picked.mapIndexed { index, section ->
            val newPage = if (renumberPagesFrom1) index + 1 else section.page
            val newWords = if (shuffleAnswers) section.words.shuffled(random) else section.words

            // Prefer copy() if your models are data classes; otherwise construct a new instance.
            section.copy(page = newPage, words = newWords)
        }
    }

}