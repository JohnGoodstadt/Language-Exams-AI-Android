package com.goodstadt.john.language.exams.utils

import java.util.Date
import java.util.concurrent.TimeUnit

fun Date.isWithin24Hours(): Boolean {
    // Get the current time in milliseconds
    val currentTimeMillis = System.currentTimeMillis()

    // Get the timestamp of this Date object
    val dateTimestampMillis = this.time

    // Calculate the difference between now and the installed date
    val difference = currentTimeMillis - dateTimestampMillis

    // Define 24 hours in milliseconds
    val twentyFourHoursInMillis = TimeUnit.HOURS.toMillis(24)

    // Check if the difference is greater than 0 (i.e., date is in the past)
    // AND less than 24 hours
    return difference in 1..twentyFourHoursInMillis
}