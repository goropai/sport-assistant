package com.sportassistant.reminders

import java.time.DayOfWeek
import java.time.LocalDateTime

/** Bounds for the settings steppers, kept pure so the rules are tested. All values are minutes after midnight. */
object ReminderLimits {
    private const val HOUR = 60
    private const val LAST_END = 23 * HOUR

    /** Moves the start of the active window; it stays at least an hour before the end. */
    fun shiftStart(start: Int, end: Int, deltaMin: Int): Int = (start + deltaMin).coerceIn(0, (end - HOUR).coerceAtLeast(0))

    /** Moves the end of the active window; it stays at least an hour after the start and no later than 23:00. */
    fun shiftEnd(start: Int, end: Int, deltaMin: Int): Int = (end + deltaMin).coerceIn((start + HOUR).coerceAtMost(LAST_END), LAST_END)

    /** Moves a time of day, wrapping around midnight (23:30 + 30 min = 00:00). */
    fun shiftTime(value: Int, deltaMin: Int): Int = Math.floorMod(value + deltaMin, 24 * HOUR)

    fun format(minutes: Int): String = "%02d:%02d".format(minutes / HOUR, minutes % HOUR)
}

/**
 * When the next reminder should fire. Pure functions so the tricky parts (day boundaries, weekends,
 * "strictly after now") are covered by unit tests instead of being discovered on a phone.
 *
 * Times of day are minutes after midnight (0..1439).
 */
object Schedule {
    /** WorkManager cannot reliably deliver more often than this, and it would only be annoying. */
    const val MIN_INTERVAL_MIN = 15

    /** A reminder that runs this much later than planned (device asleep) is stale and gets skipped. */
    const val STALE_AFTER_MS = 20 * 60_000L

    /**
     * Reminders on a fixed grid inside the active window: start+offset, then every [everyMin] minutes up
     * to [endMin] (inclusive), on every day, or on weekdays only. Always returns a time strictly after
     * [now], so asking again right after a reminder fires gives the following one.
     */
    fun nextInterval(
        now: LocalDateTime,
        startMin: Int,
        endMin: Int,
        everyMin: Int,
        weekdaysOnly: Boolean,
        offsetMin: Int = 0,
    ): LocalDateTime {
        val every = everyMin.coerceAtLeast(MIN_INTERVAL_MIN)
        val start = startMin.coerceIn(0, 1439)
        val end = endMin.coerceIn(start, 1439)
        val slots = buildList {
            var m = start + offsetMin
            while (m <= end) {
                add(m)
                m += every
            }
        }.ifEmpty { listOf(start) } // an offset that overshoots a short window must not silence the reminder

        for (day in 0L..9L) {
            val date = now.toLocalDate().plusDays(day)
            if (weekdaysOnly && date.dayOfWeek.isWeekend()) continue
            for (m in slots) {
                val t = date.atTime(m / 60, m % 60)
                if (t.isAfter(now)) return t
            }
        }
        // Unreachable with the loop above (a weekday is at most 2 days away); kept as a safe fallback.
        return now.toLocalDate().plusDays(1).atTime(start / 60, start % 60)
    }

    /** Reminders at fixed times every day (for example brushing teeth). Null if there are no times. */
    fun nextFixed(now: LocalDateTime, timesMin: List<Int>): LocalDateTime? {
        val times = timesMin.map { it.coerceIn(0, 1439) }.distinct().sorted()
        if (times.isEmpty()) return null
        for (day in 0L..1L) {
            val date = now.toLocalDate().plusDays(day)
            for (m in times) {
                val t = date.atTime(m / 60, m % 60)
                if (t.isAfter(now)) return t
            }
        }
        return null
    }

    fun isStale(scheduledAtMs: Long, nowMs: Long): Boolean = nowMs - scheduledAtMs > STALE_AFTER_MS

    private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY
}
