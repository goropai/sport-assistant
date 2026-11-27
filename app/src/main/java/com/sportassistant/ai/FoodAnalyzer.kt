package com.sportassistant.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
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

object FoodParser {
    private const val MAX_KCAL = 5000
    private const val MAX_GRAMS = 5000
    private const val MAX_MACRO = 1000

    /**
     * Parses the model's text answer. Models sometimes wrap JSON in code fences or add prose, so the
     * outermost {...} is extracted first. Values are clamped: the model's numbers are untrusted.
     * @throws IllegalArgumentException if no usable JSON object is present
     */
    fun parse(text: String): List<FoodItem> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start in 0 until end) { "no json object" }
        val items = try {
            JSONObject(text.substring(start, end + 1)).optJSONArray("items")
        } catch (e: JSONException) {
            throw IllegalArgumentException("malformed json", e)
        } ?: throw IllegalArgumentException("no items array")

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

    fun buildFoodRequest(imageBase64: String, languageName: String): String = JSONObject()
        .put("system_instruction", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", foodPrompt(languageName)))))
        .put(
            "contents",
            org.json.JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "parts",
                        org.json.JSONArray()
                            .put(JSONObject().put("text", "Analyze the photo."))
                            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64))),
                    ),
            ),
        )
        .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        .toString()

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

/** Sends a downscaled photo straight to Gemini using the user's own API key (entered in Settings). */
object FoodAnalyzer {
    private const val MAX_SIDE_PX = 1280
    private const val JPEG_QUALITY = 80
    private const val TIMEOUT_MS = 45_000

    suspend fun prepareJpeg(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE_PX * 2) sample *= 2

        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("cannot decode image")

        val scale = MAX_SIDE_PX.toFloat() / max(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
        } else bitmap
        ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
    }

    suspend fun analyze(apiKey: String, jpeg: ByteArray, lang: String): AnalysisResult = withContext(Dispatchers.IO) {
        val image = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val request = Request(
            GeminiApi.ENDPOINT,
            mapOf("x-goog-api-key" to apiKey.trim()),
            GeminiApi.buildFoodRequest(image, if (lang == "uk") "Ukrainian" else "English"),
        )

        try {
            val (code, text) = post(request)
            when {
                code == 200 -> {
                    val items = FoodParser.parse(GeminiApi.extractText(text))
                    if (items.isEmpty()) AnalysisResult.NoFood else AnalysisResult.Success(items)
                }
                // Gemini reports a bad API key as 400 (API_KEY_INVALID) or 403.
                code == 400 || code == 401 || code == 403 -> AnalysisResult.Failure(AnalysisError.UNAUTHORIZED)
                code == 429 -> AnalysisResult.Failure(AnalysisError.RATE_LIMITED)
                code in 500..599 -> AnalysisResult.Failure(AnalysisError.UNAVAILABLE)
                else -> AnalysisResult.Failure(AnalysisError.BAD_RESPONSE)
            }
        } catch (e: IOException) {
            AnalysisResult.Failure(AnalysisError.NETWORK)
        } catch (e: IllegalArgumentException) {
            AnalysisResult.Failure(AnalysisError.BAD_RESPONSE)
        } catch (e: JSONException) {
            AnalysisResult.Failure(AnalysisError.BAD_RESPONSE)
        }
    }

    private class Request(val url: String, val headers: Map<String, String>, val body: String)

    private fun post(r: Request): Pair<Int, String> {
        val conn = URL(r.url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            r.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(r.body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }
}
