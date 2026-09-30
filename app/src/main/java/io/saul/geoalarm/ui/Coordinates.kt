package io.saul.geoalarm.ui

import io.saul.geoalarm.engine.GeoPoint

object Coordinates {
    private val pair = Regex("""^\s*(-?\d{1,3}(?:\.\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")

    /** Parses "lat, lon" in decimal degrees. Returns null if malformed or out of range. */
    fun parse(text: String): GeoPoint? {
        val m = pair.matchEntire(text) ?: return null
        val lat = m.groupValues[1].toDouble()
        val lon = m.groupValues[2].toDouble()
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return GeoPoint(lat, lon)
    }
}
