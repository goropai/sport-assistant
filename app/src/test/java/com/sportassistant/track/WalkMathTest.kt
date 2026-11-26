package com.sportassistant.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkMathTest {
    @Test
    fun `one degree of latitude is about 111 km`() {
        assertEquals(111_195.0, WalkMath.distanceM(50.0, 30.0, 51.0, 30.0), 200.0)
    }

    @Test
    fun `same point has zero distance`() {
        assertEquals(0.0, WalkMath.distanceM(50.45, 30.52, 50.45, 30.52), 1e-6)
    }

    @Test
    fun `inaccurate fix is rejected`() {
        assertFalse(WalkMath.accept(accuracyM = 80f, stepM = 10.0, dtSec = 5.0))
    }

    @Test
    fun `first accurate fix is accepted`() {
        assertTrue(WalkMath.accept(accuracyM = 10f, stepM = null, dtSec = null))
    }

    @Test
    fun `tiny drift while standing is ignored`() {
        assertFalse(WalkMath.accept(accuracyM = 5f, stepM = 2.0, dtSec = 2.0))
    }

    @Test
    fun `gps jump faster than humanly possible is ignored`() {
        assertFalse(WalkMath.accept(accuracyM = 5f, stepM = 200.0, dtSec = 2.0))
    }

    @Test
    fun `normal walking step is accepted`() {
        assertTrue(WalkMath.accept(accuracyM = 8f, stepM = 3.0 * 2.0 + 1, dtSec = 2.0))
    }

    @Test
    fun `one hour at 5 kmh for 80 kg is about 280 kcal`() {
        assertEquals(280, WalkMath.kcal(5000.0, 3600, 80))
    }

    @Test
    fun `zero duration burns nothing`() {
        assertEquals(0, WalkMath.kcal(0.0, 0, 80))
    }

    @Test
    fun `pace of 6 minutes per km`() {
        assertEquals(360, WalkMath.paceSecPerKm(1000.0, 360))
    }

    @Test
    fun `pace is unknown without movement`() {
        assertNull(WalkMath.paceSecPerKm(10.0, 60))
    }
}
