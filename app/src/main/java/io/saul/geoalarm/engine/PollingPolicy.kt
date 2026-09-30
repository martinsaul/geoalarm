package io.saul.geoalarm.engine

/**
 * How often the local engine asks for a GPS fix. Far from every fence we can sleep a long time;
 * close to an edge we poll fast so the alarm isn't late.
 */
object PollingPolicy {
    const val MIN_INTERVAL_MS = 5_000L
    const val MAX_INTERVAL_MS = 5 * 60_000L

    /** Assume at least brisk-walking speed so a stationary phone still wakes up in time. */
    private const val MIN_SPEED_MPS = 1.5

    /** Poll often enough to take about 3 fixes before reaching the nearest edge. */
    fun intervalMs(distanceToEdgeMeters: Double?, speedMps: Double?): Long {
        if (distanceToEdgeMeters == null) return MAX_INTERVAL_MS
        val speed = maxOf(speedMps ?: 0.0, MIN_SPEED_MPS)
        val secondsToEdge = distanceToEdgeMeters / speed
        return (secondsToEdge * 1000 / 3).toLong().coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }

    /** Only re-register location updates when the interval changes meaningfully. */
    fun shouldReschedule(currentMs: Long, newMs: Long): Boolean =
        newMs < currentMs * 0.66 || newMs > currentMs * 1.5
}
