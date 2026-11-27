package com.sportassistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodHistoryTest {
    private fun entry(name: String, at: Long, grams: Int = 100, kcal: Int = 100, p: Int = 10, f: Int = 5, c: Int = 20, id: Long = at) =
        FoodEntry(id = id, epochDay = 20_000, loggedAt = at, name = name, grams = grams, kcal = kcal, proteinG = p, fatG = f, carbsG = c)

    @Test
    fun `the most frequent food comes first`() {
        val list = listOf(
            entry("Яблуко", 1), entry("Борщ", 2), entry("Яблуко", 3), entry("Яблуко", 4), entry("Борщ", 5), entry("Чай", 6),
        )
        assertEquals(listOf("Яблуко", "Борщ", "Чай"), FoodHistory.frequent(list).map { it.name })
    }

    @Test
    fun `ties go to the most recently eaten`() {
        val list = listOf(entry("A", 1), entry("B", 5), entry("C", 3))
        assertEquals(listOf("B", "C", "A"), FoodHistory.frequent(list).map { it.name })
    }

    @Test
    fun `names are grouped ignoring case and surrounding spaces`() {
        val list = listOf(entry("Гречка", 1), entry("гречка ", 2), entry(" ГРЕЧКА", 3), entry("Рис", 4))
        val result = FoodHistory.frequent(list)
        assertEquals(2, result.size)
        assertEquals(3L, result.first().loggedAt) // represented by its latest entry
    }

    @Test
    fun `each food is offered with its latest weight and calories`() {
        val list = listOf(entry("Хліб", 1, grams = 30, kcal = 80), entry("Хліб", 9, grams = 60, kcal = 160))
        val bread = FoodHistory.frequent(list).single()
        assertEquals(60, bread.grams)
        assertEquals(160, bread.kcal)
    }

    @Test
    fun `the list is limited`() {
        val list = (1..30).map { entry("Food $it", it.toLong()) }
        assertEquals(8, FoodHistory.frequent(list).size)
        assertEquals(3, FoodHistory.frequent(list, limit = 3).size)
    }

    @Test
    fun `blank names and an empty diary are handled`() {
        assertTrue(FoodHistory.frequent(emptyList()).isEmpty())
        assertTrue(FoodHistory.frequent(listOf(entry("  ", 1), entry("", 2))).isEmpty())
    }

    @Test
    fun `changing the weight scales calories and macros in proportion`() {
        val e = entry("Рис", 1, grams = 100, kcal = 130, p = 3, f = 0, c = 28)
        val doubled = FoodHistory.withGrams(e, 200)
        assertEquals(200, doubled.grams)
        assertEquals(260, doubled.kcal)
        assertEquals(6, doubled.proteinG)
        assertEquals(56, doubled.carbsG)
    }

    @Test
    fun `scaling rounds to whole numbers`() {
        val e = entry("X", 1, grams = 300, kcal = 150, p = 7, f = 5, c = 20)
        val half = FoodHistory.withGrams(e, 150)
        assertEquals(75, half.kcal)
        assertEquals(4, half.proteinG) // 3.5 rounds up
    }

    @Test
    fun `an entry logged by calories alone only changes its weight`() {
        val e = entry("Торт", 1, grams = 0, kcal = 350, p = 0, f = 0, c = 0)
        val r = FoodHistory.withGrams(e, 120)
        assertEquals(120, r.grams)
        assertEquals(350, r.kcal)
    }

    @Test
    fun `a zero or negative weight never rescales to zero calories`() {
        val e = entry("Y", 1, grams = 100, kcal = 200)
        assertEquals(200, FoodHistory.withGrams(e, 0).kcal)
        assertEquals(0, FoodHistory.withGrams(e, -5).grams)
        assertEquals(200, FoodHistory.withGrams(e, -5).kcal)
    }

    @Test
    fun `rescaling keeps identity fields`() {
        val e = entry("Z", 42, grams = 100, kcal = 100, id = 7)
        val r = FoodHistory.withGrams(e, 50)
        assertEquals(7L, r.id)
        assertEquals("Z", r.name)
        assertEquals(42L, r.loggedAt)
        assertEquals(20_000L, r.epochDay)
    }

    @Test
    fun `per 100 g is derived from the entry`() {
        val per = FoodHistory.per100Of(entry("Суп", 1, grams = 300, kcal = 150, p = 6, f = 9, c = 15))!!
        assertEquals(50.0, per.kcal, 1e-9)
        assertEquals(2.0, per.proteinG, 1e-9)
        assertEquals(3.0, per.fatG, 1e-9)
        assertEquals(5.0, per.carbsG, 1e-9)
    }

    @Test
    fun `no per 100 g without a weight`() {
        assertNull(FoodHistory.per100Of(entry("Торт", 1, grams = 0, kcal = 350)))
    }

    @Test
    fun `quick-add round trip reproduces the entry's calories`() {
        val e = entry("Вареники", 1, grams = 200, kcal = 350, p = 9, f = 7, c = 62)
        val per = FoodHistory.per100Of(e)!!
        assertEquals(e.kcal, per.kcalFor(e.grams))
        assertEquals(e.proteinG, per.proteinFor(e.grams))
    }
}
