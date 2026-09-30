package io.saul.geoalarm.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.saul.geoalarm.engine.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object CurrentLocation {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * A fresh fix from GPS (no network needed), falling back to the best last-known fix.
     * Returns null if nothing arrives within [timeoutMs].
     */
    @SuppressLint("MissingPermission")
    suspend fun get(context: Context, timeoutMs: Long = 30_000): GeoPoint? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val lastKnown = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 2 * 60_000 }
            .minByOrNull { it.accuracy }
        if (lastKnown != null) return lastKnown.toGeoPoint()

        val provider = providers.firstOrNull() ?: return null
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = androidx.core.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) {
                    if (cont.isActive) cont.resume(it)
                }
            }
        }
        return (fresh ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time })?.toGeoPoint()
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)
}
