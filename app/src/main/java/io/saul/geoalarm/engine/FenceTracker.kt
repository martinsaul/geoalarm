package io.saul.geoalarm.engine

/**
 * Pure enter/exit state machine used by the local (non-GMS) engine.
 *
 * Hysteresis: you are "inside" once within radius, and only "outside" again once beyond
 * radius + [exitMarginMeters]. Location fixes worse than their own accuracy are handled by
 * widening that margin, so GPS jitter at the edge doesn't flap the alarm.
 */
class FenceTracker(private val exitMarginMeters: Double = 25.0) {

    private val inside = mutableMapOf<Long, Boolean>()

    /** Returns the transitions caused by this fix, in fence order. The first fix only establishes state. */
    fun onFix(position: GeoPoint, accuracyMeters: Double, fences: List<FenceSpec>): List<Pair<FenceSpec, Transition>> {
        val result = mutableListOf<Pair<FenceSpec, Transition>>()
        val liveIds = fences.map { it.id }.toSet()
        inside.keys.retainAll(liveIds)

        for (fence in fences) {
            val distance = GeoMath.distanceMeters(position, fence.center)
            val margin = maxOf(exitMarginMeters, accuracyMeters)
            val was = inside[fence.id]
            val now = when (was) {
                null -> distance <= fence.radiusMeters
                true -> distance <= fence.radiusMeters + margin
                false -> distance <= fence.radiusMeters
            }
            inside[fence.id] = now
            if (was == null || was == now) continue
            if (now && fence.onEnter) result += fence to Transition.ENTER
            if (!now && fence.onExit) result += fence to Transition.EXIT
        }
        return result
    }

    /** Distance from [position] to the nearest fence edge, used to pick the next polling interval. */
    fun distanceToNearestEdge(position: GeoPoint, fences: List<FenceSpec>): Double? =
        fences.minOfOrNull { kotlin.math.abs(GeoMath.distanceMeters(position, it.center) - it.radiusMeters) }
}
