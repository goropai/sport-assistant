package com.sportassistant.data

import androidx.annotation.StringRes
import com.sportassistant.R
import java.time.LocalTime

enum class Meal(@StringRes val label: Int) {
    BREAKFAST(R.string.meal_breakfast),
    LUNCH(R.string.meal_lunch),
    DINNER(R.string.meal_dinner),
    SNACK(R.string.meal_snack);

    companion object {
        /** Stored names that are unknown (older data, hand-edited backups) become snacks. */
        fun of(name: String): Meal = entries.firstOrNull { it.name == name } ?: SNACK
    }
}

/**
 * Chooses which meal a new entry most likely belongs to: the main meal expected at this hour if it has
 * not been logged yet today, otherwise a snack. Outside the main meal windows it is always a snack.
 */
object MealPicker {
    private val BREAKFAST_HOURS = 5..10
    private val LUNCH_HOURS = 11..15
    private val DINNER_HOURS = 17..22

    fun defaultFor(now: LocalTime, loggedToday: Set<Meal>): Meal {
        val expected = when (now.hour) {
            in BREAKFAST_HOURS -> Meal.BREAKFAST
            in LUNCH_HOURS -> Meal.LUNCH
            in DINNER_HOURS -> Meal.DINNER
            else -> null
        }
        return if (expected != null && expected !in loggedToday) expected else Meal.SNACK
    }
}
