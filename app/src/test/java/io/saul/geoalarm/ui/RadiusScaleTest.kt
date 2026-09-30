package io.saul.geoalarm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RadiusScaleTest {
    @Test
    fun `ends of the slider are the limits`() {
        assertEquals(50.0, RadiusScale.toMeters(0f), 1e-6)
        assertEquals(50_000.0, RadiusScale.toMeters(1f), 1e-6)
    }

    @Test
    fun `position round trips`() {
        for (m in listOf(50.0, 300.0, 1_500.0, 12_000.0, 50_000.0)) {
            assertEquals(m, RadiusScale.toMeters(RadiusScale.toPosition(m)), m * 1e-5)
        }
    }

    @Test
    fun `snap picks friendly steps`() {
        assertEquals(120.0, RadiusScale.snap(123.4), 0.0)
        assertEquals(650.0, RadiusScale.snap(640.0), 0.0)
        assertEquals(2_300.0, RadiusScale.snap(2_334.0), 0.0)
        assertEquals(50.0, RadiusScale.snap(10.0), 0.0)
    }

    @Test
    fun labels() {
        assertEquals("300 m", RadiusScale.label(300.0))
        assertEquals("1.5 km", RadiusScale.label(1_500.0))
        assertEquals("25 km", RadiusScale.label(25_000.0))
    }
}
