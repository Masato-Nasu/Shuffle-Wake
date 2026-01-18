package jp.masatonasu.shufflewake.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import jp.masatonasu.shufflewake.MainActivity
import jp.masatonasu.shufflewake.R
import jp.masatonasu.shufflewake.alarm.AlarmScheduler
import jp.masatonasu.shufflewake.data.SettingsRepository

class PlaybackService : Service() {

    private var player: MediaPlayer? = null
    private lateinit var repo: SettingsRepository
    private lateinit var nm: NotificationManager
    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(this)
        nm = getSystemService(NotificationManager::class.java)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val uriStr = intent.getStringExtra(EXTRA_TRACK_URI)
                if (uriStr != null) playUri(uriStr)
            }
            ACTION_PLAY_RANDOM -> {
                val picked = TrackPicker(this, repo).pickRandom()
                if (picked != null) playUri(picked)
            }
            ACTION_STOP -> stopPlayback()
            ACTION_SNOOZE_10 -> {
                stopPlayback()
                // Simple snooze: 10 minutes later, one-shot alarm
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.MINUTE, 10)
                val am = getSystemService(android.app.AlarmManager::class.java)
                val pi = PendingIntent.getBroadcast(
                    this,
                    999999,
                    Intent(this, jp.masatonasu.shufflewake.alarm.AlarmReceiver::class.java).apply {
                        action = jp.masatonasu.shufflewake.alarm.AlarmReceiver.ACTION_ALARM
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
                )
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } else {
                    am.setExact(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun playUri(uriStr: String) {
        stopPlayback()

        val audioManager = getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }

        val uri = Uri.parse(uriStr)
        val mp = MediaPlayer()
        mp.setAudioAttributes(attrs)
        mp.setDataSource(this, uri)
        mp.setOnPreparedListener {
            startForeground(NOTIF_ID, buildNotification("再生中"))
            it.start()
        }
        mp.setOnCompletionListener {
            stopPlayback()
            stopSelf()
        }
        mp.setOnErrorListener { _, _, _ ->
            stopPlayback()
            stopSelf()
            true
        }
        mp.prepareAsync()
        player = mp
    }

    private fun stopPlayback() {
        player?.runCatching {
            stop()
            release()
        }
        player = null

        val audioManager = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        focusRequest = null

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "ShuffleWake", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Shuffle Wake")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "jp.masatonasu.shufflewake.action.PLAY"
        const val ACTION_PLAY_RANDOM = "jp.masatonasu.shufflewake.action.PLAY_RANDOM"
        const val ACTION_STOP = "jp.masatonasu.shufflewake.action.STOP"
        const val ACTION_SNOOZE_10 = "jp.masatonasu.shufflewake.action.SNOOZE_10"

        const val EXTRA_TRACK_URI = "track_uri"

        private const val CHANNEL_ID = "shufflewake_alarm"
        private const val NOTIF_ID = 100
    }
}
