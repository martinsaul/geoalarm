package io.saul.geoalarm.engine

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** GMS build: prefer Play Services geofencing, fall back to the on-device engine if it's missing. */
object EngineSelector {
    fun hasPlayServicesGeofencing(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
