package com.sportassistant.pose

/**
 * Counts one repetition per touch of the screen, e.g. a nose touch at the bottom of a push-up.
 *
 * Touches closer together than [minIntervalMs] are ignored: a single contact can produce several
 * events, and a chin or a second finger landing together with the nose must not count twice. A real
 * push-up takes longer than this, so legitimate repetitions are never swallowed.
 */
class TouchRepCounter(private val minIntervalMs: Long = 700) {
    var reps = 0
        private set

    private var lastCountedAt: Long? = null

    /** Returns true when this touch was counted as a repetition. */
    fun onTouch(nowMs: Long): Boolean {
        val last = lastCountedAt
        if (last != null && nowMs - last < minIntervalMs) return false
        lastCountedAt = nowMs
        reps++
        return true
    }

    fun reset() {
        reps = 0
        lastCountedAt = null
    }
}
