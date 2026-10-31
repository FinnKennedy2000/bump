package com.finn.bump.data

import com.finn.bump.domain.NameGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FetchedNamesTest {
    private fun user(first: String, gender: String) =
        """{"gender":"$gender","name":{"title":"Ms","first":"$first","last":"X"},"nat":"GB"}"""

    @Test
    fun `new plain names are kept with gender, the rest dropped`() {
        val body = """{"results":[
            ${user("laura", "female")}, ${user("LAURA", "female")}, ${user("Sam", "male")},
            ${user("Sam", "female")}, ${user("Emma", "female")}, ${user("Jean-Pierre", "male")},
            ${user("R2D2", "male")}, ${user("Zoe", "robot")}, ${user("Al", "male")}, ${user("X", "male")}
        ]}"""
        val got = parseFetchedNames(body, known = setOf("emma"))
        assertEquals(listOf("Laura", "Sam", "Al"), got.map { it.name })
        assertEquals(listOf(NameGroup.GIRL, NameGroup.NEUTRAL, NameGroup.BOY), got.map { it.group })
        assertEquals(setOf(FETCHED_NAME), got.map { it.origin }.toSet())
        assertEquals("", got.first().meaning)
    }

    @Test
    fun `a response of the wrong shape throws rather than reading as empty`() {
        assertThrows(Exception::class.java) { parseFetchedNames("""{"error":"rate limited"}""", emptySet()) }
    }
}
