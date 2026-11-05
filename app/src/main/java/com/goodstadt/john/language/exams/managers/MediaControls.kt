package com.goodstadt.john.language.exams.managers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.goodstadt.john.language.exams.MainActivity
import com.goodstadt.john.language.exams.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets the car (Bluetooth steering-wheel / dashboard buttons), Android Auto, the lock screen, the notification
 * shade and headsets control [SequencePlayer]: play, pause, next and previous line. Mirrors the iOS
 * MPRemoteCommandCenter / Now Playing support.
 *
 * While a sequence is active (playing or paused) a [MediaSessionCompat] is active and [MediaPlaybackService]
 * runs in the foreground with a media notification, so the controls keep working with the screen off or the
 * app in the background. Stopping (or finishing) the sequence releases both.
 */
@Singleton
class MediaControls @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: SequencePlayer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appName: String by lazy { context.getString(R.string.app_name) }
    private var serviceRunning = false

    /** True while a sequence is playing or paused. */
    var isActive = false
        private set

    val session: MediaSessionCompat by lazy {
        MediaSessionCompat(context, "ExamAudio").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { player.resume() }
                override fun onPause() { player.pause() }
                override fun onSkipToNext() { player.skip(1) }
                override fun onSkipToPrevious() { player.skip(-1) }
                override fun onStop() { player.stop() }
            })
            setSessionActivity(openAppIntent())
        }
    }

    init {
        scope.launch {
            combine(player.playingKey, player.isPaused, player.nowPlaying) { key, paused, now ->
                Triple(key != null, paused, now)
            }.collect { (active, paused, now) -> update(active, paused, now) }
        }
    }

    /** Notification actions (prev / play-pause / next) arrive here via [MediaPlaybackService]. */
    fun handleAction(action: String?) {
        when (action) {
            ACTION_PLAY_PAUSE -> if (player.isPaused.value) player.resume() else player.pause()
            ACTION_NEXT -> player.skip(1)
            ACTION_PREVIOUS -> player.skip(-1)
            ACTION_STOP -> player.stop()
        }
    }

    private fun update(active: Boolean, paused: Boolean, now: SequencePlayer.NowPlaying?) {
        isActive = active
        if (!active) {
            if (serviceRunning) {
                serviceRunning = false
                context.stopService(Intent(context, MediaPlaybackService::class.java))
            }
            session.setPlaybackState(state(PlaybackStateCompat.STATE_STOPPED, 0))
            session.isActive = false
            return
        }
        val sentence = now?.sentence ?: now?.title ?: appName
        val subtitle = now?.title ?: appName
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, sentence)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, sentence)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, appName)
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, ((now?.index ?: 0) + 1).toLong())
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, (now?.count ?: 0).toLong())
                .build()
        )
        session.setPlaybackState(
            state(if (paused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING, now?.index ?: 0)
        )
        session.isActive = true

        if (!serviceRunning) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, MediaPlaybackService::class.java))
                serviceRunning = true
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException if started from the background. The session
                // still works while the app is open.
                Timber.w(e, "MediaControls: could not start media service")
            }
        } else {
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun state(state: Int, index: Int): PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
            )
            .setActiveQueueItemId(index.toLong())
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f)
            .build()

    /** The media notification shown while a sequence is active (also what the foreground service needs). */
    fun buildNotification(): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Audio playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Play / pause controls while sentences or podcasts are playing"
                    setShowBadge(false)
                }
            )
        }
        val now = player.nowPlaying.value
        val paused = player.isPaused.value
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (paused) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            .setContentTitle(now?.sentence ?: now?.title ?: appName)
            .setContentText(now?.title ?: appName)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(serviceIntent(ACTION_STOP, 4))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(!paused)
            .addAction(android.R.drawable.ic_media_previous, "Previous", serviceIntent(ACTION_PREVIOUS, 1))
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "Play" else "Pause",
                serviceIntent(ACTION_PLAY_PAUSE, 2)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", serviceIntent(ACTION_NEXT, 3))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context, requestCode,
        Intent(context, MediaPlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val CHANNEL_ID = "audio_playback"
        const val NOTIFICATION_ID = 4711
        const val ACTION_PLAY_PAUSE = "exams.media.PLAY_PAUSE"
        const val ACTION_NEXT = "exams.media.NEXT"
        const val ACTION_PREVIOUS = "exams.media.PREVIOUS"
        const val ACTION_STOP = "exams.media.STOP"
    }
}

/**
 * Foreground "media playback" service that keeps the app alive (and the car / lock-screen controls working)
 * while a sequence is active. Started and stopped by [MediaControls]; it holds no player of its own.
 */
@AndroidEntryPoint
class MediaPlaybackService : Service() {
    @Inject lateinit var controls: MediaControls
    @Inject lateinit var player: SequencePlayer

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = controls.buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(MediaControls.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(MediaControls.NOTIFICATION_ID, notification)
        }
        controls.handleAction(intent?.action)
        if (!controls.isActive) stopSelf()
        return START_NOT_STICKY
    }

    /** Swiping the app away from Recents stops playback and the notification. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        player.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
