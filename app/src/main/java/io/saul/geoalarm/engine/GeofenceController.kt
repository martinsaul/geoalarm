package io.saul.geoalarm.engine

import android.content.Context
import android.util.Log
import io.saul.geoalarm.data.GeoAlarmDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single entry point that keeps the right engine armed with the enabled fences.
 * Call [sync] whenever fences change, on boot, and after an app update.
 */
object GeofenceController {
    private const val TAG = "GeofenceController"
    private val mutex = Mutex()

    suspend fun sync(context: Context) = mutex.withLock {
        val app = context.applicationContext
        val fences = GeoAlarmDatabase.get(app).fenceDao().enabled().map(FenceSpec::from)
        val active = EngineSelector.choose(app, fences.size)
        for (engine in EngineSelector.all(app)) {
            if (engine.name == active.name) continue
            runCatching { engine.disarmAll() }
        }
        if (!Permissions.canMonitor(app)) {
            Log.w(TAG, "Missing location permission; ${fences.size} fences not armed")
            runCatching { active.disarmAll() }
            return@withLock
        }
        runCatching { active.arm(fences) }
            .onSuccess { Log.i(TAG, "Armed ${fences.size} fences on ${active.name}") }
            .onFailure { e ->
                Log.w(TAG, "${active.name} failed to arm, falling back to local", e)
                if (active.name != "local") LocalGeofenceEngine(app).arm(fences)
            }
    }
}
