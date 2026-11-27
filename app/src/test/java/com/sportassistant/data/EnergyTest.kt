package com.sportassistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyTest {
    @Test
    fun `male bmr uses mifflin st jeor`() {
        // 10*80 + 6.25*175 - 5*42 + 5 = 1688.75
        assertEquals(1688, Energy.bmr(Sex.MALE, 80, 175, 42))
    }

    @Test
    fun `female bmr is lower by 166`() {
        assertEquals(1522, Energy.bmr(Sex.FEMALE, 80, 175, 42))
    }

    @Test
    fun `unspecified sex sits between male and female`() {
        val v = Energy.bmr(Sex.UNSPECIFIED, 80, 175, 42)
        assertEquals(true, v < Energy.bmr(Sex.MALE, 80, 175, 42) && v > Energy.bmr(Sex.FEMALE, 80, 175, 42))
    }

    @Test
    fun `resting energy accrues through the day`() {
        assertEquals(0, Energy.restingSoFar(1800, 0))
        assertEquals(900, Energy.restingSoFar(1800, 43_200))
        assertEquals(1800, Energy.restingSoFar(1800, 86_400))
    }

    @Test
    fun `out of range seconds are clamped`() {
        assertEquals(1800, Energy.restingSoFar(1800, 999_999))
    }
}
