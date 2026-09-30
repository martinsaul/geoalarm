package io.saul.geoalarm.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {
    @Test
    fun `zero distance to self`() {
        val p = GeoPoint(47.6062, -122.3321)
        assertEquals(0.0, GeoMath.distanceMeters(p, p), 1e-6)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        val d = GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        assertEquals(111_195.0, d, 50.0)
    }

    @Test
    fun `seattle to portland is about 234 km`() {
        val d = GeoMath.distanceMeters(GeoPoint(47.6062, -122.3321), GeoPoint(45.5152, -122.6784))
        assertEquals(234_000.0, d, 2_000.0)
    }

    @Test
    fun `antimeridian crossing takes the short way`() {
        val d = GeoMath.distanceMeters(GeoPoint(0.0, 179.9), GeoPoint(0.0, -179.9))
        assertEquals(22_239.0, d, 50.0)
    }

    @Test
    fun `destination lands at the requested distance`() {
        val origin = GeoPoint(47.6, -122.3)
        for (bearing in listOf(0.0, 45.0, 90.0, 180.0, 270.0)) {
            val p = GeoMath.destination(origin, 1_000.0, bearing)
            assertEquals(1_000.0, GeoMath.distanceMeters(origin, p), 0.5)
        }
    }

    @Test
    fun `circle ring is closed and on the radius`() {
        val c = GeoPoint(0.0, 179.99)
        val ring = GeoMath.circleRing(c, 500.0, segments = 32)
        assertEquals(33, ring.size)
        assertEquals(ring.first(), ring.last())
        ring.forEach { assertEquals(500.0, GeoMath.distanceMeters(c, it), 0.5) }
    }
}
