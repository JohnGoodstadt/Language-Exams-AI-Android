package com.goodstadt.john.language.exams.managers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.models.SaveReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules a local "time to practise" notification for a saved word, at the time implied by the chosen
 * [SaveReminder]. Uses AlarmManager (inexact / allow-while-idle - fine for a reminder) and posts via
 * [PracticeReminderReceiver]. Tapping the notification opens the app on the Me tab's "Saved" screen.
 *
 * DEBUG builds use short delays (seconds) so the flow can be tested without waiting an hour.
 */
@Singleton
class PracticeReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "practice_reminders"
        const val ACTION_FIRE = "com.goodstadt.john.language.exams.PRACTICE_REMINDER"
        const val EXTRA_WORD = "extra_word"
    }

    init {
        ensureChannel()
    }

    /**
     * Schedule a reminder for [word]. [SaveReminder.NONE] (or a blank word) does nothing.
     * Returns the epoch-millis the reminder is due (so it can be persisted on the saved entry for the
     * "REMIND ME" label), or null when nothing was scheduled.
     */
    fun schedule(reminder: SaveReminder, word: String): Long? {
        if (reminder == SaveReminder.NONE || word.isBlank()) return null
        val triggerAt = triggerTimeMillis(reminder) ?: return null

        val intent = Intent(context, PracticeReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_WORD, word)
        }
        val requestCode = word.hashCode()
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Timber.i("PracticeReminder: scheduled '$word' for $triggerAt ($reminder)")
            triggerAt
        } catch (e: Exception) {
            Timber.e(e, "PracticeReminder: failed to schedule reminder for '$word'")
            null
        }
    }

    /** Cancel any pending reminder for [word] (and dismiss it if already showing). */
    fun cancel(word: String) {
        if (word.isBlank()) return
        val intent = Intent(context, PracticeReminderReceiver::class.java).apply { action = ACTION_FIRE }
        val pi = PendingIntent.getBroadcast(
            context, word.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
            NotificationManagerCompat.from(context).cancel(word.hashCode())
            Timber.i("PracticeReminder: cancelled reminder for '$word'")
        } catch (e: Exception) {
            Timber.e(e, "PracticeReminder: failed to cancel reminder for '$word'")
        }
    }

    private fun triggerTimeMillis(reminder: SaveReminder): Long? {
        val now = System.currentTimeMillis()

        if (BuildConfig.DEBUG) {
            // Short delays for testing the notification flow.
            return when (reminder) {
                SaveReminder.ONE_HOUR -> now + 30_000L   // 30s
                SaveReminder.LATER -> now + 60_000L      // 60s
                SaveReminder.TONIGHT -> now + 90_000L    // 90s
                SaveReminder.TOMORROW -> now + 120_000L  // 120s
                SaveReminder.NONE -> null
            }
        }

        val cal = Calendar.getInstance()
        return when (reminder) {
            SaveReminder.ONE_HOUR -> now + 60L * 60L * 1000L
            SaveReminder.LATER -> now + 3L * 60L * 60L * 1000L // ~3 hours later
            SaveReminder.TONIGHT -> {
                cal.set(Calendar.HOUR_OF_DAY, 20); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                if (cal.timeInMillis <= now) now + 2L * 60L * 60L * 1000L else cal.timeInMillis
            }
            SaveReminder.TOMORROW -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 9); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            SaveReminder.NONE -> null
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Practice reminders",
                        // HIGH so the reminder peeks as a heads-up banner (not just a status-bar icon).
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "Reminders to practise words you saved." }
                )
            }
        }
    }
}
