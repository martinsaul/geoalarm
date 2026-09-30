package io.saul.geoalarm.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** On-device engine: a location foreground service that runs only while fences are armed. */
class LocalGeofenceEngine(private val context: Context) : GeofenceEngine {
    override val name = "local"

    override suspend fun arm(fences: List<FenceSpec>) {
        if (fences.isEmpty()) return disarmAll()
        // The service reads the armed set from the database itself; this just (re)starts it.
        ContextCompat.startForegroundService(context, Intent(context, GeofenceService::class.java))
    }

    override suspend fun disarmAll() {
        context.stopService(Intent(context, GeofenceService::class.java))
    }
}
