package io.saul.geoalarm.engine

import android.content.Context
import android.util.Log
import io.saul.geoalarm.data.GeoAlarmDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single entry point that keeps the right engine armed with the enabled fences.
 * Call [sync] whenever fences or connectivity change, on boot, and after an app update.
 */
object GeofenceController {
    private const val TAG = "GeofenceController"
    private val mutex = Mutex()

    /** Engine currently armed ("gms", "local", or "none"), for the settings screen. */
    private val _active = MutableStateFlow("none")
    val active: StateFlow<String> = _active.asStateFlow()

    suspend fun sync(context: Context) = mutex.withLock {
        val app = context.applicationContext
        val fences = GeoAlarmDatabase.get(app).fenceDao().enabled().map(FenceSpec::from)
        val active = EngineSelector.choose(app, fences.size)
        for (engine in EngineSelector.all(app)) {
            if (engine.name == active.name) continue
            runCatching { engine.disarmAll() }
        }
        if (!Permissions.canMonitor(app) || fences.isEmpty()) {
            if (fences.isNotEmpty()) Log.w(TAG, "Missing location permission; ${fences.size} fences not armed")
            runCatching { active.disarmAll() }
            ResyncReceiver.cancel(app)
            _active.value = "none"
            return@withLock
        }
        val armedOn = runCatching { active.arm(fences); active.name }
            .getOrElse { e ->
                Log.w(TAG, "${active.name} failed to arm, falling back to local", e)
                LocalGeofenceEngine(app).arm(fences)
                EnginePolicy.LOCAL
            }
        Log.i(TAG, "Armed ${fences.size} fences on $armedOn")
        _active.value = armedOn
        // Only Play Services needs the periodic check; the local engine runs in the foreground.
        if (armedOn == EnginePolicy.GMS) ResyncReceiver.schedule(app) else ResyncReceiver.cancel(app)
    }
}
