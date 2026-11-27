package com.sportassistant.data

import com.sportassistant.ai.FoodItem
import java.io.Serializable

/**
 * One "food" section of the add-food form (name, grams, kcal). Immutable and [Serializable], so a list
 * of them survives rotation and process death, and the calculation rules can be unit-tested.
 */
data class ManualRow(
    /** Stable identity: lets an asynchronous AI lookup find its row even after other rows are removed. */
    val id: Long,
    val name: String = "",
    val grams: String = "",
    /** The user's own calories; null means "calculate from the weight". */
    val kcalOverride: String? = null,
    /** Nutrition per 100 g, from the offline table, the AI, or a previously logged entry. */
    val kcal100: Double? = null,
    val protein100: Double? = null,
    val fat100: Double? = null,
    val carbs100: Double? = null,
) : Serializable {
    val per100: Per100?
        get() = kcal100?.let { Per100(it, protein100 ?: 0.0, fat100 ?: 0.0, carbs100 ?: 0.0) }

    val gramsInt: Int get() = grams.toIntOrNull() ?: 0

    /** Calories calculated from the weight, or null when there is no per-100 g data or no weight yet. */
    val autoKcal: Int? get() = per100?.takeIf { gramsInt > 0 }?.kcalFor(gramsInt)

    /** What the kcal field shows: the user's number if they typed one, otherwise the calculation. */
    val kcalText: String get() = kcalOverride ?: autoKcal?.toString().orEmpty()

    val kcal: Int get() = kcalText.toIntOrNull() ?: 0

    /** Only a named food with calories can be saved; an untouched or half-filled section is skipped. */
    val isValid: Boolean get() = name.isNotBlank() && kcal > 0

    fun withPer100(p: Per100?): ManualRow =
        copy(kcal100 = p?.kcal, protein100 = p?.proteinG, fat100 = p?.fatG, carbs100 = p?.carbsG)

    /** Macros are only known when the calories came from the table, not from a hand-typed number. */
    fun toItem(): FoodItem? {
        if (!isValid) return null
        val fromTable = kcalOverride == null && gramsInt > 0
        val p = per100
        return FoodItem(
            name = name.trim(),
            grams = gramsInt,
            kcal = kcal,
            proteinG = if (fromTable) p?.proteinFor(gramsInt) ?: 0 else 0,
            fatG = if (fromTable) p?.fatFor(gramsInt) ?: 0 else 0,
            carbsG = if (fromTable) p?.carbsFor(gramsInt) ?: 0 else 0,
        )
    }
}

object ManualRows {
    /** The foods to save from the whole form: every valid section, in order. */
    fun items(rows: List<ManualRow>): List<FoodItem> = rows.mapNotNull { it.toItem() }

    fun validCount(rows: List<ManualRow>): Int = rows.count { it.isValid }
}
