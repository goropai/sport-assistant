package com.sportassistant.backup

import com.sportassistant.data.Complaint
import com.sportassistant.data.FoodEntry
import com.sportassistant.data.Observation
import com.sportassistant.data.Profile
import com.sportassistant.data.Sex
import com.sportassistant.data.Walk
import com.sportassistant.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val profile = Profile(
        onboarded = true, age = 43, heightCm = 181, weightKg = 84, sitHours = 9, activity = 1,
        complaints = setOf(Complaint.NECK, Complaint.BACK), remMove = true, remWater = false, remTeeth = true,
        sex = Sex.MALE, repSensitivity = 2, geminiKey = "SECRET-KEY-123",
    )

    private val data = BackupData(
        profile = profile,
        logs = listOf(WorkoutLog(epochDay = 20_000, exercises = 6, seconds = 540, points = 80)),
        walks = listOf(Walk(epochDay = 20_001, durationSec = 1_800, distanceM = 2_345.678, kcal = 140, path = "50.45,30.52;50.46,30.53")),
        food = listOf(
            FoodEntry(epochDay = 20_001, loggedAt = 1_764_000_000_000, name = "Борщ \"домашній\"\nз м'ясом", grams = 300, kcal = 150, proteinG = 6, fatG = 5, carbsG = 20),
        ),
        observations = listOf(
            Observation(
                epochDay = 20_002, type = "POSTURE", summary = "Голова трохи вперед.", details = "a\nb", suggestions = "c",
                seeDoctor = true, doctorReason = "Асиметрія", photoPath = "/data/user/0/app/files/observations/x.jpg",
            ),
        ),
    )

    private fun roundTrip(d: BackupData = data) = BackupCodec.decode(BackupCodec.encode(d, "2026-11-27T10:00:00"))

    @Test
    fun `every record survives a round trip`() {
        val back = roundTrip()
        assertEquals(data.logs.map { it.copy(id = 0) }, back.logs)
        assertEquals(data.walks, back.walks)
        assertEquals(data.food, back.food)
    }

    @Test
    fun `text with quotes, newlines and unicode is preserved`() {
        assertEquals("Борщ \"домашній\"\nз м'ясом", roundTrip().food.single().name)
    }

    @Test
    fun `distance keeps its fractional part`() {
        assertEquals(2_345.678, roundTrip().walks.single().distanceM, 1e-9)
    }

    @Test
    fun `profile round trips but the api key is never written`() {
        val text = BackupCodec.encode(data, "now")
        assertFalse("the key must not leak into the file", text.contains("SECRET-KEY-123"))
        assertEquals(profile.copy(geminiKey = ""), BackupCodec.decode(text).profile)
    }

    @Test
    fun `observation photos are not part of a backup`() {
        val text = BackupCodec.encode(data, "now")
        assertFalse(text.contains("/data/user/0"))
        val o = BackupCodec.decode(text).observations.single()
        assertEquals(null, o.photoPath)
        assertEquals("Асиметрія", o.doctorReason)
        assertTrue(o.seeDoctor)
    }

    @Test
    fun `an empty backup round trips`() {
        val empty = BackupData(Profile(), emptyList(), emptyList(), emptyList(), emptyList())
        val back = roundTrip(empty)
        assertTrue(back.logs.isEmpty() && back.walks.isEmpty() && back.food.isEmpty() && back.observations.isEmpty())
    }

    @Test
    fun `missing sections are treated as empty`() {
        val minimal = """{"app":"sport-assistant","version":1,"profile":{"age":50}}"""
        val back = BackupCodec.decode(minimal)
        assertEquals(50, back.profile.age)
        assertTrue(back.logs.isEmpty())
    }

    @Test
    fun `unknown extra fields are ignored`() {
        val text = """{"app":"sport-assistant","version":1,"future":"x","profile":{"age":41,"newThing":1}}"""
        assertEquals(41, BackupCodec.decode(text).profile.age)
    }

    private fun errorOf(text: String): BackupError = try {
        BackupCodec.decode(text)
        fail("expected a BackupException")
        error("unreachable")
    } catch (e: BackupException) {
        e.error
    }

    @Test
    fun `random text is not a backup`() {
        assertEquals(BackupError.NOT_A_BACKUP, errorOf("hello"))
    }

    @Test
    fun `json from another app is not a backup`() {
        assertEquals(BackupError.NOT_A_BACKUP, errorOf("""{"app":"other","version":1}"""))
        assertEquals(BackupError.NOT_A_BACKUP, errorOf("""{"profile":{}}"""))
    }

    @Test
    fun `a newer file version is refused rather than half-read`() {
        assertEquals(BackupError.NEWER_VERSION, errorOf("""{"app":"sport-assistant","version":99,"profile":{}}"""))
    }

    @Test
    fun `a damaged record is reported as corrupt`() {
        val text = """{"app":"sport-assistant","version":1,"profile":{},"workoutLogs":[{"epochDay":1}]}"""
        assertEquals(BackupError.CORRUPT, errorOf(text))
    }

    @Test
    fun `a backup without a profile is corrupt`() {
        assertEquals(BackupError.CORRUPT, errorOf("""{"app":"sport-assistant","version":1}"""))
    }

    @Test
    fun `unknown enum names fall back to defaults`() {
        val text = """{"app":"sport-assistant","version":1,"profile":{"sex":"ALIEN","complaints":["NECK","TAIL"],"repSensitivity":9}}"""
        val p = BackupCodec.decode(text).profile
        assertEquals(Sex.UNSPECIFIED, p.sex)
        assertEquals(setOf(Complaint.NECK), p.complaints)
        assertEquals(2, p.repSensitivity)
    }

    @Test
    fun `a long walk path round trips`() {
        val path = (1..5_000).joinToString(";") { "50.${1000 + it},30.${2000 + it}" }
        val long = data.copy(walks = listOf(data.walks.single().copy(path = path)))
        assertEquals(path, roundTrip(long).walks.single().path)
    }
}
