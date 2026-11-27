package com.sportassistant.data

import androidx.annotation.StringRes
import com.sportassistant.R
import kotlin.math.roundToInt

/** One day of data read from the watch via Health Connect; null where nothing was recorded. */
data class DayHealth(val epochDay: Long, val steps: Long?, val activeKcal: Int?, val sleepMinutes: Int?)

enum class Headline(@StringRes val text: Int) {
    GREAT(R.string.report_headline_great),
    GOOD(R.string.report_headline_good),
    START(R.string.report_headline_start),
    RESTART(R.string.report_headline_restart),
}

enum class Tip(@StringRes val text: Int) {
    MORE_STEPS(R.string.report_tip_steps),
    MORE_SLEEP(R.string.report_tip_sleep),
    MORE_ACTIVITY(R.string.report_tip_activity),
}

data class WeekSummary(
    val activeDays: Int,
    val activeMinutes: Int,
    val workouts: Int,
    val workoutsPrevWeek: Int,
    /** Oldest first, one entry per day of the 7-day window; null when the watch has no data for that day. */
    val stepsPerDay: List<Long?>,
    val avgSteps: Int?,
    val avgSleepMinutes: Int?,
    val avgEatenKcal: Int?,
    val headline: Headline,
    val tips: List<Tip>,
)

/** Builds the "this week" summary from the local log, the watch data and the food diary. */
object WeeklyReport {
    /** WHO: at least 150 minutes of moderate activity per week. */
    const val ACTIVE_MINUTES_GOAL = 150
    private const val LOW_STEPS = 5_000
    private const val LOW_SLEEP_MINUTES = 6.5 * 60

    fun build(
        today: Long,
        logs: List<WorkoutLog>,
        health: List<DayHealth>?,
        eatenKcalByDay: Map<Long, Int>,
    ): WeekSummary {
        val window = (today - 6)..today
        val previous = (today - 13)..(today - 7)
        val thisWeek = logs.filter { it.epochDay in window }

        val activeDays = thisWeek.map { it.epochDay }.toSet().size
        val activeMinutes = thisWeek.sumOf { it.seconds } / 60
        val byDay = health.orEmpty().associateBy { it.epochDay }
        val stepsPerDay = window.map { byDay[it]?.steps?.takeIf { s -> s > 0 } }

        val avgSteps = averageOf(window.mapNotNull { day -> stepsPerDay[(day - window.first).toInt()]?.let { day to it.toDouble() } }, today)
        val avgSleep = averageOf(window.mapNotNull { d -> byDay[d]?.sleepMinutes?.takeIf { it > 0 }?.let { d to it.toDouble() } }, today)
        val avgEaten = averageOf(window.mapNotNull { d -> eatenKcalByDay[d]?.takeIf { it > 0 }?.let { d to it.toDouble() } }, today)

        val headline = when {
            activeDays >= 4 -> Headline.GREAT
            activeDays == 3 -> Headline.GOOD
            activeDays >= 1 -> Headline.START
            else -> Headline.RESTART
        }
        val tips = buildList {
            if (avgSteps != null && avgSteps < LOW_STEPS) add(Tip.MORE_STEPS)
            if (avgSleep != null && avgSleep < LOW_SLEEP_MINUTES) add(Tip.MORE_SLEEP)
            if (activeMinutes < ACTIVE_MINUTES_GOAL) add(Tip.MORE_ACTIVITY)
        }

        return WeekSummary(
            activeDays = activeDays,
            activeMinutes = activeMinutes,
            workouts = thisWeek.size,
            workoutsPrevWeek = logs.count { it.epochDay in previous },
            stepsPerDay = stepsPerDay,
            avgSteps = avgSteps?.roundToInt(),
            avgSleepMinutes = avgSleep?.roundToInt(),
            avgEatenKcal = avgEaten?.roundToInt(),
            headline = headline,
            tips = tips,
        )
    }

    /**
     * Average over the given (day, value) pairs. Today is still in progress, so its partial value is
     * left out whenever at least one complete day exists.
     */
    private fun averageOf(values: List<Pair<Long, Double>>, today: Long): Double? {
        if (values.isEmpty()) return null
        val complete = values.filter { it.first < today }
        return (complete.ifEmpty { values }).map { it.second }.average()
    }
}
