package io.saul.geoalarm.engine

import android.content.Context

/** F-Droid build: no Play Services, always the on-device engine. */
object EngineSelector {
    fun all(context: Context): List<GeofenceEngine> = listOf(LocalGeofenceEngine(context))

    fun choose(context: Context, fenceCount: Int): GeofenceEngine = LocalGeofenceEngine(context)
}
