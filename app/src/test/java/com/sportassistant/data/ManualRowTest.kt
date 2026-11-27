package com.sportassistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualRowTest {
    private val buckwheat = Per100(92.0, 3.4, 0.6, 20.0)

    private fun row(id: Long = 1, name: String = "", grams: String = "", override: String? = null, per: Per100? = null) =
        ManualRow(id = id, name = name, grams = grams, kcalOverride = override).withPer100(per)

    @Test
    fun `calories are calculated from the weight`() {
        val r = row(name = "Гречка", grams = "200", per = buckwheat)
        assertEquals(184, r.autoKcal)
        assertEquals("184", r.kcalText)
        assertEquals(184, r.kcal)
    }

    @Test
    fun `changing the weight recalculates`() {
        val r = row(name = "Гречка", grams = "100", per = buckwheat)
        assertEquals(92, r.kcal)
        assertEquals(138, r.copy(grams = "150").kcal)
    }

    @Test
    fun `a typed number overrides the calculation`() {
        val r = row(name = "Гречка", grams = "200", override = "250", per = buckwheat)
        assertEquals(184, r.autoKcal) // still known, so it can be offered back
        assertEquals(250, r.kcal)
    }

    @Test
    fun `no weight and no per 100 g data means no calories`() {
        assertEquals(0, row(name = "X").kcal)
        assertNull(row(name = "X", grams = "100").autoKcal)
        assertNull(row(name = "Гречка", per = buckwheat).autoKcal) // weight still empty
    }

    @Test
    fun `calories alone are enough when typed by hand`() {
        val r = row(name = "Торт", override = "350")
        assertTrue(r.isValid)
        assertEquals(350, r.kcal)
    }

    @Test
    fun `a section needs a name and calories to be saved`() {
        assertFalse(row().isValid)
        assertFalse(row(name = "Гречка").isValid)
        assertFalse(row(name = "   ", override = "100").isValid)
        assertFalse(row(name = "Гречка", override = "0").isValid)
        assertTrue(row(name = "Гречка", grams = "100", per = buckwheat).isValid)
    }

    @Test
    fun `macros come from the table only when the calories were not overridden`() {
        val auto = row(name = "Гречка", grams = "200", per = buckwheat).toItem()!!
        assertEquals(7, auto.proteinG) // 3.4 * 2 = 6.8
        assertEquals(40, auto.carbsG)

        val manual = row(name = "Гречка", grams = "200", override = "250", per = buckwheat).toItem()!!
        assertEquals(250, manual.kcal)
        assertEquals(0, manual.proteinG)
        assertEquals(0, manual.carbsG)
    }

    @Test
    fun `an invalid section produces no item`() {
        assertNull(row(name = "Гречка").toItem())
    }

    @Test
    fun `the item keeps a trimmed name and the weight`() {
        val item = row(name = "  Гречка ", grams = "150", per = buckwheat).toItem()!!
        assertEquals("Гречка", item.name)
        assertEquals(150, item.grams)
    }

    @Test
    fun `several sections produce several items in order`() {
        val rows = listOf(
            row(1, "Гречка", "200", per = buckwheat),
            row(2, "Чай", "250", override = "5"),
            row(3, "Яблуко", "180", per = Per100(52.0, 0.3, 0.2, 14.0)),
        )
        val items = ManualRows.items(rows)
        assertEquals(listOf("Гречка", "Чай", "Яблуко"), items.map { it.name })
        assertEquals(184 + 5 + 94, items.sumOf { it.kcal })
    }

    @Test
    fun `empty and half-filled sections are skipped when saving`() {
        val rows = listOf(
            row(1, "Гречка", "200", per = buckwheat),
            row(2), // untouched
            row(3, "Назва без калорій"),
            row(4, "Чай", override = "5"),
        )
        assertEquals(2, ManualRows.validCount(rows))
        assertEquals(listOf("Гречка", "Чай"), ManualRows.items(rows).map { it.name })
    }

    @Test
    fun `a form with nothing valid saves nothing`() {
        assertEquals(0, ManualRows.validCount(listOf(row(1), row(2, "x"))))
        assertTrue(ManualRows.items(emptyList()).isEmpty())
    }

    @Test
    fun `per 100 g round trips through the row`() {
        val r = row(per = Per100(10.5, 1.0, 2.0, 3.0))
        val p = r.per100!!
        assertEquals(10.5, p.kcal, 1e-9)
        assertEquals(3.0, p.carbsG, 1e-9)
        assertNull(r.withPer100(null).per100)
    }

    @Test
    fun `rows keep their own identity`() {
        assertEquals(setOf(1L, 2L, 3L), listOf(row(1), row(2), row(3)).map { it.id }.toSet())
    }

    @Test
    fun `rows are serializable so the form survives rotation`() {
        val r = row(7, "Гречка", "200", per = buckwheat)
        val bytes = java.io.ByteArrayOutputStream().also { java.io.ObjectOutputStream(it).use { o -> o.writeObject(r) } }.toByteArray()
        val back = java.io.ObjectInputStream(bytes.inputStream()).use { it.readObject() } as ManualRow
        assertEquals(r, back)
    }
}
