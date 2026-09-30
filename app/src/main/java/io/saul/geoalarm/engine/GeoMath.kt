package io.saul.geoalarm.engine

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val latitude: Double, val longitude: Double)

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres (haversine). Accurate to well under 0.5% for fence-sized distances. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Point reached by travelling [distanceMeters] from [origin] on initial [bearingDegrees] (0 = north). */
    fun destination(origin: GeoPoint, distanceMeters: Double, bearingDegrees: Double): GeoPoint {
        val delta = distanceMeters / EARTH_RADIUS_M
        val theta = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(origin.latitude)
        val lon1 = Math.toRadians(origin.longitude)
        val lat2 = asin(sin(lat1) * cos(delta) + cos(lat1) * sin(delta) * cos(theta))
        val lon2 = lon1 + atan2(sin(theta) * sin(delta) * cos(lat1), cos(delta) - sin(lat1) * sin(lat2))
        val lonDeg = (Math.toDegrees(lon2) + 540.0) % 360.0 - 180.0
        return GeoPoint(Math.toDegrees(lat2), lonDeg)
    }

    /** Closed ring of [segments] + 1 points approximating a circle, for drawing a fence on the map. */
    fun circleRing(center: GeoPoint, radiusMeters: Double, segments: Int = 64): List<GeoPoint> {
        val ring = (0 until segments).map { destination(center, radiusMeters, it * 360.0 / segments) }
        return ring + ring.first()
    }
}
