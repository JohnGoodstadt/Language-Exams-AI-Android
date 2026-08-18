package com.goodstadt.john.language.exams.models

/**
 * The mutable state the spaced-repetition engine ([com.goodstadt.john.language.exams.data.repository.VocabMasteryEngine])
 * reads and writes. Both the Vocab Quiz ([VocabLearningState], keyed by word) and the Usage Quiz
 * ([UsageQuestionStat], keyed by page number) implement this so they share one SRS "brain".
 */
interface SrsState {
    var masteryLevel: WordMasteryLevel
    var correctStreak: Int   // consecutive first-try successes, across separate review sessions
    var nextReviewTime: Long // epoch millis: when this item becomes due again
}
