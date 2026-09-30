package io.saul.geoalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.saul.geoalarm.offline.RegionInfo
import io.saul.geoalarm.offline.TileMath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSheet(
    regions: List<RegionInfo>,
    onDismiss: () -> Unit,
    onDownloadVisible: () -> Unit,
    onToggle: (RegionInfo) -> Unit,
    onUpdate: (RegionInfo) -> Unit,
    onDelete: (RegionInfo) -> Unit,
    onShow: (RegionInfo) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<RegionInfo?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Offline maps", style = MaterialTheme.typography.titleLarge)
            Text(
                "Download only the areas you need. Alarms work without internet either way; this is so you can see the map.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onDownloadVisible, modifier = Modifier.fillMaxWidth()) { Text("Download the area on screen") }
            val total = regions.sumOf { it.bytes }
            if (regions.isNotEmpty()) {
                Text("${regions.size} area(s), ${TileMath.formatBytes(total)} on this phone", style = MaterialTheme.typography.bodySmall)
            }
        }
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(regions, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(r.name) },
                    supportingContent = {
                        Column {
                            val status = when {
                                r.complete -> "Ready - ${TileMath.formatBytes(r.bytes)}"
                                r.active -> "Downloading ${(r.progress * 100).toInt()}% - ${TileMath.formatBytes(r.bytes)}"
                                else -> "Paused ${(r.progress * 100).toInt()}% - ${TileMath.formatBytes(r.bytes)}"
                            }
                            Text(status)
                            if (!r.complete) LinearProgressIndicator(progress = { r.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            r.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Row {
                                TextButton(onClick = { onShow(r) }) { Text("Show") }
                                if (r.complete) TextButton(onClick = { onUpdate(r) }) { Text("Update") }
                                else TextButton(onClick = { onToggle(r) }) { Text(if (r.active) "Pause" else "Resume") }
                                TextButton(onClick = { confirmDelete = r }) { Text("Delete") }
                            }
                        }
                    },
                )
            }
        }
    }
    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${r.name}?") },
            text = { Text("Frees ${TileMath.formatBytes(r.bytes)}. Your fences are not affected.") },
            confirmButton = { TextButton(onClick = { onDelete(r); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

/** Confirms a download with a name and an up-front size estimate. */
@Composable
fun DownloadRegionDialog(
    bounds: TileMath.Bounds,
    suggestedName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    val tiles = remember(bounds) { TileMath.tileCount(bounds) }
    val tooBig = tiles > TileMath.MAX_TILES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download for offline use") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                if (tooBig) {
                    Text(
                        "This area is too large (${"%,d".format(tiles)} tiles, limit ${"%,d".format(TileMath.MAX_TILES)}). Zoom in and try again.",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text("About ${TileMath.formatBytes(TileMath.estimateBytes(tiles))} (${"%,d".format(tiles)} tiles). Prefer Wi-Fi for large areas.")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.ifBlank { suggestedName }) }, enabled = !tooBig) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
