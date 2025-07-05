package com.goodstadt.john.language.exams.screens.utils

import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord

// A data class to hold the result, similar to the Swift tuple `(sentence, parts)`
data class SentenceDisplayParts(val sentence: String, val parts: List<String>)

/**
 * Replicates the logic of the iOS `buildSentence` function.
 * It finds the vocab word(s) within a sentence and splits the sentence
 * into parts, with the word(s) being the "holes".
 */
fun buildSentenceParts(entry: VocabWord, sentence: Sentence): SentenceDisplayParts {
    val sentenceText = sentence.sentence
    val wordToFind = entry.word

    // Case 1: The word contains a comma, indicating two words to find (e.g., "word1,word2")
    if (wordToFind.contains(",")) {
        val words = wordToFind.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (words.size == 2) {
            // Use placeholders to safely split the string
            val placeholder1 = "@@PLACEHOLDER1@@"
            val placeholder2 = "@@PLACEHOLDER2@@"
            val tempSentence = sentenceText
                .replaceFirst(words[0], placeholder1, ignoreCase = true)
                .replaceFirst(words[1], placeholder2, ignoreCase = true)

            val parts = tempSentence.split(placeholder1, placeholder2)
            // If the split results in 3 parts, it's a success
            if (parts.size == 3) {
                return SentenceDisplayParts(sentence = sentenceText, parts = parts)
            }
        }
    }

    // Case 2: Only one word to find
    if (sentenceText.contains(wordToFind, ignoreCase = true)) {
        // Splitting by the word with a limit of 2 is a robust way to get the parts
        val parts = sentenceText.split(wordToFind, ignoreCase = true, limit = 2)
        if (parts.size == 2) {
            return SentenceDisplayParts(sentence = sentenceText, parts = parts)
        }
    }

    // Fallback case: If logic fails or word not found, return the full sentence as a single part.
    return SentenceDisplayParts(sentence = sentenceText, parts = listOf(sentenceText))
}