package jp.masatonasu.shufflewake

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import jp.masatonasu.shufflewake.alarm.AlarmScheduler
import jp.masatonasu.shufflewake.data.SettingsRepository
import jp.masatonasu.shufflewake.data.TimeEntry
import jp.masatonasu.shufflewake.player.PlaybackService
import jp.masatonasu.shufflewake.player.TrackPicker
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var repo: SettingsRepository
    private lateinit var scheduler: AlarmScheduler

    private lateinit var status: TextView
    private lateinit var debug: TextView
    private lateinit var timesContainer: LinearLayout

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            repo.setFolderUri(uri.toString())
            toast("フォルダを保存しました")
            refreshUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = SettingsRepository(this)
        scheduler = AlarmScheduler(this, repo)

        status = findViewById(R.id.status)
        debug = findViewById(R.id.debug)
        timesContainer = findViewById(R.id.timesContainer)

        findViewById<Button>(R.id.btnChooseFolder).setOnClickListener {
            pickFolderLauncher.launch(null)
        }

        findViewById<Button>(R.id.btnRescan).setOnClickListener {
            rescan()
        }

        findViewById<Button>(R.id.btnAddTime).setOnClickListener {
            addTimeDialog()
        }

        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            ensureExactAlarmPermissionIfNeeded()
            scheduler.scheduleAllEnabledDaily()
            toast("スケジュールしました")
        }

        findViewById<Button>(R.id.btnTestPlay).setOnClickListener {
            testPlay()
        }

        ensureRuntimePermissionsIfNeeded()
        refreshUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun ensureRuntimePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val notif = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, notif) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(notif), 1001)
            }
        }

        // READ_MEDIA_AUDIO is runtime on 33+
        if (Build.VERSION.SDK_INT >= 33) {
            val perm = Manifest.permission.READ_MEDIA_AUDIO
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), 1002)
            }
        } else {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), 1003)
            }
        }
    }

    private fun ensureExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                // User must allow exact alarms in settings
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
    }

    private fun refreshUi() {
        val folder = repo.getFolderUri()
        val times = repo.getTimes()

        status.text = buildString {
            append("フォルダ: ")
            append(folder ?: "未選択")
            append("\n曲数: ")
            append(repo.getCachedTrackCount())
            append("\n時刻: ")
            append(times.size)
        }

        timesContainer.removeAllViews()
        times.forEach { entry ->
            val row = layoutInflater.inflate(R.layout.item_time, timesContainer, false)
            val tv = row.findViewById<TextView>(R.id.timeText)
            val cb = row.findViewById<CheckBox>(R.id.enabled)
            val del = row.findViewById<ImageButton>(R.id.delete)

            tv.text = entry.format()
            cb.isChecked = entry.enabled

            cb.setOnCheckedChangeListener { _, checked ->
                repo.updateTime(entry.copy(enabled = checked))
            }

            del.setOnClickListener {
                repo.deleteTime(entry.id)
                refreshUi()
            }

            timesContainer.addView(row)
        }

        debug.text = "" // reserved
    }

    private fun rescan() {
        val folderUriStr = repo.getFolderUri()
        if (folderUriStr.isNullOrBlank()) {
            toast("先にフォルダを選んでください")
            return
        }

        val folderUri = Uri.parse(folderUriStr)
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                val root = DocumentFile.fromTreeUri(this@MainActivity, folderUri) ?: return@withContext 0
                val uris = mutableListOf<String>()
                scanAudioRecursive(root, uris)
                repo.setTrackUris(uris)
                uris.size
            }
            toast("スキャン完了: ${count}曲")
            refreshUi()
        }
    }

    private fun scanAudioRecursive(dir: DocumentFile, out: MutableList<String>) {
        if (!dir.isDirectory) return
        dir.listFiles().forEach { f ->
            if (f.isDirectory) {
                scanAudioRecursive(f, out)
            } else {
                val name = f.name?.lowercase() ?: ""
                // 主要拡張子だけ許可
                if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".wav") || name.endsWith(".flac") || name.endsWith(".ogg")) {
                    out.add(f.uri.toString())
                }
            }
        }
    }

    private fun addTimeDialog() {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)

        val picker = android.app.TimePickerDialog(
            this,
            { _, h, m ->
                val entry = TimeEntry.create(h, m)
                repo.addTime(entry)
                refreshUi()
            },
            hour,
            minute,
            true
        )
        picker.show()
    }

    private fun testPlay() {
        val picker = TrackPicker(this, repo)
        val track = picker.pickRandom()
        if (track == null) {
            toast("曲がありません。フォルダ選択→スキャンをしてください")
            return
        }
        val i = Intent(this, PlaybackService::class.java)
        i.action = PlaybackService.ACTION_PLAY
        i.putExtra(PlaybackService.EXTRA_TRACK_URI, track)
        ContextCompat.startForegroundService(this, i)
        toast("再生開始")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
