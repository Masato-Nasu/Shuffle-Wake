package com.example.shufflealarm.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.shufflealarm.R
import com.example.shufflealarm.alarm.AlarmActionReceiver
import com.example.shufflealarm.alarm.AlarmScheduler
import com.example.shufflealarm.data.SettingsRepository
import com.example.shufflealarm.data.TrackPicker
import com.example.shufflealarm.ui.AlarmRingingActivity
import com.example.shufflealarm.ui.MainActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

class PlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var fadeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        player = ExoPlayer.Builder(this).build().apply {
            val attr = AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_ALARM)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            setAudioAttributes(attr, true)
        }
    }

    override fun onDestroy() {
        fadeJob?.cancel()
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> {
                // start foreground immediately with placeholder
                startForeground(NOTIF_ID, buildNotification(title = getString(R.string.notif_alarm_ringing), text = "", isRinging = true))
                serviceScope.launch { startPlayback(isTest = false) }
            }
            ACTION_TEST_PLAY -> {
                startForeground(NOTIF_ID, buildNotification(title = "Test", text = "", isRinging = false))
                serviceScope.launch { startPlayback(isTest = true) }
            }
            ACTION_STOP_INTERNAL -> {
                serviceScope.launch { stopAndScheduleNext() }
            }
            ACTION_SNOOZE_INTERNAL -> {
                serviceScope.launch { snoozeAndStop() }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun startPlayback(isTest: Boolean) {
        val repo = SettingsRepository(this)
        val settings = repo.settingsFlow.first()
        if (settings.tracks.isEmpty()) {
            // No tracks: stop gracefully
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val picker = TrackPicker(recentLimit = 7)
        val res = picker.pickNext(
            tracks = settings.tracks,
            shuffleBag = settings.shuffleBag,
            recent = settings.recent,
            excludeRecent = settings.excludeRecent
        )

        val pickedUri = res.pickedUri ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(); return
        }

        repo.setShuffleBag(res.newBag)
        repo.setRecent(res.newRecent)

        val trackName = settings.tracks.firstOrNull { it.uri == pickedUri }?.name ?: getString(R.string.ringing_unknown_track)

        // Update notification with current track and actions
        val notif = buildNotification(
            title = getString(R.string.notif_alarm_ringing),
            text = trackName,
            isRinging = !isTest
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, notif)

        // Play
        val p = player ?: return
        fadeJob?.cancel()
        p.stop()
        p.clearMediaItems()
        p.setMediaItem(MediaItem.fromUri(Uri.parse(pickedUri)))
        p.prepare()
        p.volume = 0f
        p.playWhenReady = true

        // Fade-in
        val fadeSeconds = max(0, settings.fadeSeconds)
        fadeJob = serviceScope.launch {
            if (fadeSeconds <= 0) {
                p.volume = 1f
                return@launch
            }
            val steps = max(1, fadeSeconds * 20)
            for (i in 1..steps) {
                val v = i.toFloat() / steps.toFloat()
                p.volume = v
                delay(50)
            }
            p.volume = 1f
        }

        if (isTest) {
            // Auto-stop test after 15 seconds
            serviceScope.launch {
                delay(15_000)
                stopForeground(STOP_FOREGROUND_REMOVE)
                p.stop()
                stopSelf()
            }
        }
    }

    private suspend fun stopAndScheduleNext() {
        val repo = SettingsRepository(this)
        val s = repo.settingsFlow.first()
        player?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Reschedule daily for safety
        AlarmScheduler.scheduleDaily(this, s.alarmHour, s.alarmMinute)
        stopSelf()
    }

    private suspend fun snoozeAndStop() {
        val repo = SettingsRepository(this)
        val s = repo.settingsFlow.first()
        player?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        AlarmScheduler.scheduleSnooze(this, s.snoozeMinutes)
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_alarm),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_alarm_desc)
                setSound(null, null) // sound comes from playback
                enableVibration(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(title: String, text: String, isRinging: Boolean): android.app.Notification {
        val stopPi = PendingIntent.getBroadcast(
            this,
            2001,
            Intent(this, AlarmActionReceiver::class.java).setAction(AlarmActionReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val snoozePi = PendingIntent.getBroadcast(
            this,
            2002,
            Intent(this, AlarmActionReceiver::class.java).setAction(AlarmActionReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val contentPi = PendingIntent.getActivity(
            this,
            3001,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val fullScreenPi = PendingIntent.getActivity(
            this,
            3002,
            Intent(this, AlarmRingingActivity::class.java).putExtra(AlarmRingingActivity.EXTRA_TRACK_NAME, text),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(contentPi)
            .addAction(0, getString(R.string.action_stop), stopPi)

        if (isRinging) {
            builder.addAction(0, getString(R.string.action_snooze), snoozePi)
            builder.setFullScreenIntent(fullScreenPi, true)
        }

        return builder.build()
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    companion object {
        private const val CHANNEL_ID = "alarm"
        private const val NOTIF_ID = 42

        private const val ACTION_START_ALARM = "com.example.shufflealarm.player.START_ALARM"
        private const val ACTION_TEST_PLAY = "com.example.shufflealarm.player.TEST_PLAY"
        private const val ACTION_STOP_INTERNAL = "com.example.shufflealarm.player.STOP"
        private const val ACTION_SNOOZE_INTERNAL = "com.example.shufflealarm.player.SNOOZE"

        fun startAlarm(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_START_ALARM)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopAlarm(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_STOP_INTERNAL)
            context.startService(intent)
        }

        fun snoozeAlarm(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_SNOOZE_INTERNAL)
            context.startService(intent)
        }

        fun testPlay(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_TEST_PLAY)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
