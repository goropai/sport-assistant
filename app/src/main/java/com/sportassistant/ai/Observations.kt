package com.sportassistant.ai

import androidx.annotation.StringRes
import com.sportassistant.R
import org.json.JSONArray

enum class ObservationType(@StringRes val label: Int) {
    POSTURE(R.string.obs_type_posture),
    SKIN(R.string.obs_type_skin),
}

data class ObservationResult(
    val summary: String,
    val observations: List<String>,
    val suggestions: List<String>,
    val seeDoctor: Boolean,
    val doctorReason: String,
)

sealed interface ObservationOutcome {
    data class Success(val result: ObservationResult) : ObservationOutcome
    data class Failure(val error: AnalysisError) : ObservationOutcome
}

/**
 * The prompts are deliberately conservative: describe what is visible, never name conditions, and
 * escalate to a doctor instead of reassuring. A phone photo cannot replace a medical examination.
 */
object ObservationPrompts {
    private const val FORMAT = "Respond ONLY with JSON: " +
        "{\"summary\":string (1-2 sentences),\"observations\":[string] (up to 5 short items of what is visible)," +
        "\"suggestions\":[string] (2-3 gentle general tips),\"see_doctor\":boolean,\"see_doctor_reason\":string (empty if false)}. "

    fun forType(type: ObservationType, languageName: String): String = when (type) {
        ObservationType.POSTURE ->
            "You are a careful wellness assistant for an adult who works sitting at a desk. From the photo, describe only " +
                "what is visibly apparent about posture: head position relative to the shoulders, shoulder height symmetry, " +
                "rounded shoulders, visible pelvic tilt or spine alignment. Mention if the angle, clothing or framing limits " +
                "what can be judged. Never diagnose or name medical conditions. Suggest gentle stretches, strengthening or " +
                "workstation habits. Set see_doctor to true only if something looks markedly asymmetric or the person would " +
                "reasonably benefit from a professional assessment. $FORMAT Write in $languageName."
        ObservationType.SKIN ->
            "You are a careful wellness assistant. From the photo, describe only visible skin features: colour evenness, " +
                "dryness, redness, texture, visible spots. Never diagnose or name skin diseases, and never say a mark is " +
                "harmless. Give general care tips (sun protection, moisturising, hydration, sleep). Set see_doctor to true for " +
                "any mole or spot that is asymmetric, has irregular borders or several colours, is changing, bleeding or " +
                "ulcerated, for marked redness, swelling or anything else concerning, and explain why in see_doctor_reason. " +
                "State that a photo cannot replace an examination by a dermatologist. $FORMAT Write in $languageName."
    }
}

object ObservationParser {
    private const val MAX_TEXT = 400

    /** @throws IllegalArgumentException if the answer has no usable content */
    fun parse(text: String): ObservationResult {
        val o = extractJsonObject(text)
        val result = ObservationResult(
            summary = o.optString("summary").trim().take(MAX_TEXT),
            observations = strings(o.optJSONArray("observations"), 5),
            suggestions = strings(o.optJSONArray("suggestions"), 4),
            seeDoctor = o.optBoolean("see_doctor", false),
            doctorReason = o.optString("see_doctor_reason").trim().take(MAX_TEXT),
        )
        require(result.summary.isNotEmpty() || result.observations.isNotEmpty()) { "empty answer" }
        return result
    }

    private fun strings(array: JSONArray?, max: Int): List<String> =
        if (array == null) emptyList()
        else (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotEmpty() }.take(max).map { it.take(MAX_TEXT) }
}

object ObservationAnalyzer {
    suspend fun analyze(apiKey: String, type: ObservationType, jpeg: ByteArray, lang: String): ObservationOutcome =
        when (val r = GeminiClient.generate(apiKey, ObservationPrompts.forType(type, languageName(lang)), jpeg)) {
            is GenResult.Failure -> ObservationOutcome.Failure(r.error)
            is GenResult.Text -> try {
                ObservationOutcome.Success(ObservationParser.parse(r.text))
            } catch (e: IllegalArgumentException) {
                ObservationOutcome.Failure(AnalysisError.BAD_RESPONSE)
            }
        }
}
