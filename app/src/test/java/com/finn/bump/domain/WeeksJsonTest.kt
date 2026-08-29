package com.finn.bump.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parses the real bundled content rather than a fixture.
 *
 * `weeks.json` is loaded at runtime from `res/raw`, so a malformed or incomplete file
 * would only show up as a crash on launch. This is the cheapest thing that fails first
 * instead. The unit test runs with the module directory as its working directory.
 */
class WeeksJsonTest {

    private val weeks: List<WeekInfo> = Json { ignoreUnknownKeys = true }.decodeFromString(
        ListSerializer(WeekInfo.serializer()),
        File("src/main/res/raw/weeks.json").readText(),
    )

    @Test
    fun `covers every week from 0 to 40 with no gaps or duplicates`() {
        assertEquals((0..40).toList(), weeks.map { it.week })
    }

    @Test
    fun `every week has a size label and a blurb`() {
        weeks.forEach { w ->
            assertTrue("week ${w.week} has no size", w.size.isNotBlank())
            assertTrue("week ${w.week} has no blurb", w.blurb.isNotBlank())
        }
    }

    @Test
    fun `length and weight only ever increase`() {
        val lengths = weeks.mapNotNull { it.lengthCm }
        assertEquals(lengths.sorted(), lengths)
        val weights = weeks.mapNotNull { it.weightG }
        assertEquals(weights.sorted(), weights)
    }

    @Test
    fun `emoji are single graphemes where present`() {
        weeks.mapNotNull { w -> w.emoji?.let { w.week to it } }.forEach { (week, e) ->
            assertTrue("week $week emoji is blank", e.isNotBlank())
            // A stray label instead of a picture would blow the row layout apart.
            assertTrue("week $week emoji '$e' looks like text", e.length <= 8)
        }
    }

    @Test
    fun `measured weeks and max length back the sizes screen`() {
        val table = WeekTable(weeks)
        // Weeks 0-2 have no measurement and must not reach the Sizes list.
        assertTrue(table.measured.none { it.lengthCm == null })
        assertEquals(3, table.measured.first().week)
        assertEquals(40, table.measured.last().week)
        assertEquals(weeks.mapNotNull { it.lengthCm }.max(), table.maxLengthCm, 0.0)
    }

    @Test
    fun `sub-centimetre lengths are shown in millimetres`() {
        // "%.1f cm" renders every early week as "0.0 cm", which reads as no baby at all.
        assertEquals("0.1 mm", formatLength(0.01))
        assertEquals("2.0 mm", formatLength(0.2))
        assertEquals("1.0 cm", formatLength(1.0))
        assertEquals("51.2 cm", formatLength(51.2))
        weeks.mapNotNull { it.lengthCm }.forEach {
            assertTrue("$it formats to zero", !formatLength(it).startsWith("0.0 "))
        }
    }

    @Test
    fun `weights switch to kilograms at a kilo`() {
        assertEquals("999 g", formatWeight(999))
        assertEquals("1.00 kg", formatWeight(1000))
        assertEquals("3.46 kg", formatWeight(3462))
    }

    @Test
    fun `the table loads into a WeekTable and clamps past term`() {
        val table = WeekTable(weeks)
        assertEquals(40, table.lastWeek)
        assertEquals(table[40].size, table[43].size)
    }
}
