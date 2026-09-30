package io.saul.geoalarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ScheduleTest {
    // 2026-09-28 is a Monday.
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 9, 28, h, m).plusDays(day.toLong())
    private val weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        .fold(0) { acc, d -> acc or Schedule.dayBit(d) }

    @Test
    fun `always active by default`() {
        assertTrue(Schedule.isActive(Schedule.ALL_DAYS, null, null, at(6, 3)))
    }

    @Test
    fun `weekdays only`() {
        assertTrue(Schedule.isActive(weekdays, null, null, at(4, 12))) // Friday
        assertFalse(Schedule.isActive(weekdays, null, null, at(5, 12))) // Saturday
    }

    @Test
    fun `daytime window is start-inclusive, end-exclusive`() {
        assertFalse(Schedule.isActive(Schedule.ALL_DAYS, 9 * 60, 17 * 60, at(0, 8, 59)))
        assertTrue(Schedule.isActive(Schedule.ALL_DAYS, 9 * 60, 17 * 60, at(0, 9, 0)))
        assertFalse(Schedule.isActive(Schedule.ALL_DAYS, 9 * 60, 17 * 60, at(0, 17, 0)))
    }

    @Test
    fun `overnight window belongs to the day it started`() {
        // Weekdays 22:00-06:00. Saturday 02:00 belongs to Friday night: active. Monday 02:00 belongs to Sunday: not.
        assertTrue(Schedule.isActive(weekdays, 22 * 60, 6 * 60, at(5, 2)))
        assertFalse(Schedule.isActive(weekdays, 22 * 60, 6 * 60, at(0, 2)))
        assertTrue(Schedule.isActive(weekdays, 22 * 60, 6 * 60, at(0, 23)))
        assertFalse(Schedule.isActive(weekdays, 22 * 60, 6 * 60, at(0, 12)))
    }

    @Test
    fun describe() {
        val f = Fence(label = "x", latitude = 0.0, longitude = 0.0, radiusMeters = 100.0)
        assertNull(Schedule.describe(f))
        assertEquals("Mo Tu We Th Fr, 07:30-19:00", Schedule.describe(f.copy(activeDays = weekdays, windowStartMinutes = 450, windowEndMinutes = 1140)))
    }
}
