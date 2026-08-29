package com.finn.bump

import com.finn.bump.data.AppState
import com.finn.bump.data.BabySex
import com.finn.bump.data.displayName
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AppStateTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `state saved before baby details existed still loads`() {
        // Exactly what an installed copy of the app has on disk today. If this breaks,
        // everyone's saved pregnancy is replaced by the corruption handler's blank state.
        val old = """
            {"dueDate":"2026-12-25","babyName":"Pip","appointments":[],
             "weights":[],"notes":[],"kickSessions":[]}
        """.trimIndent()

        val state = json.decodeFromString(AppState.serializer(), old)

        assertEquals(LocalDate.of(2026, 12, 25), state.dueDate)
        assertEquals("Pip", state.babyName)
        assertEquals("", state.babyNickname)
        assertEquals(BabySex.UNKNOWN, state.babySex)
    }

    @Test
    fun `round trips through json`() {
        val state = AppState(
            dueDate = LocalDate.of(2026, 12, 25),
            babyName = "Rowan",
            babyNickname = "Peanut",
            babySex = BabySex.SURPRISE,
        )
        assertEquals(
            state,
            json.decodeFromString(AppState.serializer(), json.encodeToString(AppState.serializer(), state)),
        )
    }

    @Test
    fun `display name prefers the real name, then the bump name`() {
        assertEquals("Your baby", AppState().displayName)
        assertEquals("Peanut", AppState(babyNickname = "Peanut").displayName)
        assertEquals("Rowan", AppState(babyName = "Rowan", babyNickname = "Peanut").displayName)
        // Whitespace is not a name.
        assertEquals("Peanut", AppState(babyName = "   ", babyNickname = "Peanut").displayName)
    }
}
