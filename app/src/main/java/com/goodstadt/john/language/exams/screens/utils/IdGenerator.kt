package com.goodstadt.john.language.exams.utils

import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord

/**
 * Creates a unique, human-readable, and file-system-safe ID for a given word and sentence combination.
 * This function should be the single source of truth for generating these IDs throughout the app.
 *
 * @param word The vocabulary word object.
 * @param sentence The sentence object associated with the word.
 * @return A filesystem-safe string, e.g., "hello_world_This_is_the_sentence".
 */
fun generateUniqueSentenceId(word: VocabWord, sentence: Sentence, googleVoice:String): String {
    // Combine the word and sentence
    val rawId = "${googleVoice}_${word.word}.${sentence.sentence}"

    // Sanitize the string to make it safe for use as a filename.
    // This replaces any character that is NOT a letter, number, or period with an underscore.
    return rawId.replace(Regex("[^a-zA-Z0-9.]"), "_")
}
fun generateUniqueSentenceId(sentence: String, googleVoice:String): String {

    val rawId = "${googleVoice}_$sentence"

    // Sanitize the string to make it safe for use as a filename.
    // This replaces any character that is NOT a letter, number, or period with an underscore.
    return rawId.replace(Regex("[^a-zA-Z0-9.]"), "_")
}