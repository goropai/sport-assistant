package com.sportassistant.backup

import com.sportassistant.data.Complaint
import com.sportassistant.data.MIN_REMINDER_INTERVAL
import com.sportassistant.data.FoodEntry
import com.sportassistant.data.Observation
import com.sportassistant.data.Profile
import com.sportassistant.data.Sex
import com.sportassistant.data.Walk
import com.sportassistant.data.WorkoutLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything the user has recorded. Photos and the Gemini API key are deliberately not part of it. */
data class BackupData(
    val profile: Profile,
    val logs: List<WorkoutLog>,
    val walks: List<Walk>,
    val food: List<FoodEntry>,
    val observations: List<Observation>,
)

enum class BackupError { NOT_A_BACKUP, NEWER_VERSION, CORRUPT }

class BackupException(val error: BackupError, cause: Throwable? = null) : Exception(error.name, cause)

object BackupCodec {
    const val VERSION = 1
    private const val APP = "sport-assistant"

    fun encode(data: BackupData, exportedAt: String): String = JSONObject()
        .put("app", APP)
        .put("version", VERSION)
        .put("exportedAt", exportedAt)
        .put("profile", profileJson(data.profile))
        .put("workoutLogs", array(data.logs) {
            JSONObject().put("epochDay", it.epochDay).put("exercises", it.exercises).put("seconds", it.seconds).put("points", it.points)
        })
        .put("walks", array(data.walks) {
            JSONObject().put("epochDay", it.epochDay).put("durationSec", it.durationSec).put("distanceM", it.distanceM)
                .put("kcal", it.kcal).put("path", it.path)
        })
        .put("food", array(data.food) {
            JSONObject().put("epochDay", it.epochDay).put("loggedAt", it.loggedAt).put("name", it.name).put("grams", it.grams)
                .put("kcal", it.kcal).put("proteinG", it.proteinG).put("fatG", it.fatG).put("carbsG", it.carbsG)
        })
        .put("observations", array(data.observations) {
            JSONObject().put("epochDay", it.epochDay).put("type", it.type).put("summary", it.summary).put("details", it.details)
                .put("suggestions", it.suggestions).put("seeDoctor", it.seeDoctor).put("doctorReason", it.doctorReason)
        })
        .toString(2)

    /**
     * @throws BackupException NOT_A_BACKUP for anything that is not one of our files, NEWER_VERSION when it
     * was written by a newer app, CORRUPT when it is ours but a record is damaged.
     */
    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException(BackupError.NOT_A_BACKUP, e)
        }
        if (root.optString("app") != APP) throw BackupException(BackupError.NOT_A_BACKUP)
        if (root.optInt("version", 0) > VERSION) throw BackupException(BackupError.NEWER_VERSION)

        return try {
            BackupData(
                profile = profileOf(root.getJSONObject("profile")),
                logs = records(root, "workoutLogs") {
                    WorkoutLog(epochDay = it.getLong("epochDay"), exercises = it.getInt("exercises"), seconds = it.getInt("seconds"), points = it.getInt("points"))
                },
                walks = records(root, "walks") {
                    Walk(
                        epochDay = it.getLong("epochDay"), durationSec = it.getLong("durationSec"), distanceM = it.getDouble("distanceM"),
                        kcal = it.getInt("kcal"), path = it.getString("path"),
                    )
                },
                food = records(root, "food") {
                    FoodEntry(
                        epochDay = it.getLong("epochDay"), loggedAt = it.getLong("loggedAt"), name = it.getString("name"), grams = it.getInt("grams"),
                        kcal = it.getInt("kcal"), proteinG = it.getInt("proteinG"), fatG = it.getInt("fatG"), carbsG = it.getInt("carbsG"),
                    )
                },
                observations = records(root, "observations") {
                    Observation(
                        epochDay = it.getLong("epochDay"), type = it.getString("type"), summary = it.getString("summary"),
                        details = it.getString("details"), suggestions = it.getString("suggestions"), seeDoctor = it.getBoolean("seeDoctor"),
                        doctorReason = it.getString("doctorReason"), photoPath = null,
                    )
                },
            )
        } catch (e: JSONException) {
            throw BackupException(BackupError.CORRUPT, e)
        }
    }

    private fun profileJson(p: Profile) = JSONObject()
        .put("onboarded", p.onboarded).put("age", p.age).put("heightCm", p.heightCm).put("weightKg", p.weightKg)
        .put("sitHours", p.sitHours).put("activity", p.activity)
        .put("complaints", JSONArray(p.complaints.map { it.name }))
        .put("remMove", p.remMove).put("remWater", p.remWater).put("remTeeth", p.remTeeth)
        .put("moveEveryMin", p.moveEveryMin).put("waterEveryMin", p.waterEveryMin)
        .put("dayStartMin", p.dayStartMin).put("dayEndMin", p.dayEndMin).put("weekdaysOnly", p.weekdaysOnly)
        .put("teethMorningMin", p.teethMorningMin).put("teethEveningMin", p.teethEveningMin)
        .put("sex", p.sex.name).put("repSensitivity", p.repSensitivity)

    private fun profileOf(o: JSONObject): Profile {
        val d = Profile()
        val complaints = o.optJSONArray("complaints")?.let { a ->
            (0 until a.length()).mapNotNull { i -> Complaint.entries.firstOrNull { it.name == a.optString(i) } }.toSet()
        } ?: emptySet()
        return Profile(
            onboarded = o.optBoolean("onboarded", d.onboarded),
            age = o.optInt("age", d.age),
            heightCm = o.optInt("heightCm", d.heightCm),
            weightKg = o.optInt("weightKg", d.weightKg),
            sitHours = o.optInt("sitHours", d.sitHours),
            activity = o.optInt("activity", d.activity),
            complaints = complaints,
            remMove = o.optBoolean("remMove", d.remMove),
            remWater = o.optBoolean("remWater", d.remWater),
            remTeeth = o.optBoolean("remTeeth", d.remTeeth),
            // Reminder timing arrived after the first backups: older files simply keep the defaults.
            moveEveryMin = o.optInt("moveEveryMin", d.moveEveryMin).coerceAtLeast(MIN_REMINDER_INTERVAL),
            waterEveryMin = o.optInt("waterEveryMin", d.waterEveryMin).coerceAtLeast(MIN_REMINDER_INTERVAL),
            dayStartMin = o.optInt("dayStartMin", d.dayStartMin).coerceIn(0, 1439),
            dayEndMin = o.optInt("dayEndMin", d.dayEndMin).coerceIn(0, 1439),
            weekdaysOnly = o.optBoolean("weekdaysOnly", d.weekdaysOnly),
            teethMorningMin = o.optInt("teethMorningMin", d.teethMorningMin).coerceIn(0, 1439),
            teethEveningMin = o.optInt("teethEveningMin", d.teethEveningMin).coerceIn(0, 1439),
            sex = Sex.entries.firstOrNull { it.name == o.optString("sex") } ?: d.sex,
            repSensitivity = o.optInt("repSensitivity", d.repSensitivity).coerceIn(0, 2),
            geminiKey = "",
        )
    }

    private fun <T> array(items: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> items.forEach { a.put(f(it)) } }

    /** A missing section is an empty list (older/leaner files); a damaged record throws. */
    private fun <T> records(root: JSONObject, key: String, f: (JSONObject) -> T): List<T> {
        val a = root.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }
}
