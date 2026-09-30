package io.saul.geoalarm.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.saul.geoalarm.R

/** Lock-screen alarm UI. Lives only as long as something is ringing. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContent {
            MaterialTheme {
                val alarm by AlarmRingService.current.collectAsStateWithLifecycle()
                LaunchedEffect(alarm) { if (alarm == null) finish() }
                val a = alarm ?: return@MaterialTheme
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF123F31)).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(96.dp))
                    Text(
                        getString(if (a.entered) R.string.alarm_arrived_heading else R.string.alarm_left_heading),
                        color = Color(0xFFB7E4D2), style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        a.label, color = Color.White, style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                    )
                    if (a.note.isNotBlank()) {
                        Text(
                            a.note, color = Color.White, style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedButton(
                            onClick = { AlarmRingService.actionIntent(this@AlarmActivity, AlarmRingService.ACTION_SNOOZE).send() },
                            modifier = Modifier.weight(1f).height(64.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) { Text(getString(R.string.alarm_snooze)) }
                        Button(
                            onClick = { AlarmRingService.actionIntent(this@AlarmActivity, AlarmRingService.ACTION_DISMISS).send() },
                            modifier = Modifier.weight(1f).height(64.dp),
                        ) { Text(getString(R.string.alarm_dismiss)) }
                    }
                }
            }
        }
    }
}
