package com.sportassistant.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface GenResult {
    data class Text(val text: String) : GenResult
    data class Failure(val error: AnalysisError) : GenResult
}

/** Photo in, model text out. Talks to Gemini directly with the user's own API key. */
object GeminiClient {
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

    suspend fun generate(
        apiKey: String,
        systemPrompt: String,
        jpeg: ByteArray?,
        userText: String = "Analyze the photo.",
    ): GenResult = withContext(Dispatchers.IO) {
        val body = GeminiApi.buildRequest(systemPrompt, userText, jpeg?.let { Base64.encodeToString(it, Base64.NO_WRAP) })
        try {
            val (code, text) = post(apiKey.trim(), body)
            when {
                code == 200 -> GenResult.Text(GeminiApi.extractText(text))
                // Gemini reports a bad API key as 400 (API_KEY_INVALID) or 403.
                code == 400 || code == 401 || code == 403 -> GenResult.Failure(AnalysisError.UNAUTHORIZED)
                code == 429 -> GenResult.Failure(AnalysisError.RATE_LIMITED)
                code in 500..599 -> GenResult.Failure(AnalysisError.UNAVAILABLE)
                else -> GenResult.Failure(AnalysisError.BAD_RESPONSE)
            }
        } catch (e: IOException) {
            GenResult.Failure(AnalysisError.NETWORK)
        } catch (e: IllegalArgumentException) {
            GenResult.Failure(AnalysisError.BAD_RESPONSE)
        } catch (e: JSONException) {
            GenResult.Failure(AnalysisError.BAD_RESPONSE)
        }
    }

    private fun post(apiKey: String, body: String): Pair<Int, String> {
        val conn = URL(GeminiApi.ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }
}
