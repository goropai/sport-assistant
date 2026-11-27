package com.sportassistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReportTest {
    private val today = 20_000L

    private fun log(day: Long, seconds: Int = 600, points: Int = 50) =
        WorkoutLog(epochDay = day, exercises = 5, seconds = seconds, points = points)

    private fun build(
        logs: List<WorkoutLog> = emptyList(),
        health: List<DayHealth>? = null,
        eaten: Map<Long, Int> = emptyMap(),
    ) = WeeklyReport.build(today, logs, health, eaten)

    @Test
    fun `counts distinct active days and total minutes`() {
        val s = build(listOf(log(today), log(today), log(today - 2, 1200), log(today - 5, 300)))
        assertEquals(3, s.activeDays)
        assertEquals(4, s.workouts)
        assertEquals((600 + 600 + 1200 + 300) / 60, s.activeMinutes)
    }

    @Test
    fun `ignores workouts outside the seven day window`() {
        val s = build(listOf(log(today - 7), log(today - 30)))
        assertEquals(0, s.activeDays)
        assertEquals(0, s.workouts)
    }

    @Test
    fun `previous week workouts are counted separately`() {
        val s = build(listOf(log(today), log(today - 7), log(today - 9), log(today - 13), log(today - 14)))
        assertEquals(1, s.workouts)
        assertEquals(3, s.workoutsPrevWeek)
    }

    @Test
    fun `headline follows the number of active days`() {
        assertEquals(Headline.RESTART, build().headline)
        assertEquals(Headline.START, build(listOf(log(today))).headline)
        assertEquals(Headline.GOOD, build((0L..2L).map { log(today - it) }).headline)
        assertEquals(Headline.GREAT, build((0L..3L).map { log(today - it) }).headline)
    }

    @Test
    fun `steps are aligned to the window and average skips partial today`() {
        val health = listOf(
            DayHealth(today - 2, steps = 8_000, activeKcal = null, sleepMinutes = null),
            DayHealth(today - 1, steps = 6_000, activeKcal = null, sleepMinutes = null),
            DayHealth(today, steps = 500, activeKcal = null, sleepMinutes = null),
        )
        val s = build(health = health)
        assertEquals(7, s.stepsPerDay.size)
        assertEquals(500L, s.stepsPerDay.last())
        assertEquals(6_000L, s.stepsPerDay[5])
        assertNull(s.stepsPerDay.first())
        assertEquals(7_000, s.avgSteps) // (8000 + 6000) / 2, today excluded
    }

    @Test
    fun `today is used when it is the only day with data`() {
        val s = build(health = listOf(DayHealth(today, 3_000, null, null)))
        assertEquals(3_000, s.avgSteps)
    }

    @Test
    fun `no watch data means no step or sleep figures`() {
        val s = build(health = null)
        assertNull(s.avgSteps)
        assertNull(s.avgSleepMinutes)
        assertTrue(s.stepsPerDay.all { it == null })
    }

    @Test
    fun `zero step days count as missing data`() {
        val s = build(health = listOf(DayHealth(today - 1, 0, null, null)))
        assertNull(s.avgSteps)
    }

    @Test
    fun `average eaten calories use days with entries only`() {
        val s = build(eaten = mapOf(today - 1 to 2_000, today - 2 to 1_800, today to 400))
        assertEquals(1_900, s.avgEatenKcal)
    }

    @Test
    fun `low steps, low sleep and few active minutes each produce a tip`() {
        val health = listOf(DayHealth(today - 1, 3_000, null, 5 * 60))
        val tips = build(listOf(log(today - 1, seconds = 600)), health).tips
        assertEquals(listOf(Tip.MORE_STEPS, Tip.MORE_SLEEP, Tip.MORE_ACTIVITY), tips)
    }

    @Test
    fun `a good week gets no tips`() {
        val logs = (0L..4L).map { log(today - it, seconds = 2_000) } // 5 * 33 min
        val health = listOf(DayHealth(today - 1, 9_000, null, 7 * 60 + 30))
        assertTrue(build(logs, health).tips.isEmpty())
    }

    @Test
    fun `the activity goal is met exactly at 150 minutes`() {
        val s = build(listOf(log(today - 1, seconds = 150 * 60)))
        assertTrue(Tip.MORE_ACTIVITY !in s.tips)
    }
}
