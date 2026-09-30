package io.saul.geoalarm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.saul.geoalarm.BuildConfig
import io.saul.geoalarm.data.DeliveryMode
import io.saul.geoalarm.data.Fence
import io.saul.geoalarm.engine.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FenceListSheet(
    fences: List<Fence>,
    onDismiss: () -> Unit,
    onSelect: (Fence) -> Unit,
    onToggle: (Fence, Boolean) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (fences.isEmpty()) {
            Text("No fences yet. Tap the map to add one.", Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(fences, key = { it.id }) { f ->
                ListItem(
                    headlineContent = { Text(f.label) },
                    supportingContent = {
                        val trigger = listOfNotNull("arrive".takeIf { f.onEnter }, "leave".takeIf { f.onExit }).joinToString(" + ")
                        val mode = if (f.mode == DeliveryMode.ALARM) "Alarm" else "Reminder"
                        Text("${RadiusScale.label(f.radiusMeters)} - $mode on $trigger")
                    },
                    trailingContent = { Switch(checked = f.enabled, onCheckedChange = { onToggle(f, it) }) },
                    modifier = Modifier.clickable { onSelect(f) },
                )
            }
        }
    }
}

@Composable
fun CoordinatesDialog(onDismiss: () -> Unit, onConfirm: (GeoPoint) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = Coordinates.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter coordinates") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Latitude, longitude") },
                placeholder = { Text("47.6062, -122.3321") },
                isError = text.isNotBlank() && parsed == null,
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text("Place fence") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SettingsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map style") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("MapLibre style URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { url = BuildConfig.DEFAULT_STYLE_URL }) { Text("Reset to OpenFreeMap") }
                Text(
                    "Map data (c) OpenStreetMap contributors, ODbL. Tiles by OpenFreeMap.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url) }, enabled = url.startsWith("http")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
