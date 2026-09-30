package io.saul.geoalarm.engine

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** GMS build: prefer Play Services geofencing, fall back to the on-device engine if it's missing. */
object EngineSelector {
    fun hasPlayServices(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    fun all(context: Context): List<GeofenceEngine> = listOf(GmsGeofenceEngine(context), LocalGeofenceEngine(context))

    fun choose(context: Context, fenceCount: Int): GeofenceEngine =
        if (hasPlayServices(context) && fenceCount <= GmsGeofenceEngine.MAX_FENCES) GmsGeofenceEngine(context)
        else LocalGeofenceEngine(context)
}
