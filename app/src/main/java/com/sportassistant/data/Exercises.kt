package com.sportassistant.data

import androidx.annotation.StringRes
import com.sportassistant.R

enum class Category(@StringRes val label: Int) {
    MOBILITY(R.string.cat_mobility),
    STRETCH(R.string.cat_stretch),
    STRENGTH(R.string.cat_strength),
    CORE(R.string.cat_core),
    CARDIO(R.string.cat_cardio),
    EYES(R.string.cat_eyes),
}

enum class Complaint(@StringRes val label: Int) {
    NECK(R.string.complaint_neck),
    BACK(R.string.complaint_back),
    SHOULDERS(R.string.complaint_shoulders),
    EYES(R.string.complaint_eyes),
    LEGS(R.string.complaint_legs),
}

/** Either [seconds] (timed) or [reps] (counted) is set. */
data class Exercise(
    val id: String,
    val emoji: String,
    @StringRes val name: Int,
    @StringRes val steps: Int,
    val category: Category,
    val seconds: Int = 0,
    val reps: Int = 0,
    val targets: Set<Complaint> = emptySet(),
) {
    /** Rough duration used for estimates and logging. */
    val estimatedSeconds: Int get() = if (seconds > 0) seconds else reps * 4
}

object Exercises {
    val all: List<Exercise> = listOf(
        Exercise("neck_tilts", "🙆", R.string.ex_neck_tilts, R.string.ex_neck_tilts_steps, Category.MOBILITY, seconds = 30, targets = setOf(Complaint.NECK)),
        Exercise("shoulder_rolls", "🔄", R.string.ex_shoulder_rolls, R.string.ex_shoulder_rolls_steps, Category.MOBILITY, seconds = 30, targets = setOf(Complaint.SHOULDERS, Complaint.NECK)),
        Exercise("cat_cow", "🐈", R.string.ex_cat_cow, R.string.ex_cat_cow_steps, Category.MOBILITY, seconds = 40, targets = setOf(Complaint.BACK)),
        Exercise("chest_opener", "🚪", R.string.ex_chest_opener, R.string.ex_chest_opener_steps, Category.STRETCH, seconds = 30, targets = setOf(Complaint.SHOULDERS, Complaint.BACK)),
        Exercise("hip_flexor", "🧎", R.string.ex_hip_flexor, R.string.ex_hip_flexor_steps, Category.STRETCH, seconds = 40, targets = setOf(Complaint.BACK, Complaint.LEGS)),
        Exercise("wall_pushups", "🧱", R.string.ex_wall_pushups, R.string.ex_wall_pushups_steps, Category.STRENGTH, reps = 10, targets = setOf(Complaint.SHOULDERS)),
        Exercise("pushups", "💪", R.string.ex_pushups, R.string.ex_pushups_steps, Category.STRENGTH, reps = 8, targets = setOf(Complaint.SHOULDERS)),
        Exercise("squats","🪑", R.string.ex_squats, R.string.ex_squats_steps, Category.STRENGTH, reps = 12, targets = setOf(Complaint.LEGS)),
        Exercise("glute_bridge", "🌉", R.string.ex_glute_bridge, R.string.ex_glute_bridge_steps, Category.STRENGTH, reps = 12, targets = setOf(Complaint.BACK, Complaint.LEGS)),
        Exercise("calf_raises", "🦶", R.string.ex_calf_raises, R.string.ex_calf_raises_steps, Category.STRENGTH, reps = 15, targets = setOf(Complaint.LEGS)),
        Exercise("plank", "📏", R.string.ex_plank, R.string.ex_plank_steps, Category.CORE, seconds = 20, targets = setOf(Complaint.BACK)),
        Exercise("bird_dog", "🐕", R.string.ex_bird_dog, R.string.ex_bird_dog_steps, Category.CORE, reps = 10, targets = setOf(Complaint.BACK)),
        Exercise("march", "🥁", R.string.ex_march, R.string.ex_march_steps, Category.CARDIO, seconds = 180, targets = setOf(Complaint.LEGS)),
        Exercise("brisk_walk", "🚶", R.string.ex_brisk_walk, R.string.ex_brisk_walk_steps, Category.CARDIO, seconds = 600, targets = setOf(Complaint.LEGS)),
        Exercise("eye_rest", "👀", R.string.ex_eye_rest, R.string.ex_eye_rest_steps, Category.EYES, seconds = 20, targets = setOf(Complaint.EYES)),
    )

    fun byId(id: String): Exercise? = all.firstOrNull { it.id == id }
}

object PlanGenerator {
    /**
     * Deterministic daily plan: exercises matching the user's complaints come first within each
     * category, and the pool rotates with the day so the routine does not get stale.
     */
    fun forDay(epochDay: Long, complaints: Set<Complaint>, level: PlanLevel = PlanLevel.NORMAL): List<Exercise> {
        fun pick(category: Category, n: Int): List<Exercise> {
            val pool = Exercises.all.filter { it.category == category }
            if (pool.isEmpty()) return emptyList()
            val shift = (epochDay % pool.size).toInt()
            val rotated = pool.drop(shift) + pool.take(shift)
            val (relevant, other) = rotated.partition { ex -> ex.targets.any { it in complaints } }
            return (relevant + other).take(n)
        }
        val strengthCount = when (level) {
            PlanLevel.EASIER -> 1
            PlanLevel.NORMAL -> 2
            PlanLevel.HARDER -> 3
        }
        return buildList {
            if (Complaint.EYES in complaints) addAll(pick(Category.EYES, 1))
            addAll(pick(Category.MOBILITY, 2))
            addAll(pick(Category.STRENGTH, strengthCount))
            addAll(pick(Category.CORE, 1))
            if (epochDay % 2 == 0L && level != PlanLevel.EASIER) addAll(pick(Category.CARDIO, 1).filter { it.seconds <= 180 })
            addAll(pick(Category.STRETCH, 1))
        }.map { PlanAdapter.scale(it, level) }
    }
}
