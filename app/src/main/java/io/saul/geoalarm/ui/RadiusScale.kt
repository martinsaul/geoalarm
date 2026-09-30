package io.saul.geoalarm.ui

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Logarithmic slider mapping so 50 m and 50 km are both easy to hit. */
object RadiusScale {
    const val MIN_METERS = 50.0
    const val MAX_METERS = 50_000.0
    private val span = ln(MAX_METERS / MIN_METERS)

    fun toMeters(position: Float): Double =
        MIN_METERS * (MAX_METERS / MIN_METERS).pow(position.toDouble().coerceIn(0.0, 1.0))

    fun toPosition(meters: Double): Float =
        (ln(meters.coerceIn(MIN_METERS, MAX_METERS) / MIN_METERS) / span).toFloat()

    /** Round to a human-friendly step for the current magnitude. */
    fun snap(meters: Double): Double {
        val step = when {
            meters < 200 -> 10.0
            meters < 1_000 -> 50.0
            meters < 5_000 -> 100.0
            meters < 20_000 -> 500.0
            else -> 1_000.0
        }
        return ((meters / step).roundToInt() * step).coerceIn(MIN_METERS, MAX_METERS)
    }

    fun label(meters: Double): String =
        if (meters < 1_000) "${meters.roundToInt()} m"
        else if (meters < 10_000) "%.1f km".format(meters / 1_000)
        else "${(meters / 1_000).roundToInt()} km"
}
