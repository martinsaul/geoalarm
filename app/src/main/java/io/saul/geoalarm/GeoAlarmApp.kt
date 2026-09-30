package io.saul.geoalarm

import android.app.Application
import org.maplibre.android.MapLibre

class GeoAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // No API key: MapLibre talks to plain OSM vector tile servers.
        MapLibre.getInstance(this)
    }
}
