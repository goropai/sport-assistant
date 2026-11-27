package com.sportassistant.data

import kotlin.math.roundToInt

/** Pure helpers over the food diary: quick-add suggestions and weight edits. */
object FoodHistory {
    private const val DEFAULT_LIMIT = 8

    /**
     * The foods the user eats most, for one-tap adding. Entries are grouped by name (case and surrounding
     * whitespace ignored); the most frequent come first, ties go to the one eaten most recently, and each
     * is represented by its latest entry so the last-used weight is offered.
     */
    fun frequent(entries: List<FoodEntry>, limit: Int = DEFAULT_LIMIT): List<FoodEntry> =
        entries
            .filter { it.name.isNotBlank() }
            .groupBy { it.name.trim().lowercase() }
            .values
            .map { group -> group.size to group.maxBy { it.loggedAt } }
            .sortedWith(compareByDescending<Pair<Int, FoodEntry>> { it.first }.thenByDescending { it.second.loggedAt })
            .take(limit)
            .map { it.second }

    /**
     * Changes an entry's weight. When it had a weight, calories and macros scale in proportion; an entry
     * logged by calories alone has nothing to scale from, so only the weight changes.
     */
    fun withGrams(e: FoodEntry, grams: Int): FoodEntry {
        val g = grams.coerceAtLeast(0)
        if (e.grams <= 0 || g <= 0) return e.copy(grams = g)
        val f = g.toDouble() / e.grams
        return e.copy(
            grams = g,
            kcal = (e.kcal * f).roundToInt(),
            proteinG = (e.proteinG * f).roundToInt(),
            fatG = (e.fatG * f).roundToInt(),
            carbsG = (e.carbsG * f).roundToInt(),
        )
    }

    /** Nutrition per 100 g implied by an entry, or null if it has no weight to derive it from. */
    fun per100Of(e: FoodEntry): Per100? {
        if (e.grams <= 0) return null
        val k = 100.0 / e.grams
        return Per100(e.kcal * k, e.proteinG * k, e.fatG * k, e.carbsG * k)
    }
}
