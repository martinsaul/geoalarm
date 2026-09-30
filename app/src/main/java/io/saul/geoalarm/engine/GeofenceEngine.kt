package io.saul.geoalarm.engine

import io.saul.geoalarm.data.Fence

/** A circular fence as the engines see it. */
data class FenceSpec(
    val id: Long,
    val center: GeoPoint,
    val radiusMeters: Double,
    val onEnter: Boolean,
    val onExit: Boolean,
) {
    companion object {
        fun from(f: Fence) = FenceSpec(f.id, GeoPoint(f.latitude, f.longitude), f.radiusMeters, f.onEnter, f.onExit)
    }
}

enum class Transition { ENTER, EXIT }

/**
 * Arms and disarms fences. Two implementations:
 *  - GmsGeofenceEngine: Play Services GeofencingClient (gms flavor, when Play Services is available)
 *  - LocalGeofenceEngine: on-device GPS + distance checks (both flavors, fallback)
 * Neither needs a network connection to fire.
 */
interface GeofenceEngine {
    val name: String

    /** Replace the armed set with [fences]. An empty list is the same as [disarmAll]. */
    suspend fun arm(fences: List<FenceSpec>)
    suspend fun disarmAll()
}
