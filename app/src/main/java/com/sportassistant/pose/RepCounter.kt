package com.sportassistant.pose

import kotlin.math.atan2
import kotlin.math.abs

/**
 * Counts repetitions from a stream of joint angles (degrees).
 *
 * A rep is a full cycle: REST -> ACTIVE -> REST. "Rest" and "active" are angle thresholds with
 * hysteresis, so jitter around a single threshold never produces phantom reps. Works for both
 * movements where the active phase has a small angle (squat, push-up: [active] < [rest]) and a
 * large one (glute bridge: [active] > [rest]).
 */
class RepCounter(
    private val rest: Float,
    private val active: Float,
    private val smoothing: Float = 0.5f,
    private val minRepMs: Long = 500,
) {
    private enum class Phase { WAITING_FOR_REST, REST, ACTIVE }

    private var phase = Phase.WAITING_FOR_REST
    private var smoothed: Float? = null
    private var lastRepAt = -1_000_000L

    var reps = 0
        private set

    /** True while the user is in the working position (between leaving rest and returning to it). */
    val isActive: Boolean get() = phase == Phase.ACTIVE

    /** Returns true when this sample completed a repetition. */
    fun update(angle: Float, nowMs: Long): Boolean {
        val s = smoothed?.let { it + smoothing * (angle - it) } ?: angle
        smoothed = s
        when (phase) {
            Phase.WAITING_FOR_REST -> if (isRest(s)) phase = Phase.REST
            Phase.REST -> if (isActive(s)) phase = Phase.ACTIVE
            Phase.ACTIVE -> if (isRest(s)) {
                phase = Phase.REST
                if (nowMs - lastRepAt >= minRepMs) {
                    reps++
                    lastRepAt = nowMs
                    return true
                }
            }
        }
        return false
    }

    fun reset() {
        phase = Phase.WAITING_FOR_REST
        smoothed = null
        reps = 0
    }

    private fun isRest(a: Float) = if (rest > active) a >= rest else a <= rest
    private fun isActive(a: Float) = if (rest > active) a <= active else a >= active
}

/** Angle at [b] formed by points a-b-c, in degrees (0..180). */
fun jointAngle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
    val radians = atan2(cy - by, cx - bx) - atan2(ay - by, ax - bx)
    val degrees = abs(Math.toDegrees(radians.toDouble())).toFloat()
    return if (degrees > 180f) 360f - degrees else degrees
}
