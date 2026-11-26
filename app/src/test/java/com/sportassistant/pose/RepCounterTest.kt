package com.sportassistant.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepCounterTest {
    /** Feeds [angles] at 100 ms spacing, returns the final rep count. */
    private fun run(counter: RepCounter, angles: List<Float>, startMs: Long = 0): Int {
        angles.forEachIndexed { i, a -> counter.update(a, startMs + i * 100L) }
        return counter.reps
    }

    /** Linear ramp from [from] to [to] in [steps] samples (excluding the start value). */
    private fun ramp(from: Float, to: Float, steps: Int = 8) =
        (1..steps).map { from + (to - from) * it / steps }

    private fun squat() = RepCounter(rest = 160f, active = 110f)

    @Test
    fun `full squat cycle counts one rep`() {
        val c = squat()
        val angles = listOf(170f) + ramp(170f, 90f) + ramp(90f, 170f)
        assertEquals(1, run(c, angles))
    }

    @Test
    fun `shallow squat that never reaches active threshold is not counted`() {
        val c = squat()
        val angles = listOf(170f) + ramp(170f, 130f) + ramp(130f, 170f)
        assertEquals(0, run(c, angles))
    }

    @Test
    fun `three cycles count three reps`() {
        val c = squat()
        val cycle = ramp(170f, 90f) + ramp(90f, 170f)
        assertEquals(3, run(c, listOf(170f) + cycle + cycle + cycle))
    }

    @Test
    fun `jitter around the rest threshold does not create reps`() {
        val c = squat()
        val noisy = List(40) { if (it % 2 == 0) 158f else 163f }
        assertEquals(0, run(c, noisy))
    }

    @Test
    fun `starting already in the bottom position waits for rest before counting`() {
        val c = squat()
        // Begins deep, stands up (no rep yet), then does one real squat.
        val angles = listOf(95f, 95f) + ramp(95f, 170f) + ramp(170f, 90f) + ramp(90f, 170f)
        assertEquals(1, run(c, angles))
    }

    @Test
    fun `rep faster than minimum interval is ignored`() {
        val c = RepCounter(rest = 160f, active = 110f, smoothing = 1f, minRepMs = 500)
        c.update(170f, 0)
        c.update(90f, 10)
        assertTrue(c.update(170f, 600))
        c.update(90f, 610)
        assertFalse(c.update(170f, 700))
        assertEquals(1, c.reps)
    }

    @Test
    fun `glute bridge style counts when active angle is larger than rest`() {
        val c = RepCounter(rest = 145f, active = 160f)
        val cycle = ramp(130f, 175f) + ramp(175f, 130f)
        assertEquals(2, run(c, listOf(130f) + cycle + cycle))
    }

    @Test
    fun `reset clears reps`() {
        val c = squat()
        run(c, listOf(170f) + ramp(170f, 90f) + ramp(90f, 170f))
        c.reset()
        assertEquals(0, c.reps)
    }

    @Test
    fun `joint angle of a right angle is 90`() {
        assertEquals(90f, jointAngle(0f, 1f, 0f, 0f, 1f, 0f), 0.01f)
    }

    @Test
    fun `joint angle of a straight line is 180`() {
        assertEquals(180f, jointAngle(-1f, 0f, 0f, 0f, 1f, 0f), 0.01f)
    }
}
