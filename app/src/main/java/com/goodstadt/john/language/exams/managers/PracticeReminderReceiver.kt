package com.goodstadt.john.language.exams.managers

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.goodstadt.john.language.exams.MainActivity
import timber.log.Timber

/**
 * Posts the "time to practise <word>" notification when its scheduled alarm fires. Tapping it opens the app
 * on the Me tab's "Saved" screen (via [MainActivity]'s nav extra).
 */
class PracticeReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val word = intent.getStringExtra(PracticeReminderScheduler.EXTRA_WORD) ?: return

        // Tap -> open the app to Me > Saved.
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAV_TARGET, MainActivity.NAV_SAVED)
        }
        val contentPi = PendingIntent.getActivity(
            context, word.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PracticeReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("It's time to practice $word")
            .setContentText("You saved this to practice later")
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // heads-up on pre-O; matches the HIGH channel
            .build()

        // Android 13+ requires POST_NOTIFICATIONS; below that it's granted at install and this returns true.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Timber.w("PracticeReminder: POST_NOTIFICATIONS not granted; not showing notification")
            return
        }
        NotificationManagerCompat.from(context).notify(word.hashCode(), notification)
    }
}
