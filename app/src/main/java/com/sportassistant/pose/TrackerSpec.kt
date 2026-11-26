package com.sportassistant.pose

enum class Joint { KNEE, ELBOW, HIP }

/**
 * How to count one exercise: which joint angle to follow and the rest/active thresholds.
 * Thresholds are deliberately forgiving (aimed at general fitness, not strict form judging) and
 * should be tuned on a real device.
 */
data class TrackerSpec(val joint: Joint, val rest: Float, val active: Float)

object RepTrackers {
    private val specs = mapOf(
        "squats" to TrackerSpec(Joint.KNEE, rest = 160f, active = 110f),
        "pushups" to TrackerSpec(Joint.ELBOW, rest = 150f, active = 100f),
        "wall_pushups" to TrackerSpec(Joint.ELBOW, rest = 150f, active = 115f),
        // Lying hip angle (shoulder-hip-knee) is ~120-140 deg, at the top of the bridge ~165+.
        "glute_bridge" to TrackerSpec(Joint.HIP, rest = 145f, active = 160f),
    )

    fun forExercise(id: String): TrackerSpec? = specs[id]
}
