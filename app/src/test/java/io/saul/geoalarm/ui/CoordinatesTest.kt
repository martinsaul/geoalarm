package io.saul.geoalarm.ui

import io.saul.geoalarm.engine.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinatesTest {
    @Test
    fun parses() {
        assertEquals(GeoPoint(47.6062, -122.3321), Coordinates.parse("47.6062, -122.3321"))
        assertEquals(GeoPoint(-33.9, 151.2), Coordinates.parse(" -33.9 151.2 "))
        assertEquals(GeoPoint(10.0, 20.0), Coordinates.parse("10;20"))
    }

    @Test
    fun rejects() {
        assertNull(Coordinates.parse(""))
        assertNull(Coordinates.parse("91, 0"))
        assertNull(Coordinates.parse("0, 181"))
        assertNull(Coordinates.parse("abc, def"))
    }
}
