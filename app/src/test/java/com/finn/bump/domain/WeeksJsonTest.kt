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
    fun `the table loads into a WeekTable and clamps past term`() {
        val table = WeekTable(weeks)
        assertEquals(40, table.lastWeek)
        assertEquals(table[40].size, table[43].size)
    }
}
