package com.sportassistant.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchRepCounterTest {
    @Test
    fun `the first touch counts`() {
        val c = TouchRepCounter()
        assertTrue(c.onTouch(1_000))
        assertEquals(1, c.reps)
    }

    @Test
    fun `a burst of events from one contact counts once`() {
        val c = TouchRepCounter(minIntervalMs = 700)
        assertTrue(c.onTouch(1_000))
        assertFalse(c.onTouch(1_050))
        assertFalse(c.onTouch(1_300))
        assertFalse(c.onTouch(1_699))
        assertEquals(1, c.reps)
    }

    @Test
    fun `a touch exactly one interval later counts`() {
        val c = TouchRepCounter(minIntervalMs = 700)
        c.onTouch(1_000)
        assertTrue(c.onTouch(1_700))
        assertEquals(2, c.reps)
    }

    @Test
    fun `normally paced push-ups are all counted`() {
        val c = TouchRepCounter()
        // One push-up every ~2 seconds.
        (0 until 10).forEach { assertTrue(c.onTouch(it * 2_000L)) }
        assertEquals(10, c.reps)
    }

    @Test
    fun `an ignored touch does not postpone the next valid one`() {
        val c = TouchRepCounter(minIntervalMs = 700)
        c.onTouch(0)
        c.onTouch(600) // ignored
        // 700 ms after the counted touch, not after the ignored one.
        assertTrue(c.onTouch(700))
        assertEquals(2, c.reps)
    }

    @Test
    fun `reset starts over`() {
        val c = TouchRepCounter()
        c.onTouch(0)
        c.onTouch(2_000)
        c.reset()
        assertEquals(0, c.reps)
        assertTrue("the first touch after a reset counts immediately", c.onTouch(2_100))
        assertEquals(1, c.reps)
    }
}
