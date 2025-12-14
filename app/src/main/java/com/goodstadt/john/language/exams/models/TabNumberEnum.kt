package com.goodstadt.john.language.exams.models

enum class TabNumberEnum(val value: Int) {
    One(1),
    Two(2),
    Three(3);

    companion object {
        /**
         * Safely converts an Int (e.g. 1) to the Enum (e.g. One).
         * Returns null if no match found.
         */
        fun fromInt(value: Int): TabNumberEnum? {
            return entries.find { it.value == value }
        }
    }
}