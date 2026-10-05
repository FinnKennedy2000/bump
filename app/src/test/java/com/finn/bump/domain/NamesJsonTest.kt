package com.finn.bump.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** `names.json` is read at runtime from `res/raw`; a typo there is a crash on the names screen. */
class NamesJsonTest {

    private val names = Json.decodeFromString(
        ListSerializer(NameIdea.serializer()),
        File("src/main/res/raw/names.json").readText(),
    )

    @Test
    fun `every entry has a name, an origin and a meaning`() {
        val blank = names.filter { it.name.isBlank() || it.origin.isBlank() || it.meaning.isBlank() }
        assertEquals(emptyList<NameIdea>(), blank)
    }

    @Test
    fun `no name appears twice in one group`() {
        val dupes = names.groupBy { it.group to it.name.lowercase() }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<Any>(), dupes)
    }

    @Test
    fun `every group has enough to swipe through`() {
        NameGroup.entries.forEach { g ->
            assertTrue("$g has too few names", names.count { it.group == g } >= 50)
        }
    }
}
