package io.saul.geoalarm.engine

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

/** Play Services geofencing: the OS batches location for us, so it's the lightest on battery. */
class GmsGeofenceEngine(private val context: Context) : GeofenceEngine {
    override val name = "gms"

    private val client = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context, 0, Intent(context, GmsGeofenceReceiver::class.java),
            // Play Services fills in the event extras, so this must be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun arm(fences: List<FenceSpec>) {
        client.removeGeofences(pendingIntent).await()
        if (fences.isEmpty()) return
        val geofences = fences.map { f ->
            var types = 0
            if (f.onEnter) types = types or Geofence.GEOFENCE_TRANSITION_ENTER
            if (f.onExit) types = types or Geofence.GEOFENCE_TRANSITION_EXIT
            Geofence.Builder()
                .setRequestId(f.id.toString())
                .setCircularRegion(f.center.latitude, f.center.longitude, f.radiusMeters.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(types)
                .setNotificationResponsiveness(RESPONSIVENESS_MS)
                .build()
        }
        val request = GeofencingRequest.Builder()
            // No initial trigger: creating a fence you're standing in shouldn't ring.
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()
        client.addGeofences(request, pendingIntent).await()
    }

    override suspend fun disarmAll() {
        runCatching { client.removeGeofences(pendingIntent).await() }
    }

    companion object {
        /** Play Services limit per app. Above this the controller falls back to the local engine. */
        const val MAX_FENCES = 100
        private const val RESPONSIVENESS_MS = 30_000
    }
}
