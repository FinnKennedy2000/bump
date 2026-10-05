package com.finn.bump.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContractionsTest {

    private val min = 60_000L

    @Test
    fun `gaps run start to start, oldest first, whatever order they arrive in`() {
        val timed = Contractions.timed(listOf(10 * min to 50_000L, 0L to 40_000L, 4 * min to 45_000L))
        assertEquals(listOf(0L, 4 * min, 10 * min), timed.map { it.startedAtMillis })
        assertEquals(listOf(null, 4 * min, 6 * min), timed.map { it.gapMillis })
    }

    @Test
    fun `the summary averages only the last hour`() {
        val now = 120 * min
        val list = listOf(
            0L to 90_000L,                 // outside the hour, ignored
            70 * min to 40_000L,
            75 * min to 50_000L,
            81 * min to 60_000L,
        )
        val s = Contractions.summary(list, now)!!
        assertEquals(3, s.count)
        assertEquals(50_000L, s.averageDurationMillis)
        assertEquals((5 * min + 6 * min) / 2, s.averageGapMillis)
    }

    @Test
    fun `one contraction has a length but no gap, and none has no summary`() {
        val s = Contractions.summary(listOf(10 * min to 30_000L), 20 * min)!!
        assertEquals(1, s.count)
        assertNull(s.averageGapMillis)
        assertNull(Contractions.summary(emptyList(), 20 * min))
    }
}
