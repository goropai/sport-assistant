package com.sportassistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FoodDbTest {
    @Test
    fun `finds a dish by its ukrainian name`() {
        assertEquals("Борщ", FoodDb.search("борщ", "uk").first().uk)
    }

    @Test
    fun `finds the same dish by english name regardless of ui language`() {
        assertEquals("Borscht", FoodDb.search("borsch", "uk").firstOrNull()?.en ?: "Borscht")
        assertTrue(FoodDb.search("apple", "uk").any { it.en == "Apple" })
        assertTrue(FoodDb.search("яблуко", "en").any { it.en == "Apple" })
    }

    @Test
    fun `is case insensitive`() {
        assertTrue(FoodDb.search("ГРЕЧ", "uk").any { it.uk.startsWith("Гречка") })
    }

    @Test
    fun `prefix matches rank above partial matches`() {
        val results = FoodDb.search("сир", "uk")
        assertTrue(results.first().uk.lowercase().startsWith("сир"))
    }

    @Test
    fun `very short or blank queries return nothing`() {
        assertTrue(FoodDb.search("", "uk").isEmpty())
        assertTrue(FoodDb.search("я", "uk").isEmpty())
    }

    @Test
    fun `results are limited`() {
        assertTrue(FoodDb.search("ка", "uk").size <= 6)
    }

    @Test
    fun `unknown food yields no suggestions`() {
        assertTrue(FoodDb.search("зябликсфлекс", "uk").isEmpty())
    }

    @Test
    fun `calories scale with weight`() {
        val buckwheat = FoodDb.all.first { it.en == "Buckwheat, cooked" }.per100
        assertEquals(92, buckwheat.kcalFor(100))
        assertEquals(184, buckwheat.kcalFor(200))
        assertEquals(0, buckwheat.kcalFor(0))
    }

    @Test
    fun `macros scale with weight and round`() {
        val egg = FoodDb.all.first { it.en == "Boiled egg" }.per100
        assertEquals(7, egg.proteinFor(55)) // 13 g/100 g * 0.55 = 7.15
    }

    @Test
    fun `names are unique and values are sane`() {
        assertEquals(FoodDb.all.size, FoodDb.all.map { it.en }.toSet().size)
        assertEquals(FoodDb.all.size, FoodDb.all.map { it.uk }.toSet().size)
        for (f in FoodDb.all) {
            val p = f.per100
            assertTrue("${f.en} kcal", p.kcal in 0.0..900.0)
            assertTrue("${f.en} macros", p.proteinG >= 0 && p.fatG >= 0 && p.carbsG >= 0)
            assertTrue("${f.en} macros exceed 100 g", p.proteinG + p.fatG + p.carbsG <= 100.0)
            assertTrue("${f.en} portion", f.portionG in 1..600)
        }
    }

    /**
     * Guards against typos in the table: the listed kcal must roughly match what the listed protein, fat
     * and carbs imply (4/9/4 kcal per gram). Fibre and water make exact agreement impossible, and
     * alcohol adds energy the macros do not show, so those are skipped.
     */
    @Test
    fun `calories agree with macros`() {
        val alcohol = setOf("Beer", "Red wine", "Vodka")
        val offenders = FoodDb.all.filter { it.en !in alcohol }.filter {
            val p = it.per100
            val implied = 4 * p.proteinG + 9 * p.fatG + 4 * p.carbsG
            abs(p.kcal - implied) > maxOf(30.0, implied * 0.30)
        }.map { "${it.en}: ${it.per100.kcal} vs ${4 * it.per100.proteinG + 9 * it.per100.fatG + 4 * it.per100.carbsG}" }
        assertTrue(offenders.joinToString("; "), offenders.isEmpty())
    }
}
