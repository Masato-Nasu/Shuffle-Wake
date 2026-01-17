package com.example.shufflealarm.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.shufflealarm.R
import com.example.shufflealarm.player.PlaybackService

class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trackName = intent?.getStringExtra(EXTRA_TRACK_NAME)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RingingScreen(
                        trackName = trackName,
                        onStop = {
                            PlaybackService.stopAlarm(this)
                            finish()
                        },
                        onSnooze = {
                            PlaybackService.snoozeAlarm(this)
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_TRACK_NAME = "extra_track_name"
    }
}

@Composable
private fun RingingScreen(trackName: String?, onStop: () -> Unit, onSnooze: () -> Unit) {
    val displayName = trackName ?: stringResource(R.string.ringing_unknown_track)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.ringing_title), style = MaterialTheme.typography.headlineMedium)
        Text(text = stringResource(R.string.ringing_subtitle), style = MaterialTheme.typography.titleMedium)
        Text(text = displayName, style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStop, modifier = Modifier.width(140.dp)) {
                Text(stringResource(R.string.action_stop))
            }
            OutlinedButton(onClick = onSnooze, modifier = Modifier.width(140.dp)) {
                Text(stringResource(R.string.action_snooze))
            }
        }
    }
}
