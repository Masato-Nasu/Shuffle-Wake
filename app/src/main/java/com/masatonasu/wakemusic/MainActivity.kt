package com.masatonasu.wakemusic

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var folderText: TextView
    private lateinit var timeText: TextView
    private lateinit var volText: TextView
    private lateinit var volSeek: SeekBar
    private lateinit var listText: TextView

    private lateinit var audioManager: AudioManager
    private var maxAlarmVol: Int = 1

    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val clockTick = object : Runnable {
        override fun run() {
            timeText.text = "現在時刻：${clockFormat.format(Date())}"
            handler.postDelayed(this, 1000L)
        }
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Throwable) { }
        AlarmStore.setMusicTreeUri(this, uri)
        refreshUi()
        Toast.makeText(this, "音楽フォルダを設定しました", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
            textSize = 26f
            gravity = Gravity.CENTER
        }

        timeText = TextView(this).apply { textSize = 18f }

        folderText = TextView(this).apply { textSize = 14f }

        val btnPick = Button(this).apply {
            text = "音楽フォルダを選ぶ"
            setOnClickListener { pickFolder.launch(null) }
        }

        val volTitle = TextView(this).apply {
            text = "音量（事前設定 / 再生中も変更できます）"
            textSize = 14f
        }

        volText = TextView(this).apply { textSize = 14f }

        volSeek = SeekBar(this).apply {
            max = 100
            progress = AlarmStore.getAlarmVolumePercent(this@MainActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    AlarmStore.setAlarmVolumePercent(this@MainActivity, value)
                    applyAlarmStreamPercent(value, showUi = true)
                    refreshVolLabel(value)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val btnPlayNow = Button(this).apply {
            text = "テスト再生（今すぐ鳴らす）"
            setOnClickListener {
                val i = Intent(this@MainActivity, AlarmPlayerService::class.java).apply {
                    action = AlarmPlayerService.ACTION_START_ALARM
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
            }
        }

        val btnStopNow = Button(this).apply {
            text = "停止（いま鳴っている音を止める）"
            setOnClickListener {
                val i = Intent(this@MainActivity, AlarmPlayerService::class.java).apply {
                    action = AlarmPlayerService.ACTION_STOP_ALARM
                }
                startService(i)
            }
        }

        listText = TextView(this).apply { textSize = 14f }
        val scroll = ScrollView(this).apply {
            addView(listText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        root.addView(title, lpMatchWrap())
        root.addView(timeText, lpMatchWrap(top = 12))
        root.addView(folderText, lpMatchWrap(top = 16))
        root.addView(btnPick, lpMatchWrap(top = 12))

        root.addView(volTitle, lpMatchWrap(top = 20))
        root.addView(volText, lpMatchWrap(top = 8))
        root.addView(volSeek, lpMatchWrap())

        root.addView(btnPlayNow, lpMatchWrap(top = 18))
        root.addView(btnStopNow, lpMatchWrap(top = 10))

        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        ).apply { topMargin = 24; weight = 1f })

        setContentView(root)

        refreshVolLabel(volSeek.progress)
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockTick)
        val p = AlarmStore.getAlarmVolumePercent(this)
        volSeek.progress = p
        refreshVolLabel(p)
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTick)
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
        volText.text = "設定：$p%（端末のアラーム音量：${current ?: "?"}/${maxAlarmVol}）"
    }

    private fun refreshUi() {
        val tree = AlarmStore.getMusicTreeUri(this)
        folderText.text = "音楽フォルダ：${tree?.toString() ?: "未設定"}"

        val alarms = runCatching { AlarmStore.getAlarms(this) }.getOrNull().orEmpty()
        val sb = StringBuilder()
        sb.append("登録アラーム：").append(alarms.size).append("\n\n")
        alarms.forEachIndexed { idx, a ->
            sb.append(idx + 1).append(". ")
                .append(String.format("%02d:%02d", a.hour, a.minute))
                .append(if (a.enabled) "" else " [OFF]")
                .append("\n")
        }
        if (alarms.isEmpty()) sb.append("まだありません（現状はテスト再生で動作確認してください）。\n")
        listText.text = sb.toString()
    }

    private fun lpMatchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }
}
