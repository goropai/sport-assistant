package com.sportassistant.data

import androidx.annotation.StringRes
import com.sportassistant.R

enum class Sex(@StringRes val label: Int) {
    UNSPECIFIED(R.string.sex_unspecified),
    MALE(R.string.sex_male),
    FEMALE(R.string.sex_female),
}

object Energy {
    /** Resting energy expenditure, kcal/day (Mifflin-St Jeor). Unspecified sex uses the midpoint of both constants. */
    fun bmr(sex: Sex, weightKg: Int, heightCm: Int, age: Int): Int {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        val constant = when (sex) {
            Sex.MALE -> 5.0
            Sex.FEMALE -> -161.0
            Sex.UNSPECIFIED -> -78.0
        }
        return (base + constant).toInt().coerceAtLeast(0)
    }

    /** The part of the daily resting expenditure burned by [secondsOfDay] (0..86400). */
    fun restingSoFar(bmr: Int, secondsOfDay: Int): Int =
        (bmr * secondsOfDay.coerceIn(0, 86_400) / 86_400.0).toInt()
}
