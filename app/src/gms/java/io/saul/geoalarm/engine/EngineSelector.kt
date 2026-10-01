package io.saul.geoalarm.engine

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** GMS build: hybrid. Play Services while online, the on-device GPS engine while offline. */
object EngineSelector {
    fun hasPlayServices(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    fun all(context: Context): List<GeofenceEngine> = listOf(GmsGeofenceEngine(context), LocalGeofenceEngine(context))

    fun choose(context: Context, fenceCount: Int): GeofenceEngine =
        when (EnginePolicy.pick(hasPlayServices(context), fenceCount, Connectivity.isOnline(context), GmsGeofenceEngine.MAX_FENCES)) {
            EnginePolicy.GMS -> GmsGeofenceEngine(context)
            else -> LocalGeofenceEngine(context)
        }
}
