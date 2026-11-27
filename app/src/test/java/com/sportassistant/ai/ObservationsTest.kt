package com.sportassistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationsTest {
    @Test
    fun `parses a full answer`() {
        val r = ObservationParser.parse(
            """{"summary":"Head slightly forward.","observations":["Left shoulder a bit higher"],
            "suggestions":["Chin tucks","Doorway stretch"],"see_doctor":false,"see_doctor_reason":""}""",
        )
        assertEquals("Head slightly forward.", r.summary)
        assertEquals(listOf("Left shoulder a bit higher"), r.observations)
        assertEquals(2, r.suggestions.size)
        assertFalse(r.seeDoctor)
    }

    @Test
    fun `keeps the doctor flag and reason`() {
        val r = ObservationParser.parse(
            """{"summary":"A dark spot with irregular edges.","see_doctor":true,"see_doctor_reason":"Irregular border"}""",
        )
        assertTrue(r.seeDoctor)
        assertEquals("Irregular border", r.doctorReason)
    }

    @Test
    fun `tolerates fences and drops blanks`() {
        val r = ObservationParser.parse("```json\n{\"summary\":\"ok\",\"observations\":[\"  \",\"dry\"],\"suggestions\":[]}\n```")
        assertEquals(listOf("dry"), r.observations)
    }

    @Test
    fun `limits list sizes`() {
        val many = (1..20).joinToString(",") { "\"item $it\"" }
        val r = ObservationParser.parse("""{"summary":"s","observations":[$many],"suggestions":[$many]}""")
        assertEquals(5, r.observations.size)
        assertEquals(4, r.suggestions.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty answer is rejected`() {
        ObservationParser.parse("""{"summary":"","observations":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `prose without json is rejected`() {
        ObservationParser.parse("I cannot assess this photo.")
    }

    @Test
    fun `prompts forbid diagnosing and use the requested language`() {
        for (type in ObservationType.entries) {
            val p = ObservationPrompts.forType(type, "Ukrainian")
            assertTrue(p.contains("Never diagnose"))
            assertTrue(p.contains("Ukrainian"))
            assertTrue(p.contains("see_doctor"))
        }
    }

    @Test
    fun `skin prompt escalates concerning spots instead of reassuring`() {
        val p = ObservationPrompts.forType(ObservationType.SKIN, "English")
        assertTrue(p.contains("never say a mark is harmless"))
        assertTrue(p.contains("dermatologist"))
    }
}
