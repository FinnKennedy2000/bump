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
    fun `every week says what baby looks like, and hand and foot sizes start at week 8`() {
        weeks.forEach { w ->
            assertTrue("week ${w.week} has no looks", w.looks.isNotBlank())
            assertEquals("week ${w.week} hand", w.week < 8, w.handMm == null)
            assertEquals("week ${w.week} foot", w.week < 8, w.footMm == null)
        }
        val hands = weeks.mapNotNull { it.handMm }
        val feet = weeks.mapNotNull { it.footMm }
        assertEquals(hands.sorted(), hands)
        assertEquals(feet.sorted(), feet)
        assertTrue("foot at 40 is ${feet.last()}", feet.last() in 60..85)
    }

    @Test
    fun `length and weight only ever increase`() {
        val lengths = weeks.mapNotNull { it.lengthCm }
        assertEquals(lengths.sorted(), lengths)
        val weights = weeks.mapNotNull { it.weightG }
        assertEquals(weights.sorted(), weights)
    }

    @Test
    fun `every measured week has a drawn mark, and no mark is orphaned`() {
        // The marks are looked up by week number in WeekMark.kt, so a missing file is a
        // crash on the size chart and a stray one is a mark nothing can ever show.
        val marks = File("src/main/res/drawable")
            .listFiles { f -> f.name.startsWith("week_") }
            .orEmpty()
            .map { it.name.removePrefix("week_").removeSuffix(".xml").toInt() }
            .sorted()

        assertEquals((3..40).toList(), marks)
        assertTrue(
            "some measured weeks have no mark",
            weeks.filter { it.lengthCm != null }.all { it.week in marks },
        )
    }

    @Test
    fun `every arc in every mark spells out its flags`() {
        // Compose's parser accepts SVG's packed arc flags ("0 0111.5" for "0 0 1 11.5");
        // the framework's VectorDrawable parser, used by the widget and the keepsake PDF,
        // throws on them. So every arc must have whole multiples of seven numbers.
        val number = Regex("""-?(\d+\.?\d*|\.\d+)""")
        val pathData = Regex("""android:pathData="([^"]*)"""")
        val arc = Regex("""[Aa]([^A-Za-z]*)""")
        val bad = listOf("drawable", "drawable-night").flatMap { dir ->
            File("src/main/res/$dir").listFiles { f -> f.name.startsWith("week_") }.orEmpty().flatMap { f ->
                pathData.findAll(f.readText())
                    .flatMap { arc.findAll(it.groupValues[1]) }
                    .filter { number.findAll(it.groupValues[1]).count() % 7 != 0 }
                    .map { "$dir/${f.name}: ${it.value.trim()}" }
                    .toList()
            }
        }
        assertTrue("packed arc flags in: $bad", bad.isEmpty())
    }

    @Test
    fun `measured weeks and max length back the size chart`() {
        val table = WeekTable(weeks)
        // Weeks 0-2 have no measurement and must not reach the size chart.
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
