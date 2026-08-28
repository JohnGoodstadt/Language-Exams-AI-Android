package com.goodstadt.john.language.exams.models

import androidx.annotation.Keep

/**
 * One vocab entry the user has swiped to "Save" for later practice. Persisted locally by
 * SavedPracticeManager, keyed by [level] then [id], so each CEFR level (A1/A2/B1/B2) has its own list.
 *
 * The full [word] (definition, pronunciation, all its sentences) is stored so the practice screen can show
 * the rich layout without re-loading or looking up the vocab sheet. [categoryTitle] is the section header
 * the entry came from, for grouping/prioritising on the read screen.
 */
@Keep
data class SavedSentence(
    val id: String = "",             // stable key within a level (the word text)
    val level: String = "",          // "A1" / "A2" / "B1" / "B2"
    val categoryTitle: String = "",  // section header the word lives under
    val savedAt: Long = 0,           // epoch SECONDS when saved (for ordering)
    val word: Format0Word? = null,   // full vocab entry for display
    // Epoch MILLIS the practice reminder is due; 0 = no active reminder. Once this time has passed the
    // entry is "triggered" (shows the REMIND ME label and sorts to the top). Cleared to 0 when the user
    // plays a sentence on the row (acknowledged) or removes the entry.
    val reminderAt: Long = 0
)
