package com.sportassistant.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleTest {
    // 2026-11-23 is a Monday.
    private val monday = LocalDate.of(2026, 11, 23)
    private fun at(date: LocalDate, h: Int, m: Int = 0): LocalDateTime = date.atTime(h, m)

    private fun hourly(now: LocalDateTime, weekdaysOnly: Boolean = false) =
        Schedule.nextInterval(now, startMin = 9 * 60, endMin = 19 * 60, everyMin = 60, weekdaysOnly = weekdaysOnly)

    @Test
    fun `before the window the first slot is the start`() {
        assertEquals(at(monday, 9), hourly(at(monday, 7, 30)))
    }

    @Test
    fun `inside the window it is the next grid time`() {
        assertEquals(at(monday, 11), hourly(at(monday, 10, 20)))
    }

    @Test
    fun `exactly on a slot gives the following one, never the same`() {
        assertEquals(at(monday, 10), hourly(at(monday, 9)))
    }

    @Test
    fun `the last slot of the day is inclusive`() {
        assertEquals(at(monday, 19), hourly(at(monday, 18, 1)))
    }

    @Test
    fun `after the last slot it rolls to the next morning`() {
        assertEquals(at(monday.plusDays(1), 9), hourly(at(monday, 19)))
        assertEquals(at(monday.plusDays(1), 9), hourly(at(monday, 23, 59)))
    }

    @Test
    fun `an interval that does not divide the window stops before the end`() {
        // 9:00, 10:30, 12:00, 13:30, 15:00, 16:30, 18:00; the next would be 19:30, after the window.
        val last = Schedule.nextInterval(at(monday, 16, 31), 9 * 60, 19 * 60, 90, weekdaysOnly = false)
        assertEquals(at(monday, 18), last)
        assertEquals(at(monday.plusDays(1), 9), Schedule.nextInterval(at(monday, 18), 9 * 60, 19 * 60, 90, false))
    }

    @Test
    fun `an offset shifts the whole grid`() {
        val water = Schedule.nextInterval(at(monday, 8), 9 * 60, 19 * 60, 90, false, offsetMin = 30)
        assertEquals(at(monday, 9, 30), water)
        assertEquals(at(monday, 11), Schedule.nextInterval(at(monday, 9, 30), 9 * 60, 19 * 60, 90, false, offsetMin = 30))
    }

    @Test
    fun `an offset that overshoots a short window still yields a reminder`() {
        val t = Schedule.nextInterval(at(monday, 8), startMin = 9 * 60, endMin = 9 * 60 + 10, everyMin = 60, weekdaysOnly = false, offsetMin = 30)
        assertEquals(at(monday, 9), t)
    }

    @Test
    fun `a window that is a single time works`() {
        assertEquals(at(monday, 12), Schedule.nextInterval(at(monday, 8), 12 * 60, 12 * 60, 60, false))
        assertEquals(at(monday.plusDays(1), 12), Schedule.nextInterval(at(monday, 12), 12 * 60, 12 * 60, 60, false))
    }

    @Test
    fun `weekdays only skips the weekend`() {
        val friday = monday.plusDays(4)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        val next = hourly(at(friday, 19), weekdaysOnly = true)
        assertEquals(at(monday.plusDays(7), 9), next)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
    }

    @Test
    fun `weekdays only on a saturday morning waits for monday`() {
        val saturday = monday.plusDays(5)
        assertEquals(at(monday.plusDays(7), 9), hourly(at(saturday, 10), weekdaysOnly = true))
    }

    @Test
    fun `weekends are used when weekdays only is off`() {
        val saturday = monday.plusDays(5)
        assertEquals(at(saturday, 11), hourly(at(saturday, 10, 30)))
    }

    @Test
    fun `too small an interval is raised to the minimum`() {
        val t = Schedule.nextInterval(at(monday, 9), 9 * 60, 19 * 60, everyMin = 1, weekdaysOnly = false)
        assertEquals(at(monday, 9, Schedule.MIN_INTERVAL_MIN), t)
    }

    @Test
    fun `an end before the start is treated as a one-slot window`() {
        val t = Schedule.nextInterval(at(monday, 8), startMin = 10 * 60, endMin = 9 * 60, everyMin = 60, weekdaysOnly = false)
        assertEquals(at(monday, 10), t)
    }

    @Test
    fun `fixed times pick the next one today`() {
        val times = listOf(8 * 60, 20 * 60)
        assertEquals(at(monday, 8), Schedule.nextFixed(at(monday, 7), times))
        assertEquals(at(monday, 20), Schedule.nextFixed(at(monday, 8), times))
        assertEquals(at(monday.plusDays(1), 8), Schedule.nextFixed(at(monday, 21), times))
    }

    @Test
    fun `fixed times may be given in any order and with duplicates`() {
        assertEquals(at(monday, 8), Schedule.nextFixed(at(monday, 6), listOf(20 * 60, 8 * 60, 8 * 60)))
    }

    @Test
    fun `no fixed times means no reminder`() {
        assertNull(Schedule.nextFixed(at(monday, 6), emptyList()))
    }

    @Test
    fun `fixed times ignore weekends`() {
        val sunday = monday.plusDays(6)
        assertEquals(at(sunday, 20), Schedule.nextFixed(at(sunday, 9), listOf(8 * 60, 20 * 60)))
    }

    @Test
    fun `a reminder that fired late is stale only beyond the grace period`() {
        val scheduled = 1_000_000L
        assertFalse(Schedule.isStale(scheduled, scheduled + 5 * 60_000))
        assertFalse(Schedule.isStale(scheduled, scheduled + Schedule.STALE_AFTER_MS))
        assertTrue(Schedule.isStale(scheduled, scheduled + Schedule.STALE_AFTER_MS + 1))
    }

    @Test
    fun `the window start cannot pass an hour before the end`() {
        assertEquals(18 * 60, ReminderLimits.shiftStart(18 * 60, 19 * 60, 60).coerceAtMost(18 * 60))
        assertEquals(10 * 60, ReminderLimits.shiftStart(9 * 60, 19 * 60, 60))
        assertEquals(18 * 60, ReminderLimits.shiftStart(18 * 60, 19 * 60, 60)) // already at the limit
        assertEquals(0, ReminderLimits.shiftStart(0, 19 * 60, -60))
    }

    @Test
    fun `the window end cannot get within an hour of the start or past 23 00`() {
        assertEquals(10 * 60, ReminderLimits.shiftEnd(9 * 60, 10 * 60, -60)) // already at the limit
        assertEquals(20 * 60, ReminderLimits.shiftEnd(9 * 60, 19 * 60, 60))
        assertEquals(23 * 60, ReminderLimits.shiftEnd(9 * 60, 23 * 60, 60))
    }

    @Test
    fun `a window pushed to the edges is still valid`() {
        var start = 9 * 60
        var end = 19 * 60
        repeat(30) { start = ReminderLimits.shiftStart(start, end, 60) }
        assertEquals(18 * 60, start)
        repeat(30) { end = ReminderLimits.shiftEnd(start, end, -60) }
        assertEquals(19 * 60, end)
        assertTrue(end - start >= 60)
    }

    @Test
    fun `a time of day wraps around midnight both ways`() {
        assertEquals(0, ReminderLimits.shiftTime(23 * 60 + 30, 30))
        assertEquals(23 * 60 + 30, ReminderLimits.shiftTime(0, -30))
        assertEquals(8 * 60 + 30, ReminderLimits.shiftTime(8 * 60, 30))
    }

    @Test
    fun `times are formatted with two digits`() {
        assertEquals("08:05", ReminderLimits.format(8 * 60 + 5))
        assertEquals("00:00", ReminderLimits.format(0))
        assertEquals("23:59", ReminderLimits.format(1439))
    }

    @Test
    fun `the next reminder is always in the future for every minute of a week`() {
        var now = monday.atStartOfDay()
        repeat(7 * 24 * 4) { // every 15 minutes for a week
            val t = Schedule.nextInterval(now, 9 * 60, 19 * 60, 60, weekdaysOnly = true)
            assertTrue("$now -> $t", t.isAfter(now))
            assertTrue("$t is on a weekday", t.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
            now = now.plusMinutes(15)
        }
    }
}
