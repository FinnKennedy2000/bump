package com.finn.bump.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CultureTest {
    private val names = Json.decodeFromString(
        ListSerializer(NameIdea.serializer()),
        File("src/main/res/raw/names.json").readText(),
    )

    // Origins that deliberately have no culture of their own.
    private val deliberatelyOther = setOf("Hawaiian", "Igbo", "Sioux")

    @Test
    fun `every bundled origin lands in a culture except the few left as Other`() {
        val other = names.map { it.origin }.filter { cultureOf(it) == Culture.OTHER }.toSet()
        val counts = names.groupingBy { cultureOf(it.origin) }.eachCount()
        assertEquals("unmapped origins; counts: $counts", deliberatelyOther, other)
    }

    @Test
    fun `fetched and compound labels map`() {
        assertEquals(Culture.IRISH, cultureOf("Irish"))
        assertEquals(Culture.SLAVIC, cultureOf("Ukrainian"))
        assertEquals(Culture.SLAVIC, cultureOf("Serbian"))
        assertEquals(Culture.NORDIC, cultureOf("Finnish"))
        assertEquals(Culture.SPANISH, cultureOf("Mexican"))
        assertEquals(Culture.PORTUGUESE, cultureOf("Brazilian"))
        assertEquals(Culture.GERMAN, cultureOf("Swiss"))
        assertEquals(Culture.OTHER, cultureOf(FETCHED_LEGACY))
        assertTrue(Culture.entries.all { it.label.isNotBlank() })
    }

    private val FETCHED_LEGACY = "Fetched name"
}
