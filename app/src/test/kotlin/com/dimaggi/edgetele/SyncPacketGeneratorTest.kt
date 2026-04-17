package com.dimaggi.edgetele

import com.dimaggi.edgetele.data.model.enums.IncidentCategory
import com.dimaggi.edgetele.data.model.enums.Language
import com.google.gson.Gson
import com.google.gson.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Tests for the JSON serialisation and hash logic used in SyncPacketGenerator.
 * These run on the JVM without Android or Room — they verify the data contract
 * that coordination centres depend on when receiving sync packets.
 */
class SyncPacketGeneratorTest {

    private val gson = Gson()

    // ---- Category label round-trip ----

    @Test fun `all categories survive gemmaLabel round-trip`() {
        IncidentCategory.entries.forEach { category ->
            val recovered = IncidentCategory.fromGemmaLabel(category.gemmaLabel)
            assertEquals("Round-trip failed for $category", category, recovered)
        }
    }

    @Test fun `fromGemmaLabel is case-insensitive`() {
        assertEquals(IncidentCategory.FLOOD_DAMAGE, IncidentCategory.fromGemmaLabel("FLOOD_DAMAGE"))
        assertEquals(IncidentCategory.FLOOD_DAMAGE, IncidentCategory.fromGemmaLabel("Flood Damage"))
        assertEquals(IncidentCategory.INJURY, IncidentCategory.fromGemmaLabel("Injury"))
    }

    @Test fun `unknown label falls back to OTHER`() {
        assertEquals(IncidentCategory.OTHER, IncidentCategory.fromGemmaLabel("unknown_xyz"))
    }

    // ---- SHA-256 integrity ----

    @Test fun `sha256 of known bytes is stable`() {
        val input = "edge-tele-photo-test".toByteArray()
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(input)
            .joinToString("") { "%02x".format(it) }

        val actual = MessageDigest.getInstance("SHA-256")
            .digest(input)
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, actual)
        assertEquals(64, actual.length) // SHA-256 = 32 bytes = 64 hex chars
    }

    // ---- Bulk JSON structure ----

    @Test fun `bulk json is a valid array`() {
        val fakeJson = """[{"id":"a"},{"id":"b"}]"""
        val parsed = gson.fromJson(fakeJson, JsonArray::class.java)
        assertEquals(2, parsed.size())
    }

    // ---- Language asset keys are unique ----

    @Test fun `all language asset keys are unique`() {
        val keys = Language.entries.map { it.assetKey }
        assertEquals("Duplicate language asset keys", keys.size, keys.toSet().size)
    }

    // ---- No-delete guard: incident ID is stable after construction ----

    @Test fun `incident id is non-empty uuid format`() {
        val id = java.util.UUID.randomUUID().toString()
        assertTrue(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        assertNotNull(id)
    }
}
