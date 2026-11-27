package com.sportassistant.data

import androidx.annotation.StringRes
import com.sportassistant.R
import kotlin.math.roundToInt

enum class PlanLevel(@StringRes val note: Int?) {
    EASIER(R.string.plan_note_easier),
    NORMAL(null),
    HARDER(R.string.plan_note_harder),
}

/**
 * Adjusts the daily plan to how the user has actually been training, so it stays achievable after a
 * break and keeps progressing when they are consistent. Based on the days *before* today, so the plan
 * does not change under the user's feet after they finish today's workout.
 */
object PlanAdapter {
    private const val CONSISTENT_DAYS = 4

    fun levelFor(activeDaysPrev6: Int, hadWorkoutBeforeToday: Boolean, activity: Int): PlanLevel = when {
        // First days: start gently unless the person already reports being active.
        !hadWorkoutBeforeToday -> if (activity == 0) PlanLevel.EASIER else PlanLevel.NORMAL
        // Coming back after a quiet week.
        activeDaysPrev6 == 0 -> PlanLevel.EASIER
        activeDaysPrev6 >= CONSISTENT_DAYS -> PlanLevel.HARDER
        else -> PlanLevel.NORMAL
    }

    /** Scales the dose (reps or seconds) of one exercise for the level. */
    fun scale(ex: Exercise, level: PlanLevel): Exercise = when (level) {
        PlanLevel.NORMAL -> ex
        PlanLevel.EASIER -> ex.copy(
            reps = if (ex.reps > 0) (ex.reps * 0.7).roundToInt().coerceAtLeast(5) else 0,
            seconds = if (ex.seconds > 0) (ex.seconds * 0.8).roundToInt().coerceAtLeast(15) else 0,
        )
        PlanLevel.HARDER -> ex.copy(
            reps = if (ex.reps > 0) (ex.reps * 1.25).roundToInt() else 0,
            seconds = if (ex.seconds > 0) (ex.seconds * 1.2).roundToInt() else 0,
        )
    }
}
