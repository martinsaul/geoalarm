package io.saul.geoalarm.engine

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.saul.geoalarm.MainActivity
import io.saul.geoalarm.R
import io.saul.geoalarm.alarm.Notifications
import io.saul.geoalarm.alarm.TriggerDispatcher
import io.saul.geoalarm.data.GeoAlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Local geofence engine. Listens to GPS (no network needed) at an adaptive interval and feeds
 * fixes through [FenceTracker]. Stops itself when no fences are enabled.
 */
class GeofenceService : Service(), LocationListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tracker: FenceTracker
    private lateinit var stateStore: FenceStateStore
    private var fences: List<FenceSpec> = emptyList()
    private var intervalMs = PollingPolicy.MIN_INTERVAL_MS
    private var registered = false
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        stateStore = FenceStateStore(this)
        tracker = FenceTracker(initialState = stateStore.load())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = runCatching {
            ServiceCompat.startForeground(
                this, Notifications.TRACKING_ID, trackingNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        }
        if (started.isFailure) {
            // Typically: location permission revoked, or a background start without background location.
            Log.w(TAG, "Cannot start location foreground service", started.exceptionOrNull())
            stopSelf()
            return START_NOT_STICKY
        }
        if (watchJob == null) {
            watchJob = scope.launch {
                GeoAlarmDatabase.get(this@GeofenceService).fenceDao().observeAll()
                    .map { all -> all.filter { it.enabled }.map(FenceSpec::from) }
                    .collectLatest { armed ->
                        fences = armed
                        if (armed.isEmpty()) stopSelf() else requestUpdates(PollingPolicy.MIN_INTERVAL_MS, force = true)
                    }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(newIntervalMs: Long, force: Boolean = false) {
        if (!force && registered && !PollingPolicy.shouldReschedule(intervalMs, newIntervalMs)) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        runCatching { lm.removeUpdates(this) }
        intervalMs = newIntervalMs
        registered = runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, this, Looper.getMainLooper())
            // Free rides: fixes other apps request (including network location where it exists).
            if (lm.allProviders.contains(LocationManager.PASSIVE_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, PollingPolicy.MIN_INTERVAL_MS, 0f, this, Looper.getMainLooper())
            }
            true
        }.onFailure { Log.w(TAG, "requestLocationUpdates failed", it) }.getOrDefault(false)
        Log.d(TAG, "Polling every ${intervalMs / 1000}s for ${fences.size} fences")
    }

    override fun onLocationChanged(location: Location) {
        if (fences.isEmpty()) return
        // Ignore wildly inaccurate fixes; they would only widen the exit margin anyway.
        if (location.hasAccuracy() && location.accuracy > 500f) return
        val here = GeoPoint(location.latitude, location.longitude)
        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else 50.0
        val transitions = tracker.onFix(here, accuracy, fences)
        stateStore.save(tracker.snapshot())
        if (transitions.isNotEmpty()) {
            scope.launch { transitions.forEach { (fence, t) -> TriggerDispatcher.dispatch(this@GeofenceService, fence.id, t) } }
        }
        val speed = if (location.hasSpeed()) location.speed.toDouble() else null
        val edge = tracker.distanceToNearestEdge(here, fences)
        Log.d(TAG, "Fix ${location.provider} acc=${accuracy.toInt()}m edge=${edge?.toInt()}m speed=$speed -> ${transitions.size} transitions")
        requestUpdates(PollingPolicy.intervalMs(edge, speed))
    }

    // Older platforms call these; defaults avoid AbstractMethodError on API < 30.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    override fun onDestroy() {
        runCatching { getSystemService(LocationManager::class.java)?.removeUpdates(this) }
        scope.cancel()
        super.onDestroy()
    }

    private fun trackingNotification(): Notification {
        Notifications.ensureChannels(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_stat_fence)
            .setContentTitle(getString(R.string.tracking_title))
            .setContentText(getString(R.string.tracking_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private companion object {
        const val TAG = "GeofenceService"
    }
}
