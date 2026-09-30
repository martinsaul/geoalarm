package io.saul.geoalarm.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.saul.geoalarm.GeoAlarmApp
import io.saul.geoalarm.engine.Permissions

/** One thing the app needs before alarms can fire in the background. */
enum class PermissionStep(val title: String, val why: String, val required: Boolean) {
    LOCATION(
        "Allow location",
        "GeoAlarm needs your precise location to know when you reach a fence. GPS works without internet.",
        true,
    ),
    BACKGROUND(
        "Allow location all the time",
        "Alarms must fire while the app is closed. On the next screen choose \"Allow all the time\".",
        true,
    ),
    NOTIFICATIONS(
        "Allow notifications",
        "Alarms and reminders are delivered as notifications.",
        true,
    ),
    FULL_SCREEN(
        "Allow full-screen alarms",
        "Lets an alarm take over the lock screen like a clock alarm does.",
        false,
    ),
    BATTERY(
        "Don't restrict battery",
        "Stops Android from pausing location checks to save power, which can make alarms late.",
        false,
    );

    fun isDone(context: Context): Boolean = when (this) {
        LOCATION -> Permissions.fineLocation(context)
        BACKGROUND -> Permissions.backgroundLocation(context)
        NOTIFICATIONS -> Permissions.notifications(context)
        FULL_SCREEN -> Permissions.fullScreenIntent(context)
        BATTERY -> Permissions.batteryUnrestricted(context)
    }
}

/**
 * Walks the user through the permissions one at a time, explaining each.
 * Shows nothing once everything required is granted and optional steps are granted or skipped.
 */
@Composable
fun PermissionSteps(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var skipped by rememberSaveable { mutableStateOf(setOf<String>()) }

    // Settings screens return via onResume, not a result callback.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val onGranted = {
        refresh++
        (context.applicationContext as GeoAlarmApp).resync()
    }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onGranted() }
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onGranted() }

    @Suppress("UNUSED_EXPRESSION") refresh
    val step = PermissionStep.entries.firstOrNull { !it.isDone(context) && it.name !in skipped } ?: return
    // Background location can only be asked for after foreground location.
    val grant: () -> Unit = {
        when (step) {
            PermissionStep.LOCATION -> fineLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            PermissionStep.BACKGROUND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            PermissionStep.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionStep.FULL_SCREEN -> openFullScreenSettings(context)
            PermissionStep.BATTERY -> requestBatteryExemption(context)
        }
    }

    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(step.title, style = MaterialTheme.typography.titleMedium)
            Text(step.why, style = MaterialTheme.typography.bodyMedium)
            Row {
                if (!step.required) TextButton(onClick = { skipped = skipped + step.name }) { Text("Not now") }
                Spacer(Modifier.weight(1f))
                Button(onClick = grant) { Text("Continue") }
            }
        }
    }
}

private fun openFullScreenSettings(context: Context) {
    if (Build.VERSION.SDK_INT < 34) return
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
    )
}

@SuppressLint("BatteryLife") // Location alarms are the textbook case for this exemption; not a Play-distributed build.
private fun requestBatteryExemption(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
    )
}
