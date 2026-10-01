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
        // Handover from the local engine: if we last knew you were outside a fence, an ENTER-on-add means
        // you crossed in during the switch, and vice versa. Fences with no known state don't fire on add,
        // so creating a fence you're standing in doesn't ring.
        val known = FenceStateStore(context).load()
        val groups = fences.groupBy { f ->
            when (known[f.id]) {
                false -> GeofencingRequest.INITIAL_TRIGGER_ENTER
                true -> GeofencingRequest.INITIAL_TRIGGER_EXIT
                null -> 0
            }
        }
        for ((initial, group) in groups) {
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(initial)
                .addGeofences(group.map(::toGeofence))
                .build()
            client.addGeofences(request, pendingIntent).await()
        }
    }

    private fun toGeofence(f: FenceSpec): Geofence {
        return Geofence.Builder()
            .setRequestId(f.id.toString())
            .setCircularRegion(f.center.latitude, f.center.longitude, f.radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            // Always watch both directions so the handover state stays right; the dispatcher filters.
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(RESPONSIVENESS_MS)
            .build()
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
