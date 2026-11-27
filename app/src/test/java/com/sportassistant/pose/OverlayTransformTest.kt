package com.sportassistant.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTransformTest {
    private fun map(nx: Float, ny: Float, iw: Float, ih: Float, vw: Float, vh: Float, mirror: Boolean = false) =
        OverlayTransform.toView(nx, ny, iw, ih, vw, vh, mirror)

    @Test
    fun `image centre maps to view centre whatever the aspect ratios`() {
        val (x, y) = map(0.5f, 0.5f, 480f, 640f, 1080f, 2400f)
        assertEquals(540f, x, 0.01f)
        assertEquals(1200f, y, 0.01f)
    }

    @Test
    fun `same aspect ratio scales linearly`() {
        val (x, y) = map(0.25f, 0.75f, 480f, 640f, 960f, 1280f)
        assertEquals(240f, x, 0.01f)
        assertEquals(960f, y, 0.01f)
    }

    @Test
    fun `fill-center crops the overflowing axis`() {
        // 3:4 image shown in a much taller view: height fills, width overflows and is cropped.
        val (left, _) = map(0f, 0.5f, 480f, 640f, 1080f, 2400f)
        val (right, _) = map(1f, 0.5f, 480f, 640f, 1080f, 2400f)
        assertTrue("left edge is outside the view", left < 0f)
        assertTrue("right edge is outside the view", right > 1080f)
        assertEquals(1080f, (left + right) / 2f * 2f - 0f, 1080f) // symmetric about the centre
        assertEquals(540f, (left + right) / 2f, 0.01f)
    }

    @Test
    fun `mirroring flips x only`() {
        val (x, y) = map(0.2f, 0.4f, 480f, 640f, 960f, 1280f, mirror = false)
        val (mx, my) = map(0.2f, 0.4f, 480f, 640f, 960f, 1280f, mirror = true)
        assertEquals(960f - x, mx, 0.01f)
        assertEquals(y, my, 0.01f)
    }

    @Test
    fun `mirrored centre stays at the centre`() {
        assertEquals(540f, map(0.5f, 0.5f, 480f, 640f, 1080f, 2400f, mirror = true).first, 0.01f)
    }

    @Test
    fun `strict is deeper and easy is shallower than normal for a squat`() {
        val squat = TrackerSpec(Joint.KNEE, rest = 160f, active = 110f)
        val strict = squat.withSensitivity(Sensitivity.STRICT).active
        val normal = squat.withSensitivity(Sensitivity.NORMAL).active
        val easy = squat.withSensitivity(Sensitivity.EASY).active
        assertEquals(110f, normal, 0.01f)
        assertTrue(strict < normal)
        assertTrue(easy > normal)
        assertTrue("working position must stay clearly away from rest", easy < squat.rest - 20f)
    }

    @Test
    fun `sensitivity also works when the working angle is larger than rest`() {
        val bridge = TrackerSpec(Joint.HIP, rest = 145f, active = 160f)
        val easy = bridge.withSensitivity(Sensitivity.EASY).active
        val strict = bridge.withSensitivity(Sensitivity.STRICT).active
        assertTrue("easy moves the threshold toward rest", easy < bridge.active && easy > bridge.rest)
        assertTrue("strict moves it away from rest", strict > bridge.active)
    }

    @Test
    fun `easier sensitivity counts a shallow squat that strict ignores`() {
        val squat = TrackerSpec(Joint.KNEE, rest = 160f, active = 110f)
        fun repsFor(level: Int): Int {
            val s = squat.withSensitivity(level)
            val c = RepCounter(s.rest, s.active, smoothing = 1f)
            // Down to only 120 degrees: a shallow squat. Thresholds: strict 102.5, normal 110, easy 122.5.
            listOf(170f, 150f, 120f, 150f, 170f).forEachIndexed { i, a -> c.update(a, i * 700L) }
            return c.reps
        }
        assertEquals(1, repsFor(Sensitivity.EASY))
        assertEquals(0, repsFor(Sensitivity.NORMAL))
        assertEquals(0, repsFor(Sensitivity.STRICT))
    }

    @Test
    fun `counter reports when it is in the working position`() {
        val c = RepCounter(rest = 160f, active = 110f, smoothing = 1f)
        c.update(170f, 0)
        assertFalse(c.isActive)
        c.update(100f, 700)
        assertTrue(c.isActive)
        c.update(170f, 1400)
        assertFalse(c.isActive)
    }
}
