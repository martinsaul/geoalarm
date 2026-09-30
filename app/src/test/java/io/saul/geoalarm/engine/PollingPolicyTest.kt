package io.saul.geoalarm.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollingPolicyTest {
    @Test
    fun `no fences sleeps the maximum`() {
        assertEquals(PollingPolicy.MAX_INTERVAL_MS, PollingPolicy.intervalMs(null, null))
    }

    @Test
    fun `near an edge polls at the minimum`() {
        assertEquals(PollingPolicy.MIN_INTERVAL_MS, PollingPolicy.intervalMs(5.0, 1.0))
    }

    @Test
    fun `far away and stationary is capped`() {
        assertEquals(PollingPolicy.MAX_INTERVAL_MS, PollingPolicy.intervalMs(50_000.0, 0.0))
    }

    @Test
    fun `driving toward a fence polls faster than walking`() {
        val walking = PollingPolicy.intervalMs(2_000.0, 1.4)
        val driving = PollingPolicy.intervalMs(2_000.0, 30.0)
        assertTrue(driving < walking)
        // 2 km at 30 m/s is ~67 s to the edge, so about 22 s between fixes.
        assertEquals(22_222L, driving)
    }

    @Test
    fun `small changes don't reschedule`() {
        assertFalse(PollingPolicy.shouldReschedule(60_000, 70_000))
        assertTrue(PollingPolicy.shouldReschedule(60_000, 20_000))
        assertTrue(PollingPolicy.shouldReschedule(60_000, 120_000))
    }
}
