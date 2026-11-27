package com.sportassistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodParserTest {
    @Test
    fun `parses a plain json answer`() {
        val items = FoodParser.parse(
            """{"items":[{"name":"Borscht","grams":300,"kcal":150,"protein_g":6,"fat_g":5,"carbs_g":20}],"confidence":"high"}""",
        )
        assertEquals(listOf(FoodItem("Borscht", 300, 150, 6, 5, 20)), items)
    }

    @Test
    fun `tolerates code fences and surrounding prose`() {
        val text = "Here you go:\n```json\n{\"items\":[{\"name\":\"Apple\",\"grams\":150,\"kcal\":78}]}\n```\nEnjoy!"
        val items = FoodParser.parse(text)
        assertEquals(1, items.size)
        assertEquals(78, items[0].kcal)
    }

    @Test
    fun `rounds fractional numbers`() {
        val item = FoodParser.parse("""{"items":[{"name":"Rice","grams":180.6,"kcal":233.4,"protein_g":4.5}]}""").single()
        assertEquals(181, item.grams)
        assertEquals(233, item.kcal)
        assertEquals(5, item.proteinG)
    }

    @Test
    fun `clamps absurd values from the model`() {
        val item = FoodParser.parse("""{"items":[{"name":"Plate","grams":-50,"kcal":900000}]}""").single()
        assertEquals(0, item.grams)
        assertEquals(5000, item.kcal)
    }

    @Test
    fun `skips items without a name`() {
        val items = FoodParser.parse("""{"items":[{"name":"  ","kcal":100},{"kcal":50},{"name":"Tea","kcal":2}]}""")
        assertEquals(listOf("Tea"), items.map { it.name })
    }

    @Test
    fun `empty items means no food`() {
        assertTrue(FoodParser.parse("""{"items":[],"confidence":"low"}""").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `text without json is rejected`() {
        FoodParser.parse("Sorry, I cannot help with that.")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `json without items array is rejected`() {
        FoodParser.parse("""{"result":"ok"}""")
    }
}
