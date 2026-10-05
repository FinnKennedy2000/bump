package com.finn.bump.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.zip.ZipInputStream

class ExportTest {

    @Test
    fun `the zip holds the live record and every photo this phone has`() {
        val dir = Files.createTempDirectory("photos").toFile()
        File(dir, "here.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val state = AppState(
            babyName = "Bean",
            photos = listOf(
                Photo(id = "1", date = LocalDate.of(2026, 9, 1), blob = "here"),
                Photo(id = "2", date = LocalDate.of(2026, 9, 2), blob = "not-synced-yet"),
                Photo(id = "3", date = LocalDate.of(2026, 9, 3), blob = "gone", deleted = true),
            ),
        )

        val out = ByteArrayOutputStream()
        val missing = writeExport(state, { File(dir, "$it.jpg") }, out)

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                entries[e.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("record.json", "photos/here.jpg"), entries.keys)
        assertEquals(listOf<Byte>(1, 2, 3), entries.getValue("photos/here.jpg").toList())
        assertEquals(listOf("not-synced-yet"), missing)

        val json = entries.getValue("record.json").decodeToString()
        assertFalse("deleted photos stay out", json.contains("\"gone\""))
        assertEquals("Bean", kotlinx.serialization.json.Json.decodeFromString(AppState.serializer(), json).babyName)
    }
}
