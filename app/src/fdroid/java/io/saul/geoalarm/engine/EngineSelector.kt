package io.saul.geoalarm.engine

import android.content.Context

/** F-Droid build: no Play Services, always the on-device engine. */
object EngineSelector {
    fun hasPlayServicesGeofencing(context: Context): Boolean = false
}
