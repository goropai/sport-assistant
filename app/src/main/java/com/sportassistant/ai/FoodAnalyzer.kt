package com.sportassistant.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

data class FoodItem(
    val name: String,
    val grams: Int,
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
)

enum class AnalysisError { UNAUTHORIZED, RATE_LIMITED, UNAVAILABLE, NETWORK, BAD_RESPONSE }

sealed interface AnalysisResult {
    data class Success(val items: List<FoodItem>) : AnalysisResult
    data object NoFood : AnalysisResult
    data class Failure(val error: AnalysisError) : AnalysisResult
}

/** Pulls the outermost JSON object out of a model answer (models add code fences or prose). */
internal fun extractJsonObject(text: String): JSONObject {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    require(start in 0 until end) { "no json object" }
    return try {
        JSONObject(text.substring(start, end + 1))
    } catch (e: JSONException) {
        throw IllegalArgumentException("malformed json", e)
    }
}

object FoodParser {
    private const val MAX_KCAL = 5000
    private const val MAX_GRAMS = 5000
    private const val MAX_MACRO = 1000

    /**
     * Parses the model's text answer. Values are clamped: the model's numbers are untrusted.
     * @throws IllegalArgumentException if no usable JSON object is present
     */
    fun parse(text: String): List<FoodItem> {
        val items = extractJsonObject(text).optJSONArray("items") ?: throw IllegalArgumentException("no items array")
        return (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            FoodItem(
                name = name.take(80),
                grams = o.optDouble("grams", 0.0).roundToInt().coerceIn(0, MAX_GRAMS),
                kcal = o.optDouble("kcal", 0.0).roundToInt().coerceIn(0, MAX_KCAL),
                proteinG = o.optDouble("protein_g", 0.0).roundToInt().coerceIn(0, MAX_MACRO),
                fatG = o.optDouble("fat_g", 0.0).roundToInt().coerceIn(0, MAX_MACRO),
                carbsG = o.optDouble("carbs_g", 0.0).roundToInt().coerceIn(0, MAX_MACRO),
            )
        }
    }
}

/** Request/response shapes of the Gemini generateContent API, kept pure so they can be unit-tested. */
object GeminiApi {
    const val MODEL = "gemini-3.8-flash"
    const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    fun foodPrompt(languageName: String) =
        "Identify each distinct food or drink in the photo. Respond ONLY with JSON: " +
            "{\"items\":[{\"name\":string,\"grams\":number,\"kcal\":number,\"protein_g\":number,\"fat_g\":number,\"carbs_g\":number}]," +
            "\"confidence\":\"low\"|\"medium\"|\"high\"}. " +
            "grams is the estimated portion weight shown, kcal and macros are for that whole portion. " +
            "If the photo has no food, return {\"items\":[],\"confidence\":\"low\"}. Names in $languageName."

    fun buildRequest(systemPrompt: String, userText: String, imageBase64: String): String = JSONObject()
        .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
        .put(
            "contents",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", userText))
                            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64))),
                    ),
            ),
        )
        .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        .toString()

    fun buildFoodRequest(imageBase64: String, languageName: String): String =
        buildRequest(foodPrompt(languageName), "Analyze the photo.", imageBase64)

    /** Concatenated text parts of the first candidate. @throws IllegalArgumentException if the answer has none. */
    fun extractText(response: String): String {
        val parts = try {
            JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        } catch (e: JSONException) {
            throw IllegalArgumentException("no candidates (blocked or empty answer)", e)
        }
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
            .also { require(it.isNotBlank()) { "empty text" } }
    }
}

object FoodAnalyzer {
    suspend fun analyze(apiKey: String, jpeg: ByteArray, lang: String): AnalysisResult =
        when (val r = GeminiClient.generate(apiKey, GeminiApi.foodPrompt(languageName(lang)), jpeg)) {
            is GenResult.Failure -> AnalysisResult.Failure(r.error)
            is GenResult.Text -> try {
                val items = FoodParser.parse(r.text)
                if (items.isEmpty()) AnalysisResult.NoFood else AnalysisResult.Success(items)
            } catch (e: IllegalArgumentException) {
                AnalysisResult.Failure(AnalysisError.BAD_RESPONSE)
            }
        }
}

internal fun languageName(lang: String) = if (lang == "uk") "Ukrainian" else "English"
