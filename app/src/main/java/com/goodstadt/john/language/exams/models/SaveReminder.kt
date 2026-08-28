package com.goodstadt.john.language.exams.models

/**
 * Practice-reminder choices offered on the "Saved" info sheet (Apple-Mail style). UI + plumbing only for
 * now: the selection is not yet persisted or scheduled. Later this can be stored against the saved word so
 * the "Saved" screen can read it, and used to schedule a local notification.
 */
enum class SaveReminder(val label: String) {
    ONE_HOUR("Remind me in 1 hour"),
    TONIGHT("Remind me tonight"),
    TOMORROW("Remind me tomorrow"),
    LATER("Remind me later"),
    NONE("Don't remind me")
}
