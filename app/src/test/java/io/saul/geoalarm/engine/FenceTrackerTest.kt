package io.saul.geoalarm.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FenceTrackerTest {
    private val center = GeoPoint(0.0, 0.0)
    private val fence = FenceSpec(id = 1, center = center, radiusMeters = 100.0, onEnter = true, onExit = true)

    /** Point [meters] north of the fence centre (1 m of latitude ~ 1/111195 degrees). */
    private fun north(meters: Double) = GeoPoint(meters / 111_195.0, 0.0)

    @Test
    fun `first fix only establishes state`() {
        val t = FenceTracker()
        assertTrue(t.onFix(north(10.0), 5.0, listOf(fence)).isEmpty())
    }

    @Test
    fun `walking in fires enter once`() {
        val t = FenceTracker()
        t.onFix(north(500.0), 5.0, listOf(fence))
        assertTrue(t.onFix(north(300.0), 5.0, listOf(fence)).isEmpty())
        assertEquals(listOf(fence to Transition.ENTER), t.onFix(north(90.0), 5.0, listOf(fence)))
        assertTrue(t.onFix(north(20.0), 5.0, listOf(fence)).isEmpty())
    }

    @Test
    fun `jitter at the edge does not flap`() {
        val t = FenceTracker(exitMarginMeters = 25.0)
        t.onFix(north(500.0), 5.0, listOf(fence))
        t.onFix(north(95.0), 5.0, listOf(fence)) // ENTER
        for (d in listOf(105.0, 98.0, 115.0, 99.0, 120.0)) {
            assertTrue("flapped at $d m", t.onFix(north(d), 5.0, listOf(fence)).isEmpty())
        }
        assertEquals(listOf(fence to Transition.EXIT), t.onFix(north(130.0), 5.0, listOf(fence)))
    }

    @Test
    fun `poor accuracy widens the exit margin`() {
        val t = FenceTracker(exitMarginMeters = 25.0)
        t.onFix(north(50.0), 5.0, listOf(fence))
        assertTrue(t.onFix(north(160.0), 80.0, listOf(fence)).isEmpty())
        assertEquals(listOf(fence to Transition.EXIT), t.onFix(north(160.0), 10.0, listOf(fence)))
    }

    @Test
    fun `exit-only fence ignores enter`() {
        val exitOnly = fence.copy(onEnter = false)
        val t = FenceTracker()
        t.onFix(north(500.0), 5.0, listOf(exitOnly))
        assertTrue(t.onFix(north(10.0), 5.0, listOf(exitOnly)).isEmpty())
        assertEquals(listOf(exitOnly to Transition.EXIT), t.onFix(north(500.0), 5.0, listOf(exitOnly)))
    }

    @Test
    fun `nearest edge distance`() {
        val t = FenceTracker()
        assertEquals(400.0, t.distanceToNearestEdge(north(500.0), listOf(fence))!!, 1.0)
    }
}
