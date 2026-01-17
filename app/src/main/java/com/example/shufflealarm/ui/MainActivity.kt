package com.example.shufflealarm.ui

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shufflealarm.R
import com.example.shufflealarm.alarm.AlarmScheduler
import com.example.shufflealarm.player.PlaybackService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as Activity
    val settings by vm.settings.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            vm.persistTreePermission(uri)
            vm.setFolderUri(uri)
            vm.rescan()
        }
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore */ }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)

        SectionCard(title = stringResource(R.string.section_alarm)) {
            val timeText = stringResource(R.string.time_format_hm, settings.alarmHour, settings.alarmMinute)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "${stringResource(R.string.label_alarm_time)}: $timeText", modifier = Modifier.weight(1f))
                Button(onClick = {
                    val dlg = TimePickerDialog(
                        context,
                        { _, h, m ->
                            vm.setAlarmTime(h, m)
                            if (!AlarmScheduler.canScheduleExactAlarms(context)) {
                                Toast.makeText(context, context.getString(R.string.toast_alarm_disabled), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_alarm_set), Toast.LENGTH_SHORT).show()
                            }
                        },
                        settings.alarmHour,
                        settings.alarmMinute,
                        DateFormat.is24HourFormat(context)
                    )
                    dlg.show()
                }) {
                    Text(stringResource(R.string.action_set_time))
                }
            }
        }

        SectionCard(title = stringResource(R.string.section_music)) {
            val folderLabel = settings.treeUri ?: stringResource(R.string.label_none)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "${stringResource(R.string.label_selected_folder)}: $folderLabel")
                Text(text = stringResource(R.string.label_tracks_count, settings.tracks.size))

                if (settings.lastScanMillis > 0) {
                    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                    Text(text = stringResource(R.string.label_last_scan, fmt.format(Date(settings.lastScanMillis))))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { folderPicker.launch(null) }) {
                        Text(stringResource(R.string.action_choose_folder))
                    }
                    OutlinedButton(onClick = { vm.rescan() }, enabled = settings.treeUri != null) {
                        Text(stringResource(R.string.action_rescan))
                    }
                }

                Button(
                    onClick = {
                        if (settings.tracks.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.toast_no_tracks), Toast.LENGTH_SHORT).show()
                        } else {
                            PlaybackService.testPlay(context)
                        }
                    },
                    enabled = settings.tracks.isNotEmpty()
                ) {
                    Text(stringResource(R.string.action_test_play))
                }
            }
        }

        SectionCard(title = stringResource(R.string.section_settings)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Fade seconds
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.label_fade_in), modifier = Modifier.weight(1f))
                    Text(text = stringResource(R.string.unit_seconds, settings.fadeSeconds))
                }
                Slider(
                    value = settings.fadeSeconds.toFloat(),
                    onValueChange = { vm.setFadeSeconds(it.toInt()) },
                    valueRange = 0f..5f,
                    steps = 4
                )

                // Snooze minutes
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.label_snooze_minutes), modifier = Modifier.weight(1f))
                    Text(text = stringResource(R.string.unit_minutes, settings.snoozeMinutes))
                }
                Slider(
                    value = settings.snoozeMinutes.toFloat(),
                    onValueChange = { vm.setSnoozeMinutes(it.toInt()) },
                    valueRange = 1f..30f,
                    steps = 28
                )

                // Exclude recent
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.label_exclude_recent), modifier = Modifier.weight(1f))
                    Switch(checked = settings.excludeRecent, onCheckedChange = { vm.setExcludeRecent(it) })
                }
            }
        }

        SectionCard(title = stringResource(R.string.section_permissions)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionRow(
                    label = stringResource(R.string.perm_notifications),
                    granted = hasNotificationsPermission(context),
                    actionLabel = stringResource(R.string.action_request)
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                PermissionRow(
                    label = stringResource(R.string.perm_exact_alarm),
                    granted = AlarmScheduler.canScheduleExactAlarms(context),
                    actionLabel = stringResource(R.string.action_open_settings)
                ) {
                    openExactAlarmSettings(context)
                }

                PermissionRow(
                    label = stringResource(R.string.perm_folder),
                    granted = settings.treeUri != null,
                    actionLabel = stringResource(R.string.action_choose_folder)
                ) {
                    folderPicker.launch(null)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label)
            Text(
                text = if (granted) stringResource(R.string.perm_granted) else stringResource(R.string.perm_not_granted),
                style = MaterialTheme.typography.bodySmall
            )
        }
        OutlinedButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

private fun hasNotificationsPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun openExactAlarmSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // On some devices this opens the per-app exact alarm toggle.
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Throwable) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    } else {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${context.packageName}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
