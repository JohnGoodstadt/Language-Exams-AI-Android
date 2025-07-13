package com.goodstadt.john.language.exams.data

/**
 * Constants for collection names shared across multiple repositories.
 */
object Collections {
    const val USERS = "users"
    const val CATEGORIES = "categories"
}

/**
 * Constants for fields within the 'users' collection.
 * Used by AuthRepository and potentially a future UserProfileRepository.
 */
object UserFields {
    const val LAST_ACTIVITY_DATE = "lastActivityDate"
    const val ACTIVITY_DAYS = "activityDays"
    // ...
}