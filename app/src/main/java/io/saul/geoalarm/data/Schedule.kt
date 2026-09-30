package io.saul.geoalarm.data

import java.time.DayOfWeek
import java.time.LocalDateTime

/** When a fence is allowed to fire. Pure so it can be unit tested. */
object Schedule {
    const val ALL_DAYS = 0b111_1111

    fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun isActive(activeDays: Int, windowStart: Int?, windowEnd: Int?, now: LocalDateTime): Boolean {
        val minutes = now.hour * 60 + now.minute
        val today = now.dayOfWeek
        if (windowStart == null || windowEnd == null || windowStart == windowEnd) {
            return activeDays and dayBit(today) != 0
        }
        return if (windowStart < windowEnd) {
            activeDays and dayBit(today) != 0 && minutes in windowStart until windowEnd
        } else {
            // Overnight window, e.g. 22:00-06:00: the early-morning part belongs to the previous day's schedule.
            when {
                minutes >= windowStart -> activeDays and dayBit(today) != 0
                minutes < windowEnd -> activeDays and dayBit(today.minus(1)) != 0
                else -> false
            }
        }
    }

    fun isActive(f: Fence, now: LocalDateTime) = isActive(f.activeDays, f.windowStartMinutes, f.windowEndMinutes, now)

    fun formatMinutes(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    /** Short human summary, or null when the fence is always active. */
    fun describe(f: Fence): String? {
        val days = if (f.activeDays == ALL_DAYS) null else DayOfWeek.entries
            .filter { f.activeDays and dayBit(it) != 0 }
            .joinToString(" ") { it.name.take(2).lowercase().replaceFirstChar(Char::uppercase) }
            .ifEmpty { "never" }
        val window = if (f.windowStartMinutes != null && f.windowEndMinutes != null && f.windowStartMinutes != f.windowEndMinutes) {
            "${formatMinutes(f.windowStartMinutes)}-${formatMinutes(f.windowEndMinutes)}"
        } else null
        return listOfNotNull(days, window).joinToString(", ").ifEmpty { null }
    }
}
