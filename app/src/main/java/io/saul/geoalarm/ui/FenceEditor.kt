package io.saul.geoalarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import io.saul.geoalarm.data.DeliveryMode
import io.saul.geoalarm.data.Schedule
import java.time.DayOfWeek

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FenceEditor(
    draft: FenceDraft,
    onChange: ((FenceDraft) -> FenceDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var pickingTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            onChange { it.copy(soundUri = uri?.toString()) }
        }
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (draft.id == 0L) "New fence" else "Edit fence",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Tap the map to move it. %.5f, %.5f".format(draft.center.latitude, draft.center.longitude),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = draft.label,
                onValueChange = { v -> onChange { it.copy(label = v) } },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Radius", Modifier.width(64.dp))
                Slider(
                    value = RadiusScale.toPosition(draft.radiusMeters),
                    onValueChange = { pos -> onChange { it.copy(radiusMeters = RadiusScale.snap(RadiusScale.toMeters(pos))) } },
                    modifier = Modifier.weight(1f),
                )
                Text(RadiusScale.label(draft.radiusMeters), Modifier.width(64.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Trigger on")
                FilterChip(
                    selected = draft.onEnter,
                    onClick = { onChange { it.copy(onEnter = !it.onEnter) } },
                    label = { Text("Arrive") },
                )
                FilterChip(
                    selected = draft.onExit,
                    onClick = { onChange { it.copy(onExit = !it.onExit) } },
                    label = { Text("Leave") },
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DeliveryMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = draft.mode == mode,
                        onClick = { onChange { it.copy(mode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, DeliveryMode.entries.size),
                    ) { Text(if (mode == DeliveryMode.ALARM) "Alarm" else "Reminder") }
                }
            }
            OutlinedTextField(
                value = draft.note,
                onValueChange = { v -> onChange { it.copy(note = v) } },
                label = { Text("Note (shown when it fires)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Repeat")
                    Text(
                        if (draft.repeat) "Fires every time" else "Fires once, then turns off",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = draft.repeat, onCheckedChange = { v -> onChange { it.copy(repeat = v) } })
            }
            Text("Active days")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val bit = Schedule.dayBit(day)
                    FilterChip(
                        selected = draft.activeDays and bit != 0,
                        onClick = { onChange { it.copy(activeDays = it.activeDays xor bit) } },
                        label = { Text(day.name.take(2).lowercase().replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
            val hasWindow = draft.windowStartMinutes != null && draft.windowEndMinutes != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Only between")
                    Text(
                        if (hasWindow) "${Schedule.formatMinutes(draft.windowStartMinutes!!)} - ${Schedule.formatMinutes(draft.windowEndMinutes!!)}"
                        else "All day",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = hasWindow, onCheckedChange = { on ->
                    onChange { if (on) it.copy(windowStartMinutes = 7 * 60, windowEndMinutes = 19 * 60) else it.copy(windowStartMinutes = null, windowEndMinutes = null) }
                })
            }
            if (hasWindow) {
                Row {
                    TextButton(onClick = { pickingTime = true }) { Text("From ${Schedule.formatMinutes(draft.windowStartMinutes!!)}") }
                    TextButton(onClick = { pickingTime = false }) { Text("To ${Schedule.formatMinutes(draft.windowEndMinutes!!)}") }
                }
            }
            if (draft.mode == DeliveryMode.ALARM) {
                val soundName = remember(draft.soundUri) {
                    draft.soundUri?.let { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) } ?: "Default alarm"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sound: $soundName", Modifier.weight(1f))
                    TextButton(onClick = {
                        soundPicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, draft.soundUri?.let(Uri::parse)),
                        )
                    }) { Text("Change") }
                }
            }
            if (draft.activeDays == 0) {
                Text("Pick at least one day.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (draft.id != 0L) TextButton(onClick = onDelete) { Text("Delete") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = onSave, enabled = draft.canSave) { Text("Save") }
            }
        }
    }

    pickingTime?.let { start ->
        val initial = (if (start) draft.windowStartMinutes else draft.windowEndMinutes) ?: 0
        TimeDialog(
            initialMinutes = initial,
            onDismiss = { pickingTime = null },
            onConfirm = { m ->
                pickingTime = null
                onChange { if (start) it.copy(windowStartMinutes = m) else it.copy(windowEndMinutes = m) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimeInput(state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Two actions split across a row. */
@Composable
fun Row2(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        left()
        right()
    }
}
