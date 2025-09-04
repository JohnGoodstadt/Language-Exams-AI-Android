package com.goodstadt.john.language.exams.utils // Or your preferred package

import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * A data class to hold the parsed components of a timing string.
 * This is the Kotlin equivalent of Swift's tuple: (multiplier:Int, intervalType:String)
 */
data class TimingComponents(val multiplier: Int, val intervalType: String)

// ... your other functions like timingToDurationMillis ...

// --- ADD THESE SHARED CONSTANTS HERE ---
const val TIMINGS = "10m,1h,1D,1W,1M,4M"
val STOPS: List<String> = TIMINGS.split(",")

/**
 * Parses a timing string (e.g., "2D", "7H", "14M") into its constituent parts.
 *
 * @param text The timing string to parse.
 * @return A [TimingComponents] object. Defaults to (1, "H") if parsing fails.
 */
fun timingToComponents(text: String): TimingComponents {
    // Kotlin's Regex class is more modern and safer than Java's Pattern.
    val regex = "(\\d+)([mHDWMY])".toRegex()
    val matchResult = regex.find(text)

    if (matchResult != null) {
        val (multiplierStr, intervalType) = matchResult.destructured
        val multiplier = multiplierStr.toIntOrNull() ?: 1
        return TimingComponents(multiplier, intervalType)
    }

    return TimingComponents(1, "H") // Default if no match
}

/**
 * Converts a timing string (e.g., "10m", "1D", "4M") into a duration in milliseconds.
 * This is the most useful conversion for your Kotlin data models.
 *
 * @param timing The timing string.
 * @return The duration in milliseconds as a Long.
 */
fun timingToDurationMillis(timing: String): Long {
    val components = timingToComponents(timing)
    val multiplier = components.multiplier.toLong() // Use Long for calculations to avoid overflow

    return when (components.intervalType) {
        "m" -> TimeUnit.MINUTES.toMillis(multiplier)
        "H" -> TimeUnit.HOURS.toMillis(multiplier)
        "D", "d" -> TimeUnit.DAYS.toMillis(multiplier)
        "W" -> TimeUnit.DAYS.toMillis(multiplier * 7)
        "M" -> {
            // Using Calendar is the correct way to handle variable month lengths
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, components.multiplier)
            // We return the difference from 'now' to get the duration
            cal.timeInMillis - System.currentTimeMillis()
        }
        "Y" -> {
            val cal = Calendar.getInstance()
            cal.add(Calendar.YEAR, components.multiplier)
            cal.timeInMillis - System.currentTimeMillis()
        }
        else -> 0L
    }
}

/**
 * (Optional) If you absolutely need a new Date object from a timing string.
 * This is the direct translation of your Swift 'StringToDateInterval'.
 *
 * @param timing The timing string.
 * @param now The starting Date object, defaults to the current time.
 * @return A new Date object calculated by adding the duration.
 */
fun timingToFutureDate(timing: String, now: java.util.Date = java.util.Date()): java.util.Date {
    val durationMillis = timingToDurationMillis(timing)
    return java.util.Date(now.time + durationMillis)
}

fun formatTimeInterval(interval: Double): String {
    val duration = interval.toDuration(DurationUnit.SECONDS)
    val hours = duration.inWholeHours
    val minutes = duration.inWholeMinutes % 60

    return if (hours == 0L) {
        if (minutes == 1L) {
            "1 minute"
        } else if (minutes > 54){ //round up
            "1 hour"
        } else {
            String.format("%02d minutes", minutes)
        }
    } else {
        String.format("%02d:%02d", hours, minutes)
    }
}