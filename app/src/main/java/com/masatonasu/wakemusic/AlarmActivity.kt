package com.masatonasu.wakemusic

import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class AlarmActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private var maxAlarmVol: Int = 1

    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private lateinit var tvTime: TextView
    private lateinit var tvVol: TextView
    private lateinit var seek: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show above lockscreen + turn on screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Status bar: dark icons on light background
        runCatching {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.statusBarColor = Color.WHITE
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        }.onFailure {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)

        @Suppress("DEPRECATION")
        volumeControlStream = AudioManager.STREAM_ALARM

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "WakeMusic"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        tvTime = TextView(this).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            text = "現在時刻：${clockFormat.format(Date())}"
        }

        val subtitle = TextView(this).apply {
            text = "アラーム再生中"
            textSize = 16f
            gravity = Gravity.CENTER
        }

        tvVol = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
        }

        seek = SeekBar(this).apply {
            max = 100
            progress = AlarmStore.getAlarmVolumePercent(this@AlarmActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    AlarmStore.setAlarmVolumePercent(this@AlarmActivity, value)
                    applyAlarmStreamPercent(value, showUi = true)
                    refreshVolLabel(value)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val stop = Button(this).apply {
            text = "停止"
            textSize = 20f
            setOnClickListener { stopAlarmAndFinish() }
        }

        root.addView(title, lpMatchWrap())
        root.addView(tvTime, lpMatchWrap(top = 12))
        root.addView(subtitle, lpMatchWrap(top = 8))
        root.addView(tvVol, lpMatchWrap(top = 22))
        root.addView(seek, lpMatchWrap(top = 6))
        root.addView(stop, lpMatchWrap(top = 28))

        setContentView(root)

        refreshVolLabel(seek.progress)
    }

    override fun onResume() {
        super.onResume()
        tvTime.text = "現在時刻：${clockFormat.format(Date())}"
        val p = AlarmStore.getAlarmVolumePercent(this)
        seek.progress = p
        refreshVolLabel(p)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun applyAlarmStreamPercent(percent: Int, showUi: Boolean) {
        val p = percent.coerceIn(0, 100)
        val target = ((maxAlarmVol.toFloat() * p.toFloat()) / 100f).roundToInt().coerceIn(0, maxAlarmVol)
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, flags) }
    }

    private fun refreshVolLabel(percent: Int) {
        val p = percent.coerceIn(0, 100)
        val current = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_ALARM) }.getOrNull()
        tvVol.text = "音量：$p%（端末のアラーム音量：${current ?: "?"}/${maxAlarmVol}）"
    }

    private fun stopAlarmAndFinish() {
        val i = Intent(this, AlarmPlayerService::class.java).apply {
            action = AlarmPlayerService.ACTION_STOP_ALARM
        }
        startService(i)
        finish()
    }

    private fun lpMatchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }
}
