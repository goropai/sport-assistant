package com.sportassistant.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiApiTest {
    @Test
    fun `request carries the prompt, image and json mode`() {
        val req = JSONObject(GeminiApi.buildFoodRequest("QUJD", "Ukrainian"))

        val system = req.getJSONObject("system_instruction").getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(system.contains("Ukrainian"))

        val parts = req.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        val inline = parts.getJSONObject(1).getJSONObject("inline_data")
        assertEquals("image/jpeg", inline.getString("mime_type"))
        assertEquals("QUJD", inline.getString("data"))

        assertEquals("application/json", req.getJSONObject("generationConfig").getString("responseMimeType"))
    }

    @Test
    fun `text-only request has no image part`() {
        val req = JSONObject(GeminiApi.buildRequest(GeminiApi.nutritionPrompt("English"), "borscht", null))
        val parts = req.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(1, parts.length())
        assertEquals("borscht", parts.getJSONObject(0).getString("text"))
    }

    @Test
    fun `nutrition prompt asks for per 100 g in the requested language`() {
        val p = GeminiApi.nutritionPrompt("Ukrainian")
        assertTrue(p.contains("per 100 g"))
        assertTrue(p.contains("Ukrainian"))
    }

    @Test
    fun `extracts and joins text parts of the first candidate`() {
        val response = """{"candidates":[{"content":{"parts":[{"text":"{\"items\":"},{"text":"[]}"}]}}]}"""
        assertEquals("""{"items":[]}""", GeminiApi.extractText(response))
    }

    @Test
    fun `extracted answer feeds the food parser`() {
        val inner = """{"items":[{"name":"Soup","grams":250,"kcal":120}]}"""
        val response = JSONObject().put(
            "candidates",
            org.json.JSONArray().put(
                JSONObject().put("content", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", inner)))),
            ),
        ).toString()
        assertEquals(120, FoodParser.parse(GeminiApi.extractText(response)).single().kcal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blocked answer without candidates is rejected`() {
        GeminiApi.extractText("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `candidate with empty text is rejected`() {
        GeminiApi.extractText("""{"candidates":[{"content":{"parts":[{"text":"  "}]}}]}""")
    }
}
