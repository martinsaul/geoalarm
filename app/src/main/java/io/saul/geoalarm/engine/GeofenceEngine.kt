package io.saul.geoalarm.engine

/** A circular fence as the engines see it. */
data class FenceSpec(
    val id: Long,
    val center: GeoPoint,
    val radiusMeters: Double,
    val onEnter: Boolean,
    val onExit: Boolean,
)

enum class Transition { ENTER, EXIT }

/**
 * Arms and disarms fences. Two implementations (GEO-4):
 *  - Play Services GeofencingClient (gms flavor, when Play Services is available)
 *  - LocalGeofenceEngine: on-device GPS + distance checks (both flavors, fallback)
 * Neither needs a network connection to fire.
 */
interface GeofenceEngine {
    val name: String
    suspend fun arm(fences: List<FenceSpec>)
    suspend fun disarmAll()
}
