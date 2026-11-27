package com.sportassistant.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class MealPickerTest {
    private fun pick(h: Int, m: Int = 0, logged: Set<Meal> = emptySet()) = MealPicker.defaultFor(LocalTime.of(h, m), logged)

    @Test
    fun `morning without breakfast suggests breakfast`() {
        assertEquals(Meal.BREAKFAST, pick(8))
    }

    @Test
    fun `morning with breakfast already logged suggests a snack`() {
        assertEquals(Meal.SNACK, pick(8, logged = setOf(Meal.BREAKFAST)))
    }

    @Test
    fun `lunch time without lunch suggests lunch`() {
        assertEquals(Meal.LUNCH, pick(13))
    }

    @Test
    fun `lunch time with lunch already logged suggests a snack`() {
        assertEquals(Meal.SNACK, pick(13, logged = setOf(Meal.LUNCH)))
    }

    @Test
    fun `evening without dinner suggests dinner`() {
        assertEquals(Meal.DINNER, pick(19))
    }

    @Test
    fun `evening with dinner already logged suggests a snack`() {
        assertEquals(Meal.SNACK, pick(19, logged = setOf(Meal.DINNER)))
    }

    @Test
    fun `only the meal expected at this hour matters`() {
        // Breakfast was skipped, but it is lunch time: lunch is suggested, not breakfast.
        assertEquals(Meal.LUNCH, pick(12, logged = emptySet()))
        // Lunch is logged, breakfast is not, still lunch time: a snack, not breakfast.
        assertEquals(Meal.SNACK, pick(12, logged = setOf(Meal.LUNCH)))
        // A snack on record does not use up a main meal.
        assertEquals(Meal.BREAKFAST, pick(8, logged = setOf(Meal.SNACK)))
    }

    @Test
    fun `window boundaries`() {
        assertEquals(Meal.SNACK, pick(4, 59))
        assertEquals(Meal.BREAKFAST, pick(5, 0))
        assertEquals(Meal.BREAKFAST, pick(10, 59))
        assertEquals(Meal.LUNCH, pick(11, 0))
        assertEquals(Meal.LUNCH, pick(15, 59))
        assertEquals(Meal.SNACK, pick(16, 0))
        assertEquals(Meal.SNACK, pick(16, 59))
        assertEquals(Meal.DINNER, pick(17, 0))
        assertEquals(Meal.DINNER, pick(22, 59))
        assertEquals(Meal.SNACK, pick(23, 0))
    }

    @Test
    fun `night is always a snack`() {
        for (h in listOf(0, 1, 2, 3, 4, 23)) assertEquals("hour $h", Meal.SNACK, pick(h))
    }

    @Test
    fun `every hour of the day gives some meal`() {
        for (h in 0..23) for (m in listOf(0, 30, 59)) pick(h, m)
    }

    @Test
    fun `unknown stored names become snacks`() {
        assertEquals(Meal.SNACK, Meal.of("BRUNCH"))
        assertEquals(Meal.SNACK, Meal.of(""))
        assertEquals(Meal.DINNER, Meal.of("DINNER"))
    }

    @Test
    fun `every meal survives a name round trip`() {
        for (meal in Meal.entries) assertEquals(meal, Meal.of(meal.name))
    }
}
