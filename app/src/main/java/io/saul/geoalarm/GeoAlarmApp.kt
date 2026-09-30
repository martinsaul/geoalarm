package io.saul.geoalarm

import android.app.Application
import io.saul.geoalarm.alarm.Notifications
import io.saul.geoalarm.data.GeoAlarmDatabase
import io.saul.geoalarm.engine.GeofenceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class GeoAlarmApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // No API key: MapLibre talks to plain OSM vector tile servers.
        MapLibre.getInstance(this)
        Notifications.ensureChannels(this)

        // Keep the engines in step with the enabled fences for as long as the process lives.
        appScope.launch {
            GeoAlarmDatabase.get(this@GeoAlarmApp).fenceDao().observeAll()
                .map { all -> all.filter { it.enabled } }
                .distinctUntilChanged()
                .debounce(500)
                .collect { GeofenceController.sync(this@GeoAlarmApp) }
        }
    }

    /** Re-arm after permissions change (called from the UI). */
    fun resync() {
        appScope.launch { GeofenceController.sync(this@GeoAlarmApp) }
    }
}
