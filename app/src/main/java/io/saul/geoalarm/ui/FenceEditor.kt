package io.saul.geoalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.saul.geoalarm.data.DeliveryMode

@Composable
fun FenceEditor(
    draft: FenceDraft,
    onChange: ((FenceDraft) -> FenceDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (draft.id != 0L) TextButton(onClick = onDelete) { Text("Delete") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = onSave, enabled = draft.canSave) { Text("Save") }
            }
        }
    }
}

/** Two actions split across a row. */
@Composable
fun Row2(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        left()
        right()
    }
}
