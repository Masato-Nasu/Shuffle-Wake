package com.example.wakemusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * AlarmPlayerService (reliable + preset volume)
 *
 * Fixes "plays sometimes / not others" via:
 * - startForeground() immediately (avoid FGS kill)
 * - playlist scan in background thread
 * - preset alarm volume applied BEFORE playback (AlarmStore percent)
 * - robust fallback to system ringtone URIs if folder is empty / URI fails
 * - onPlayerError: skip to next or fallback
 *
 * Manifest recommended:
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 * <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
 *
 * Service entry recommended:
 * <service android:name=".AlarmPlayerService" android:exported="false" android:foregroundServiceType="mediaPlayback"/>
 */
class AlarmPlayerService : Service() {

    private var player: ExoPlayer? = null
    @Volatile private var starting = false

    private var prevAlarmVolume: Int? = null
    private var errorStreak: Int = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action.orEmpty()
        when {
            action == ACTION_START_ALARM || action.endsWith(SUFFIX_START) -> startAlarm()
            action == ACTION_STOP_ALARM || action.endsWith(SUFFIX_STOP) -> stopAlarm()
        }
        return START_STICKY
    }

    private fun startAlarm() {
        if (starting) return
        starting = true
        errorStreak = 0

        // 1) Foreground ASAP
        val fullScreen = Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fullScreenPi = PendingIntent.getActivity(
            this, 2001, fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmPlayerService::class.java).setAction(ACTION_STOP_ALARM)
        val stopPi = PendingIntent.getService(
            this, 2002, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = getSystemService(NotificationManager::class.java)
        val canFullScreen = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
        val notification = buildNotification(fullScreenPi, stopPi, canFullScreen)

        // compile-safe foregroundServiceType: MEDIA_PLAYBACK = 2 (Android 10+), else 0
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 2 else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgType)

        // 2) Apply preset alarm volume BEFORE playback
        applyPresetAlarmVolumeOnce()

        // 3) Build playlist in background
        thread(name = "WakeMusic-Playlist") {
            val playlist = runCatching { buildShuffledPlaylistFromSelectedFolder() }
                .getOrElse { e ->
                    Log.e(TAG, "Playlist build failed", e)
                    emptyList()
                }
                .ifEmpty { fallbackUris() }

            Log.i(TAG, "Playlist size=${'$'}{playlist.size}")

            // 4) Start ExoPlayer on main thread
            Handler(Looper.getMainLooper()).post {
                val p = ensurePlayer()
                p.setMediaItems(playlist.map { MediaItem.fromUri(it) }, true)
                p.repeatMode = Player.REPEAT_MODE_ALL
                p.shuffleModeEnabled = true
                p.prepare()
                p.play()
                starting = false
            }
        }
    }

    private fun stopAlarm() {
        starting = false
        player?.run {
            stop()
            clearMediaItems()
        }
        restoreAlarmVolume()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }

        val p = ExoPlayer.Builder(this).build()

        // Route audio as ALARM usage
        val attrs = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_ALARM)
            .build()
        p.setAudioAttributes(attrs, true)

        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) errorStreak = 0
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorStreak += 1
                Log.e(TAG, "Player error(${ '$' }errorStreak): ${'$'}{error.errorCodeName}", error)

                // Try next track first; if not possible, fallback
                val pl = player ?: return
                if (errorStreak <= 10 && pl.hasNextMediaItem()) {
                    runCatching { pl.seekToNextMediaItem() }
                    runCatching { pl.play() }
                    return
                }

                // Fallback to system ringtone URIs
                val fb = fallbackUris()
                pl.setMediaItems(fb.map { MediaItem.fromUri(it) }, true)
                pl.prepare()
                pl.play()
            }
        })

        player = p
        return p
    }

    private fun applyPresetAlarmVolumeOnce() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = runCatching { am.getStreamMaxVolume(AudioManager.STREAM_ALARM) }.getOrNull() ?: return
        val current = runCatching { am.getStreamVolume(AudioManager.STREAM_ALARM) }.getOrNull() ?: return

        if (prevAlarmVolume == null) prevAlarmVolume = current

        val percent = AlarmStore.getAlarmVolumePercent(this).coerceIn(0, 100)
        val target = ((max.toFloat() * percent.toFloat()) / 100f).roundToInt().coerceIn(0, max)

        Log.i(TAG, "Preset volume: percent=${'$'}percent max=${'$'}max current=${'$'}current target=${'$'}target")

        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
            .onFailure { Log.e(TAG, "setStreamVolume failed", it) }
    }

    private fun restoreAlarmVolume() {
        val prev = prevAlarmVolume ?: return
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, prev, 0) }
        prevAlarmVolume = null
    }

    private fun buildShuffledPlaylistFromSelectedFolder(): List<Uri> {
        val treeUri = AlarmStore.getMusicTreeUri(this) ?: return emptyList()
        val root = DocumentFile.fromTreeUri(this, treeUri) ?: return emptyList()

        val uris = mutableListOf<Uri>()

        fun walk(doc: DocumentFile) {
            if (doc.isDirectory) {
                doc.listFiles().forEach { walk(it) }
            } else {
                val type = doc.type.orEmpty()
                val name = doc.name.orEmpty()
                val isAudio =
                    type.startsWith("audio/") ||
                        name.endsWith(".mp3", true) ||
                        name.endsWith(".m4a", true) ||
                        name.endsWith(".wav", true) ||
                        name.endsWith(".ogg", true) ||
                        name.endsWith(".flac", true) ||
                        name.endsWith(".aac", true) ||
                        name.endsWith(".opus", true)
                if (isAudio) uris.add(doc.uri)
            }
        }

        walk(root)
        uris.shuffle()
        return uris
    }

    private fun fallbackUris(): List<Uri> {
        // Some devices can return null for DEFAULT_ALARM_ALERT_URI; use RingtoneManager fallbacks.
        val candidates = listOf(
            SettingsSafe.defaultAlarmUri(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        ).filterNotNull()
        return if (candidates.isNotEmpty()) candidates else listOf(Uri.parse("content://settings/system/alarm_alert"))
    }

    private object SettingsSafe {
        fun defaultAlarmUri(): Uri? {
            return runCatching { android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI }.getOrNull()
        }
    }

    private fun buildNotification(
        fullScreenPi: PendingIntent,
        stopPi: PendingIntent,
        canFullScreen: Boolean,
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WakeMusic")
            .setContentText("アラーム再生中")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "停止", stopPi)

        if (canFullScreen) builder.setFullScreenIntent(fullScreenPi, true)
        else builder.setContentIntent(fullScreenPi)

        return builder.build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CHANNEL_ID,
            "WakeMusic Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "WakeMusic のアラーム再生用通知"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    override fun onDestroy() {
        restoreAlarmVolume()
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WakeMusic"
        private const val SUFFIX_START = ".action.START_ALARM"
        private const val SUFFIX_STOP = ".action.STOP_ALARM"

        const val ACTION_START_ALARM = "com.example.wakemusic.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.wakemusic.action.STOP_ALARM"

        private const val CHANNEL_ID = "wakemusic_alarm"
        private const val NOTIFICATION_ID = 42
    }
}
