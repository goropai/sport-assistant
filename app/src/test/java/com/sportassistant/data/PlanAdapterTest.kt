package com.sportassistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAdapterTest {
    @Test
    fun `sedentary newcomer starts gently`() {
        assertEquals(PlanLevel.EASIER, PlanAdapter.levelFor(0, hadWorkoutBeforeToday = false, activity = 0))
    }

    @Test
    fun `active newcomer starts at the normal level`() {
        assertEquals(PlanLevel.NORMAL, PlanAdapter.levelFor(0, hadWorkoutBeforeToday = false, activity = 2))
    }

    @Test
    fun `returning after a quiet week gets an easier plan`() {
        assertEquals(PlanLevel.EASIER, PlanAdapter.levelFor(0, hadWorkoutBeforeToday = true, activity = 2))
    }

    @Test
    fun `occasional training stays at the normal level`() {
        for (days in 1..3) assertEquals(PlanLevel.NORMAL, PlanAdapter.levelFor(days, true, 0))
    }

    @Test
    fun `consistent training steps the plan up`() {
        assertEquals(PlanLevel.HARDER, PlanAdapter.levelFor(4, true, 0))
        assertEquals(PlanLevel.HARDER, PlanAdapter.levelFor(6, true, 1))
    }

    @Test
    fun `normal level leaves exercises untouched`() {
        val squats = Exercises.byId("squats")!!
        assertEquals(squats, PlanAdapter.scale(squats, PlanLevel.NORMAL))
    }

    @Test
    fun `easier lowers the dose but never below a safe floor`() {
        val squats = Exercises.byId("squats")!! // 12 reps
        assertEquals(8, PlanAdapter.scale(squats, PlanLevel.EASIER).reps)
        val eyes = Exercises.byId("eye_rest")!! // 20 s
        assertEquals(16, PlanAdapter.scale(eyes, PlanLevel.EASIER).seconds)
        val tiny = squats.copy(reps = 6)
        assertEquals(5, PlanAdapter.scale(tiny, PlanLevel.EASIER).reps)
        val short = eyes.copy(seconds = 15)
        assertEquals(15, PlanAdapter.scale(short, PlanLevel.EASIER).seconds)
    }

    @Test
    fun `harder raises the dose`() {
        val squats = Exercises.byId("squats")!!
        assertEquals(15, PlanAdapter.scale(squats, PlanLevel.HARDER).reps)
        val march = Exercises.byId("march")!! // 180 s
        assertEquals(216, PlanAdapter.scale(march, PlanLevel.HARDER).seconds)
    }

    @Test
    fun `scaling keeps timed exercises timed and counted ones counted`() {
        for (level in PlanLevel.entries) {
            for (ex in Exercises.all) {
                val s = PlanAdapter.scale(ex, level)
                assertEquals("${ex.id} $level", ex.reps > 0, s.reps > 0)
                assertEquals("${ex.id} $level", ex.seconds > 0, s.seconds > 0)
            }
        }
    }

    @Test
    fun `plan size follows the level`() {
        val day = 20_000L // even: includes cardio on the normal and harder plans
        val easier = PlanGenerator.forDay(day, emptySet(), PlanLevel.EASIER)
        val normal = PlanGenerator.forDay(day, emptySet(), PlanLevel.NORMAL)
        val harder = PlanGenerator.forDay(day, emptySet(), PlanLevel.HARDER)
        assertTrue(easier.size < normal.size)
        assertTrue(normal.size < harder.size)
    }

    @Test
    fun `easier plan drops cardio and has fewer strength moves`() {
        val easier = PlanGenerator.forDay(20_000L, emptySet(), PlanLevel.EASIER)
        assertTrue(easier.none { it.category == Category.CARDIO })
        assertEquals(1, easier.count { it.category == Category.STRENGTH })
    }

    @Test
    fun `default level keeps the previous behaviour`() {
        assertEquals(
            PlanGenerator.forDay(20_001L, setOf(Complaint.BACK)),
            PlanGenerator.forDay(20_001L, setOf(Complaint.BACK), PlanLevel.NORMAL),
        )
    }

    @Test
    fun `the plan is stable for a given day and level`() {
        assertEquals(
            PlanGenerator.forDay(20_002L, setOf(Complaint.NECK), PlanLevel.HARDER),
            PlanGenerator.forDay(20_002L, setOf(Complaint.NECK), PlanLevel.HARDER),
        )
    }
}
