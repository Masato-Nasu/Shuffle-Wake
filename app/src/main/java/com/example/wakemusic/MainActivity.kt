package com.example.wakemusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity (adds "preset volume" slider)
 *
 * - Preset volume is stored into AlarmStore (0..100%).
 * - AlarmPlayerService applies it BEFORE playback.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var folderText: TextView
    private lateinit var volText: TextView
    private lateinit var volSeek: SeekBar
    private lateinit var listText: TextView

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Throwable) {
        }
        AlarmStore.setMusicTreeUri(this, uri)
        refreshUi()
        Toast.makeText(this, "音楽フォルダを設定しました", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "WakeMusic"
            textSize = 26f
            gravity = Gravity.CENTER
        }

        folderText = TextView(this).apply {
            textSize = 14f
        }

        val btnPick = Button(this).apply {
            text = "音楽フォルダを選ぶ"
            setOnClickListener { pickFolder.launch(null) }
        }

        val volTitle = TextView(this).apply {
            text = "起床音量（事前設定）"
            textSize = 14f
        }

        volText = TextView(this).apply { textSize = 14f }

        volSeek = SeekBar(this).apply {
            max = 100
            progress = AlarmStore.getAlarmVolumePercent(this@MainActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        AlarmStore.setAlarmVolumePercent(this@MainActivity, value)
                        refreshVolLabel(value)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val btnTest = Button(this).apply {
            text = "テスト再生（今すぐ鳴らす）"
            setOnClickListener {
                // immediate test: start service directly
                val i = Intent(this@MainActivity, AlarmPlayerService::class.java).apply {
                    action = AlarmPlayerService.ACTION_START_ALARM
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(i)
                } else {
                    startService(i)
                }
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
        root.addView(folderText, lpMatchWrap(top = 16))
        root.addView(btnPick, lpMatchWrap(top = 16))

        root.addView(volTitle, lpMatchWrap(top = 16))
        root.addView(volText, lpMatchWrap(top = 8))
        root.addView(volSeek, lpMatchWrap())

        root.addView(btnTest, lpMatchWrap(top = 16))
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        ).apply { topMargin = 24; weight = 1f })

        setContentView(root)

        refreshVolLabel(volSeek.progress)
        refreshUi()
    }

    private fun refreshVolLabel(percent: Int) {
        val p = percent.coerceIn(0, 100)
        volText.text = "設定：$p%（再生開始前に適用されます）"
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
        if (alarms.isEmpty()) sb.append("まだありません（現在はテスト再生で動作確認してください）。\n")
        listText.text = sb.toString()
    }

    private fun lpMatchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }
}
