package io.saul.geoalarm.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import io.saul.geoalarm.alarm.TriggerDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GmsGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (event.hasError()) {
                    Log.w(TAG, "Geofence error: ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
                    // GEOFENCE_NOT_AVAILABLE: location turned off or Play Services reset; hand over to the local engine.
                    if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) GeofenceController.sync(context)
                    return@launch
                }
                val transition = when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> Transition.ENTER
                    Geofence.GEOFENCE_TRANSITION_EXIT -> Transition.EXIT
                    else -> return@launch
                }
                val store = FenceStateStore(context)
                for (g in event.triggeringGeofences.orEmpty()) {
                    val id = g.requestId.toLongOrNull() ?: continue
                    store.set(id, transition == Transition.ENTER)
                    TriggerDispatcher.dispatch(context, id, transition)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GmsGeofenceReceiver"
    }
}
