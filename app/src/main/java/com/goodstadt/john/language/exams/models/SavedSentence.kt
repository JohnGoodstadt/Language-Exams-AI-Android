package com.goodstadt.john.language.exams.models

import androidx.annotation.Keep

/**
 * One sentence the user has swiped to "Save" for later practice. Persisted locally by SavedPracticeManager,
 * keyed by [level] then [id], so each CEFR level (A1/A2/B1/B2) has its own saved list.
 *
 * Carries enough context for a future "Me"-tab practice screen to display, group or prioritise the entries
 * (e.g. by [categoryTitle] section header, or [savedAt] recency) without re-loading the vocab sheet.
 */
@Keep
data class SavedSentence(
    val id: String = "",             // content ID of the sentence (voice-agnostic hash)
    val level: String = "",          // "A1" / "A2" / "B1" / "B2"
    val word: String = "",           // the headword for this sentence
    val sentence: String = "",       // the full sentence text
    val categoryTitle: String = "",  // section header the sentence lives under
    val savedAt: Long = 0            // epoch SECONDS when saved (for ordering)
)
